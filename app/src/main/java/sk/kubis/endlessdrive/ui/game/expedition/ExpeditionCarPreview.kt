package sk.kubis.endlessdrive.ui.game.expedition

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import sk.kubis.endlessdrive.game.GameEngine
import java.util.concurrent.atomic.AtomicBoolean

/** A composited GL texture respects workshop backgrounds, clipping and dialogs. */
internal class ExpeditionCarPreview(context:Context,engine:GameEngine) : TextureView(context),TextureView.SurfaceTextureListener {
    @Volatile var renderedFrames=0; private set
    @Volatile var presentedFrames=0; private set
    @Volatile var lastGlError=GLES20.GL_NO_ERROR; private set
    @Volatile private var worker:Handler?=null
    private var thread:HandlerThread?=null
    private var closed=false
    private val queued=AtomicBoolean(false)
    private var display=EGL14.EGL_NO_DISPLAY
    private var eglContext=EGL14.EGL_NO_CONTEXT
    private var eglSurface=EGL14.EGL_NO_SURFACE
    private var window:Surface?=null
    private val renderer=ExpeditionRenderer(context,{engine},engine,{false},{error,_,_->
        lastGlError=error; renderedFrames++
    },preview=true)

    init { surfaceTextureListener=this; isOpaque=true }

    override fun onSurfaceTextureAvailable(texture:SurfaceTexture,width:Int,height:Int) {
        if(closed) return
        val renderThread=HandlerThread("CarPreview3D").also { it.start() }
        thread=renderThread
        val handler=Handler(renderThread.looper)
        worker=handler
        handler.post {
            texture.setDefaultBufferSize(width,height)
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version=IntArray(2)
            check(EGL14.eglInitialize(display,version,0,version,1))
            val attributes=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,
                EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE)
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1); val count=IntArray(1)
            check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
            eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(eglContext!=EGL14.EGL_NO_CONTEXT)
            window=Surface(texture)
            eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],window,intArrayOf(EGL14.EGL_NONE),0)
            check(eglSurface!=EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))
            renderer.onSurfaceCreated(null,null)
            renderer.onSurfaceChanged(null,width,height)
            draw()
        }
    }

    private fun draw() {
        if(eglSurface==EGL14.EGL_NO_SURFACE) return
        renderer.onDrawFrame(null)
        check(EGL14.eglSwapBuffers(display,eglSurface))
    }

    fun requestRender() {
        val handler=worker ?: return
        if(closed || !queued.compareAndSet(false,true)) return
        handler.post { queued.set(false); draw() }
    }

    fun onPause() { closed=true }

    override fun onSurfaceTextureSizeChanged(texture:SurfaceTexture,width:Int,height:Int) {
        worker?.post {
            texture.setDefaultBufferSize(width,height)
            renderer.onSurfaceChanged(null,width,height)
            draw()
        }
    }

    override fun onSurfaceTextureUpdated(texture:SurfaceTexture) { presentedFrames++ }

    override fun onSurfaceTextureDestroyed(texture:SurfaceTexture):Boolean {
        val handler=worker
        val renderThread=thread
        worker=null; thread=null
        if(handler==null) { texture.release(); return false }
        handler.post {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if(eglSurface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,eglSurface)
            if(eglContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext)
            EGL14.eglTerminate(display); EGL14.eglReleaseThread()
            eglSurface=EGL14.EGL_NO_SURFACE; eglContext=EGL14.EGL_NO_CONTEXT; display=EGL14.EGL_NO_DISPLAY
            window?.release(); window=null
            texture.release()
            renderThread?.quitSafely()
        }
        return false
    }
}
