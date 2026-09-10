package sk.kubis.endlessdrive.ui.game

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Dedicated surface for the high-frequency game scene.
 *
 * Compose still owns the HUD, menus and touch controls. Only the scene is
 * posted from this thread, so a heavy frame cannot make the whole HUD
 * participate in the same Compose draw pass. The latest requested frame wins;
 * stale frames are intentionally dropped instead of building a queue.
 */
internal class GameSurfaceView(
    context: Context,
    private val viewModel: GameViewModel,
    private val renderer: GameRenderer,
    density: Density,
    layoutDirection: LayoutDirection
) : SurfaceView(context), SurfaceHolder.Callback {

    private val renderSignal = Object()

    @Volatile
    private var renderDensity = density

    @Volatile
    private var renderLayoutDirection = layoutDirection

    @Volatile
    private var surfaceReady = false

    @Volatile
    private var running = false

    @Volatile
    private var framePending = false

    private var renderThread: Thread? = null

    init {
        holder.addCallback(this)
        setBackgroundColor(AndroidColor.TRANSPARENT)
    }

    fun updateViewport(density: Density, layoutDirection: LayoutDirection) {
        renderDensity = density
        renderLayoutDirection = layoutDirection
        requestGameRender()
    }

    fun requestGameRender() {
        synchronized(renderSignal) {
            framePending = true
            renderSignal.notifyAll()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startRenderThread()
        requestGameRender()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        requestGameRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopRenderThread()
    }

    private fun startRenderThread() {
        if (renderThread?.isAlive == true) return
        running = true
        renderThread = Thread(::renderLoop, "EndlessDrive-Render").also { it.start() }
    }

    private fun stopRenderThread() {
        val thread = renderThread ?: return
        running = false
        synchronized(renderSignal) { renderSignal.notifyAll() }
        if (Thread.currentThread() !== thread) {
            runCatching { thread.join(300L) }
        }
        renderThread = null
    }

    private fun renderLoop() {
        val drawScope = CanvasDrawScope()
        while (running) {
            synchronized(renderSignal) {
                while (running && (!surfaceReady || !framePending)) {
                    runCatching { renderSignal.wait(250L) }
                }
                if (!running) break
                framePending = false
            }

            if (!holder.surface.isValid) continue
            // SurfaceHolder.lockCanvas() can return a software canvas. That
            // makes this procedural renderer fall back to CPU rasterisation
            // and drops the game to roughly one frame per second. Use the
            // hardware canvas explicitly on supported Android versions.
            val nativeCanvas = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
            }.getOrNull()
            if (nativeCanvas == null) {
                // Surface creation/resizing can briefly return no canvas. Keep
                // the latest frame requested so static screens do not remain blank.
                synchronized(renderSignal) { framePending = true }
                Thread.yield()
                continue
            }

            try {
                // The simulation and discrete UI actions are serialized with
                // drawing by the ViewModel monitor. This makes moving the scene
                // off Compose safe even when a button is pressed mid-frame.
                synchronized(viewModel) {
                    nativeCanvas.drawColor(AndroidColor.BLACK)
                    drawScope.draw(
                        density = renderDensity,
                        layoutDirection = renderLayoutDirection,
                        canvas = ComposeCanvas(nativeCanvas),
                        size = Size(nativeCanvas.width.toFloat(), nativeCanvas.height.toFloat())
                    ) {
                        with(renderer) { draw(viewModel.game) }
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(nativeCanvas)
            }
        }
    }
}
