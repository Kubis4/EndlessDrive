package sk.kubis.endlessdrive

import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLES20
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.*
import sk.kubis.endlessdrive.domain.repository.PlayerProfile
import sk.kubis.endlessdrive.domain.repository.PlayerRepository
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.world.World3D
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionCarPreview
import sk.kubis.endlessdrive.ui.game.GameScreen
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.ui.game.GameSceneSurface
import sk.kubis.endlessdrive.ui.game.expedition.ExpeditionSurfaceView
import sk.kubis.endlessdrive.ui.theme.EndlessDriveTheme
import sk.kubis.endlessdrive.ui.l10n.Copy
import sk.kubis.endlessdrive.ui.l10n.steerLeft
import sk.kubis.endlessdrive.ui.l10n.steerRight
import sk.kubis.endlessdrive.ui.l10n.throttle
import sk.kubis.endlessdrive.ui.l10n.brakeReverse
import java.io.File

/** Platform accessibility also works on Android previews without Espresso's hidden API hooks. */
class ExpeditionViewTest {
    private class Repository(private val saved:String?=null) : PlayerRepository {
        override val profile = flowOf(PlayerProfile())
        override suspend fun current() = PlayerProfile()
        override suspend fun recordRun(distanceKm: Float) = Unit
        override suspend fun loadRun(): String? = saved
        override suspend fun saveRun(data: String) = Unit
        override suspend fun clearRun() = Unit
        override val debugOptions = flowOf(DebugOptions.OFF)
        override suspend fun setDebugOptions(options: DebugOptions) = Unit
        override val throttleMode = flowOf(ThrottleMode.BINARY)
        override suspend fun setThrottleMode(mode: ThrottleMode) = Unit
        override val gaugeStyle = flowOf(GaugeStyle.CLASSIC)
        override suspend fun setGaugeStyle(style: GaugeStyle) = Unit
        override val uiScale = flowOf(1f)
        override suspend fun setUiScale(scale: Float) = Unit
        override val appLanguage = flowOf(AppLanguage.ENGLISH)
        override suspend fun setAppLanguage(language: AppLanguage) = Unit
    }

    private fun findScene(view: View): ExpeditionSurfaceView? {
        if (view is ExpeditionSurfaceView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findScene(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun hasGameSurface(view: View): Boolean {
        if (view is GameSceneSurface) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (hasGameSurface(view.getChildAt(i))) return true
        return false
    }

    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline=System.nanoTime()+30_000_000_000L
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for 3D scene / UI" }
            Thread.sleep(50)
        }
    }

    private fun clickText(text: String) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (target != null) return target
            }
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        awaitCondition { find(automation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
    }

    private fun controlBounds(description: String): Rect {
        var result: Rect?=null
        fun find(node:AccessibilityNodeInfo?) {
            if(node==null) return
            if(node.contentDescription?.toString()==description) {
                val bounds=Rect(); node.getBoundsInScreen(bounds)
                if(bounds.width()>0 && bounds.height()>0) result=bounds
            }
            for(i in 0 until node.childCount) find(node.getChild(i))
        }
        awaitCondition { find(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow); result!=null }
        return result!!
    }

    private fun touch(downTime:Long,action:Int,points:List<Pair<Float,Float>>) {
        val properties=Array(points.size) { i -> MotionEvent.PointerProperties().also { it.id=i; it.toolType=MotionEvent.TOOL_TYPE_FINGER } }
        val coords=Array(points.size) { i -> MotionEvent.PointerCoords().also {
            it.x=points[i].first; it.y=points[i].second; it.pressure=1f; it.size=1f
        } }
        val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,points.size,properties,coords,
            0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        try { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event,true)) }
        finally { event.recycle() }
    }

    @Test fun default3dSceneSupportsIndependentSteeringThrottleBrakeAndSave() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assertTrue(ExpeditionSurfaceView.supported(context))
        lateinit var vm: GameViewModel
        val showGame=mutableStateOf(true)
        ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
            activity.onActivity { host ->
                vm=GameViewModel(Repository(),0f)
                vm.debugOptions=DebugOptions(fullUpgrades=true,fullBody=true,fullFluids=true)
                vm.retry(); vm.startEngine(); vm.resume(); vm.setPaused(true)
                assertTrue("Prepared engine starts", vm.game.car.engineRunning)
                host.setContent {
                    EndlessDriveTheme {
                        if (showGame.value) GameScreen(vm,onExitToMenu={ showGame.value=false })
                    }
                }
            }
            val before=RunCodec.encode(vm.game.snapshot())
            var surface: ExpeditionSurfaceView?=null
            awaitCondition {
                activity.onActivity { surface=findScene(it.window.decorView) }
                surface?.renderedFrames?.let { it > 0 } == true
            }
            assertEquals(GLES20.GL_NO_ERROR,surface!!.lastGlError)
            awaitCondition { surface!!.frameUploadBytes<65_536 }
            Log.i("ExpeditionTest","3D frame CPU=${surface!!.frameCpuNanos/1_000_000f} ms; upload=${surface!!.frameUploadBytes} bytes")
            Log.i("ExpeditionTest", "3D frame rendered")
            activity.onActivity { assertEquals(before,RunCodec.encode(vm.game.snapshot())) }
            Log.i("ExpeditionTest", "Paused state unchanged")
            val startDistance=synchronized(vm) { vm.game.distanceM }
            val startZ=synchronized(vm) { vm.game.car.planar.z }
            activity.onActivity { vm.setPaused(false) }
            val copy=Copy.of(AppLanguage.ENGLISH)
            val wheel=controlBounds(copy.steerRight)
            val left=controlBounds(copy.steerLeft)
            assertTrue(left.centerX()<wheel.centerX())
            val throttle=controlBounds(copy.throttle)
            val brake=controlBounds(copy.brakeReverse)
            assertTrue(wheel.centerX()<brake.centerX() && brake.centerX()<throttle.centerX())
            val pedalPoint=throttle.exactCenterX() to throttle.exactCenterY()
            val wheelPoint=wheel.exactCenterX() to wheel.exactCenterY()
            val down=SystemClock.uptimeMillis()
            touch(down,MotionEvent.ACTION_DOWN,listOf(pedalPoint))
            touch(down,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(pedalPoint,wheelPoint))
            awaitCondition { synchronized(vm) { vm.game.throttleInput>0.3f && vm.game.steeringInput>0.5f && vm.game.car.planar.z>startZ+0.7f } }
            val leftPoint=left.exactCenterX() to left.exactCenterY()
            touch(down,MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(pedalPoint,wheelPoint,leftPoint))
            awaitCondition { synchronized(vm) { vm.game.steeringInput==0f && vm.game.throttleInput>0.3f } }
            touch(down,MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(pedalPoint,wheelPoint,leftPoint))
            awaitCondition { synchronized(vm) { vm.game.steeringInput>0.5f && vm.game.throttleInput>0.3f } }
            touch(down,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(pedalPoint,wheelPoint))
            awaitCondition { synchronized(vm) { vm.game.steeringInput==0f && vm.game.throttleInput>0.3f } }
            touch(down,MotionEvent.ACTION_UP,listOf(pedalPoint))
            assertTrue(synchronized(vm) { vm.game.distanceM>startDistance })
            val brakeDown=SystemClock.uptimeMillis()
            val brakePoint=brake.exactCenterX() to brake.exactCenterY()
            touch(brakeDown,MotionEvent.ACTION_DOWN,listOf(brakePoint))
            awaitCondition { synchronized(vm) { vm.game.brakeInput>0.4f && vm.game.throttleInput<0.2f } }
            touch(brakeDown,MotionEvent.ACTION_UP,listOf(brakePoint))
            awaitCondition { synchronized(vm) { vm.game.brakeInput<0.05f } }
            assertTrue("Engine remains running during 3D driving", synchronized(vm) { vm.game.car.engineRunning })
            Log.i("ExpeditionTest", "Car moving")
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            try {
                File(context.getExternalFilesDir(null),"expedition-preview.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
            } finally { bitmap.recycle() }
            activity.onActivity { surface!!.setSceneActive(false); surface!!.setSceneActive(true) }
            Log.i("ExpeditionTest", "GL resumed")
            val frames=surface!!.renderedFrames
            awaitCondition { surface!!.renderedFrames > frames }
            assertEquals(GLES20.GL_NO_ERROR,surface!!.lastGlError)
            activity.onActivity { vm.setPaused(true) }
            val after=synchronized(vm) { RunCodec.encode(vm.game.snapshot()) }
            Thread.sleep(250)
            assertEquals(after,synchronized(vm) { RunCodec.encode(vm.game.snapshot()) })
            val restored=sk.kubis.endlessdrive.game.GameEngine.restore(RunCodec.decode(after)!!,0f)
            assertEquals(vm.game.car.planar.z,restored.car.planar.z,0.001f)
            // Exercise the game's drained exit instead of abruptly destroying a live HWUI window.
            clickText("MENU")
            awaitCondition {
                var removed=false
                activity.onActivity { removed=!showGame.value && !hasGameSurface(it.window.decorView) }
                removed
            }
            Log.i("ExpeditionTest", "Exited game")
        }
    }

    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun materialsBiomesForkAndWorkshopUseReal3dMeshes() {
        val scenarios=listOf(BranchStyle.DESERT to RoadPaving.SAND_TRACK,
            BranchStyle.FOREST_ALIVE to RoadPaving.DIRT,BranchStyle.ALPINE to RoadPaving.PACKED_SNOW)
        for((style,paving) in scenarios) {
            val e=GameEngine(811L,0f,DebugOptions(fullUpgrades=true,fullBody=true,fullFluids=true))
            e.car.applyBodyPaint(VehiclePaint.CREAM)
            assertTrue(e.tryStartEngine()); e.resumeDriving()
            val original=e.snapshot()
            val snap=original.copy(segment=original.segment.copy(style=style,paving=paving))
            val ready=GameEngine.restore(snap,0f)
            if(style==BranchStyle.DESERT) {
                ready.car.x=ready.segment.endWorldX-18f
                ready.car.planar.z=World3D.roadCenter(ready.car.x)
                ready.car.y=World3D.height(ready,ready.car.x,ready.car.planar.z)+0.48f
            }
            val showGame=mutableStateOf(true)
            lateinit var vm:GameViewModel
            ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
                activity.onActivity { host ->
                    vm=GameViewModel(Repository(RunCodec.encode(ready.snapshot())),0f)
                    vm.resume()
                    host.setContent { EndlessDriveTheme { if(showGame.value) GameScreen(vm,onExitToMenu={showGame.value=false}) } }
                }
                var surface:ExpeditionSurfaceView?=null
                awaitCondition {
                    activity.onActivity { surface=findScene(it.window.decorView) }
                    surface?.renderedFrames?.let { it>8 }==true
                }
                assertEquals(style,vm.game.segment.style)
                assertEquals(GLES20.GL_NO_ERROR,surface!!.lastGlError)
                screenshot("3d-${style.name.lowercase()}.png")
                if(style==BranchStyle.FOREST_ALIVE) {
                    activity.onActivity { vm.stop() }
                    clickText("CAR")
                    fun preview(view:View):ExpeditionCarPreview? {
                        if(view is ExpeditionCarPreview) return view
                        if(view is ViewGroup) for(i in 0 until view.childCount) preview(view.getChildAt(i))?.let {return it}
                        return null
                    }
                    var model:ExpeditionCarPreview?=null
                    awaitCondition {
                        activity.onActivity { model=preview(it.window.decorView) }
                        model?.presentedFrames?.let {it>0}==true
                    }
                    assertEquals(GLES20.GL_NO_ERROR,model!!.lastGlError)
                    val presented=model!!.presentedFrames
                    activity.onActivity { model!!.requestRender() }
                    awaitCondition { model!!.presentedFrames>presented }
                    var expectedPixel=0
                    val location=IntArray(2)
                    run {
                        var buffer:Bitmap?=null
                        activity.onActivity { buffer=model!!.bitmap }
                        val captured=checkNotNull(buffer)
                        expectedPixel=captured.getPixel(captured.width/2,captured.height/2)
                        assertNotEquals("3D model must occupy the centre of its texture",captured.getPixel(0,0),expectedPixel)
                        activity.onActivity {
                            model!!.getLocationOnScreen(location)
                            location[0]+=model!!.width/2; location[1]+=model!!.height/2
                        }
                        Log.i("ExpeditionTest","Workshop texture=${captured.width}x${captured.height}, center=${captured.getPixel(captured.width/2,captured.height/2)}, corner=${captured.getPixel(0,0)}")
                        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"3d-workshop-buffer.png")
                            .outputStream().use { captured.compress(Bitmap.CompressFormat.PNG,100,it) }
                        captured.recycle()
                    }
                    Thread.sleep(250)
                    val composited=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    val actualPixel=composited.getPixel(location[0],location[1])
                    composited.recycle()
                    for(shift in intArrayOf(0,8,16)) assertTrue("Workshop model must be visible above the panel",
                        kotlin.math.abs(((expectedPixel shr shift) and 255)-((actualPixel shr shift) and 255))<24)
                    screenshot("3d-workshop.png")
                    fun close(node:AccessibilityNodeInfo?):Boolean {
                        if(node==null) return false
                        if(node.actionList.any {it.label?.toString()=="Close"}) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        for(i in 0 until node.childCount) if(close(node.getChild(i))) return true
                        return false
                    }
                    assertTrue(close(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow))
                }
                activity.onActivity { vm.setPaused(true) }
                clickText("MENU")
                awaitCondition { !showGame.value }
            }
        }
    }
}
