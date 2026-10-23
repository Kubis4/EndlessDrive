package sk.kubis.endlessdrive.ui.game.expedition

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.opengl.GLUtils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import sk.kubis.endlessdrive.game.SceneEffects
import sk.kubis.endlessdrive.core.GameConfig
import kotlin.math.*
import sk.kubis.endlessdrive.ui.game.GameSceneSurface
import sk.kubis.endlessdrive.ui.game.GameViewModel
import sk.kubis.endlessdrive.game.GameEngine
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Production 3D scene. Compose owns HUD and independent multi-touch driving controls. */
internal class ExpeditionSurfaceView(context: Context, viewModel: GameViewModel) :
    GLSurfaceView(context), GameSceneSurface {
    private var active = true
    private var closed = false
    @Volatile var lightningFlashesEnabled = true
    @Volatile var renderedFrames = 0
        private set
    @Volatile var lastGlError = GL_NO_ERROR
        private set
    @Volatile var frameCpuNanos = 0L
        private set
    @Volatile var frameUploadBytes = 0
        private set

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,0,16,0)
        preserveEGLContextOnPause = true
        setRenderer(ExpeditionRenderer(context,{viewModel.game},viewModel,{ lightningFlashesEnabled }, onFrame={ error,cpu,bytes ->
            lastGlError = error
            frameCpuNanos=cpu
            frameUploadBytes=bytes
            renderedFrames++
        }))
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun requestGameRender() { if (active && !closed) requestRender() }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0 && h>0) {
            val scale=GameConfig.sceneBufferScale(w.toFloat(),h.toFloat())
            holder.setFixedSize((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1))
        }
    }
    override fun setSceneActive(active: Boolean) {
        if (this.active == active || closed) return
        this.active = active
        if (active) { onResume(); requestRender() } else onPause()
    }
    override fun shutdown() {
        if (closed) return
        if (active) onPause()
        active = false
        closed = true
    }
    override fun shutdownThen(action: () -> Unit) {
        shutdown()
        postOnAnimation { postOnAnimation(action) }
    }

    companion object {
        fun supported(context: Context): Boolean =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.deviceConfigurationInfo?.reqGlEsVersion?.let { it >= 0x20000 } == true
    }
}

/** The workshop uses the very same textured model and installed parts as the road scene. */
internal class ExpeditionRenderer(
    private val context: Context,
    private val game: () -> GameEngine,
    private val monitor: Any,
    private val flashesEnabled: () -> Boolean,
    private val onFrame: (Int,Long,Int) -> Unit,
    private val preview:Boolean=false
) : GLSurfaceView.Renderer {
    private var scene: ExpeditionScene? = null
    private var program=0
    private var position=0; private var normal=0; private var color=0
    private var matrix=0; private var daylight=0
    private var eyeUniform=0; private var carUniform=0; private var lightUniform=0
    private var fogUniform=0; private var originUniform=0; private var brakeUniform=0
    private var particleUniform=0
    private var material=0; private var modelUniform=0; private var modelMode=0; private var paintUniform=0
    private val textures=IntArray(4)
    private val textureUniforms=IntArray(4)
    private var snowUniform=0; private var tireUniform=0
    private var carBuffers=IntArray(0)
    private val identity=FloatArray(16).also { Matrix.setIdentityM(it,0) }
    private val projection=FloatArray(16)
    private val view=FloatArray(16)
    private val vp=FloatArray(16)
    private val buffers=IntArray(3)
    private var worldVertices=0
    private var aspect=1f
    private var worldNeedsUpload=true
    private var cameraX=Float.NaN
    private var cameraY=0f
    private var cameraZ=0f
    private var lastElapsed=0f
    private var uploadedBytes=0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (scene == null) {
            val model=context.assets.open("expedition/car_modular.obj").reader().use(ExpeditionObj::read)
            scene=ExpeditionScene(model)
        }
        program=glCreateProgram()
        val vertexShader=shader(GL_VERTEX_SHADER,VERTEX)
        val fragmentShader=shader(GL_FRAGMENT_SHADER,FRAGMENT)
        glAttachShader(program,vertexShader)
        glAttachShader(program,fragmentShader)
        glLinkProgram(program)
        val status=IntArray(1)
        glGetProgramiv(program,GL_LINK_STATUS,status,0)
        check(status[0] != 0) { glGetProgramInfoLog(program) }
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
        position=glGetAttribLocation(program,"aPosition")
        normal=glGetAttribLocation(program,"aNormal")
        color=glGetAttribLocation(program,"aColor")
        matrix=glGetUniformLocation(program,"uMatrix")
        daylight=glGetUniformLocation(program,"uDaylight")
        eyeUniform=glGetUniformLocation(program,"uEye")
        carUniform=glGetUniformLocation(program,"uCar")
        lightUniform=glGetUniformLocation(program,"uHeadlights")
        fogUniform=glGetUniformLocation(program,"uFog")
        originUniform=glGetUniformLocation(program,"uOrigin")
        brakeUniform=glGetUniformLocation(program,"uBrakes")
        particleUniform=glGetUniformLocation(program,"uParticles")
        material=glGetAttribLocation(program,"aMaterial")
        modelUniform=glGetUniformLocation(program,"uModel")
        modelMode=glGetUniformLocation(program,"uModelMode")
        paintUniform=glGetUniformLocation(program,"uPaint")
        for(i in 0..3) textureUniforms[i]=glGetUniformLocation(program,arrayOf("uSand","uEarth","uAsphalt","uMetal")[i])
        snowUniform=glGetUniformLocation(program,"uSnow")
        tireUniform=glGetUniformLocation(program,"uTireState")
        val source=context.assets.open("expedition/materials-v1.png").use { BitmapFactory.decodeStream(it) }
        glGenTextures(4,textures,0)
        // Separate repeat textures have independent mip chains. Atlas-wide mipmapping
        // bleeds adjacent materials and creates a visible grid at every tile boundary.
        for(i in 0..3) {
            glBindTexture(GL_TEXTURE_2D,textures[i])
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT)
            val tile=Bitmap.createBitmap(source,(i%2)*source.width/2,(i/2)*source.height/2,source.width/2,source.height/2)
            val bitmap=Bitmap.createScaledBitmap(tile,512,512,true)
            GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0); glGenerateMipmap(GL_TEXTURE_2D)
            if(tile!==bitmap) tile.recycle()
            bitmap.recycle()
        }
        source.recycle()
        glGenBuffers(3,buffers,0)
        val parts=scene!!.carGeometry.parts
        carBuffers=IntArray(parts.size)
        glGenBuffers(carBuffers.size,carBuffers,0)
        parts.forEachIndexed { i,part -> upload(part.mesh,carBuffers[i],GL_STATIC_DRAW) }
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        worldNeedsUpload=true // EGL context recreation invalidates all GPU resources.
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0,0,width,height)
        aspect=width.toFloat()/height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val scene=scene ?: return
        val cpuStart=System.nanoTime()
        uploadedBytes=0
        // GL calls remain outside the monitor. Only geometry/state extraction reads the engine.
        val changed=synchronized(monitor) { scene.update(game(),preview) }
        if (changed || worldNeedsUpload) {
            upload(scene.world,buffers[0],GL_STATIC_DRAW)
            worldVertices=scene.world.vertexCount
            worldNeedsUpload=false
        }
        upload(scene.vehicle,buffers[1],GL_DYNAMIC_DRAW)
        upload(scene.particles,buffers[2],GL_DYNAMIC_DRAW)
        val flash=if(flashesEnabled() && scene.raining && SceneEffects.stormFlash(scene.elapsed)>0.5f) 0.35f else 0f
        val day=if(preview) 1f else (scene.daylight+flash).coerceAtMost(1f)
        val fogR=((scene.groundTint shr 16) and 255)/255f
        val fogG=((scene.groundTint shr 8) and 255)/255f
        val fogB=(scene.groundTint and 255)/255f
        val exposure=0.16f+day*0.84f
        glClearColor(fogR*exposure,fogG*exposure,fogB*exposure,1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val wx=scene.carX+scene.originX
        val wz=scene.carZ+scene.originZ
        val wy=scene.carY+scene.originY
        val dt=(scene.elapsed-lastElapsed).coerceIn(1f/120f,0.08f)
        val blend=1f-exp(-7f*dt)
        if(!cameraX.isFinite() || abs(wx-cameraX)>75f || scene.elapsed<lastElapsed) {
            cameraX=wx; cameraY=wy; cameraZ=wz
        } else {
            cameraX+=(wx-cameraX)*blend; cameraY+=(wy-cameraY)*blend; cameraZ+=(wz-cameraZ)*blend
        }
        lastElapsed=scene.elapsed
        val cx=cameraX-scene.originX; val cz=cameraZ-scene.originZ; val cy=cameraY-scene.originY
        val zoom=(abs(scene.speed)*0.11f).coerceAtMost(4f)
        val ahead=5f+(abs(scene.speed)*0.15f).coerceAtMost(5f)
        Matrix.perspectiveM(projection,0,42f,aspect,1f,320f)
        Matrix.setLookAtM(view,0,cx-22f,cy+34f+zoom,cz+34f+zoom,
            cx+cos(scene.heading)*ahead,cy+0.7f,cz+sin(scene.heading)*ahead,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        if(preview) {
            val h=scene.heading
            Matrix.perspectiveM(projection,0,34f,aspect,0.1f,80f)
            Matrix.setLookAtM(view,0,scene.carX-cos(h)*7f-sin(h)*9f,scene.carY+6f,
                scene.carZ-sin(h)*7f+cos(h)*9f,scene.carX,scene.carY+0.65f,scene.carZ,0f,1f,0f)
            Matrix.multiplyMM(vp,0,projection,0,view,0)
        }
        glUseProgram(program)
        glUniformMatrix4fv(matrix,1,false,vp,0)
        glUniform1f(daylight,day)
        glUniform3f(eyeUniform,cx-22f,cy+34f+zoom,cz+34f+zoom)
        glUniform4f(carUniform,scene.carX,scene.carY+0.7f,scene.carZ,scene.heading)
        glUniform1f(lightUniform,if(preview) 0f else scene.headlights)
        glUniform1f(brakeUniform,if(preview) 0f else scene.braking)
        glUniform4f(fogUniform,fogR*exposure,fogG*exposure,fogB*exposure,scene.fog)
        glUniform2f(originUniform,scene.originX%2048f,scene.originZ%2048f)
        glUniform1f(particleUniform,0f)
        for(i in 0..3) {
            glActiveTexture(GL_TEXTURE0+i); glBindTexture(GL_TEXTURE_2D,textures[i]); glUniform1i(textureUniforms[i],i)
        }
        glUniformMatrix4fv(modelUniform,1,false,identity,0)
        glUniform1f(modelMode,0f); glUniform4f(paintUniform,1f,1f,1f,0f)
        glUniform1f(snowUniform,0f)
        glUniform2f(tireUniform,-1000f,0f)
        if(!preview) {
            draw(buffers[0],worldVertices)
            draw(buffers[1],scene.vehicle.vertexCount)
        }
        glUniform1f(modelMode,1f)
        scene.carGeometry.parts.forEachIndexed { i,part ->
            if(part.visible) {
                glUniformMatrix4fv(modelUniform,1,false,part.matrix,0)
                glUniform4f(paintUniform,((part.paint shr 16) and 255)/255f,
                    ((part.paint shr 8) and 255)/255f,(part.paint and 255)/255f,part.wear)
                glUniform1f(snowUniform,part.snow)
                glUniform2f(tireUniform,part.tireFloor,part.tireDamage)
                draw(carBuffers[i],part.mesh.vertexCount)
            }
        }
        glUniformMatrix4fv(modelUniform,1,false,identity,0); glUniform1f(modelMode,0f)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(false)
        glUniform1f(particleUniform,1f)
        if(!preview) draw(buffers[2],scene.particles.vertexCount)
        glDepthMask(true)
        glDisable(GL_BLEND)
        glBindBuffer(GL_ARRAY_BUFFER,0)
        onFrame(glGetError(),System.nanoTime()-cpuStart,uploadedBytes)
    }

    private fun upload(mesh: SceneMesh, buffer: Int, usage: Int) {
        uploadedBytes+=mesh.size*4
        glBindBuffer(GL_ARRAY_BUFFER,buffer)
        glBufferData(GL_ARRAY_BUFFER,mesh.size*4,mesh.buffer(),usage)
    }
    private fun draw(buffer:Int,count:Int) {
        glBindBuffer(GL_ARRAY_BUFFER,buffer)
        val stride=SceneMesh.STRIDE*4
        glEnableVertexAttribArray(position); glVertexAttribPointer(position,3,GL_FLOAT,false,stride,0)
        glEnableVertexAttribArray(normal); glVertexAttribPointer(normal,3,GL_FLOAT,false,stride,12)
        glEnableVertexAttribArray(color); glVertexAttribPointer(color,3,GL_FLOAT,false,stride,24)
        glEnableVertexAttribArray(material); glVertexAttribPointer(material,1,GL_FLOAT,false,stride,36)
        glDrawArrays(GL_TRIANGLES,0,count)
    }
    private fun shader(type:Int,source:String):Int {
        val id=glCreateShader(type)
        glShaderSource(id,source); glCompileShader(id)
        val status=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,status,0)
        check(status[0] != 0) { glGetShaderInfoLog(id) }
        return id
    }

    companion object {
        private const val VERTEX="""
            uniform mat4 uMatrix;
            uniform float uDaylight;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            attribute float aMaterial;
            uniform mat4 uModel;
            uniform mediump float uModelMode;
            uniform vec4 uPaint;
            uniform float uSnow;
            uniform mediump vec2 uTireState;
            varying vec3 vColor;
            varying vec3 vPosition;
            varying float vDistance;
            varying vec3 vParticle;
            varying vec3 vTexturePosition;
            varying float vMaterial;
            varying vec3 vNormal;
            uniform mediump vec3 uEye;
            uniform mediump float uParticles;
            void main() {
                vec3 light=normalize(vec3(1.3,1.0,-0.7));
                vec3 position=(uModel*vec4(aPosition,1.0)).xyz;
                if(aMaterial>8.5) position.y=max(position.y,uTireState.x);
                vec3 normal=normalize((uModel*vec4(aNormal,0.0)).xyz);
                float diffuse=max(dot(normal,light),0.0);
                float brightness=(0.56+0.44*diffuse)*(0.16+0.84*uDaylight);
                vColor=aColor*brightness;
                if(uModelMode>0.5 && aMaterial>3.5 && aMaterial<4.5) vColor*=uPaint.rgb*(1.0-uPaint.a*0.23);
                if(uModelMode>0.5 && aNormal.y>0.45) vColor=mix(vColor,vec3(0.86,0.91,0.92)*brightness,uSnow*0.85);
                if(uParticles>0.5) vColor=aColor*(0.4+0.6*uDaylight);
                vPosition=position;
                vTexturePosition=uModelMode>0.5 ? aPosition : position;
                vMaterial=aMaterial;
                vNormal=normal;
                vParticle=aNormal;
                vDistance=length(position-uEye);
                gl_Position=uMatrix*vec4(position,1.0);
            }
        """
        private const val FRAGMENT="""
            precision mediump float;
            varying vec3 vColor;
            varying vec3 vPosition;
            varying float vDistance;
            varying vec3 vParticle;
            varying vec3 vTexturePosition;
            varying float vMaterial;
            varying vec3 vNormal;
            uniform sampler2D uSand;
            uniform sampler2D uEarth;
            uniform sampler2D uAsphalt;
            uniform sampler2D uMetal;
            uniform mediump float uModelMode;
            uniform mediump vec2 uTireState;
            uniform vec4 uCar;
            uniform float uHeadlights;
            uniform float uBrakes;
            uniform mediump vec3 uEye;
            uniform vec4 uFog;
            uniform vec2 uOrigin;
            uniform mediump float uParticles;
            void main() {
                if(vMaterial>8.5 && uTireState.y>1.5) discard;
                if(uParticles>0.5) {
                    float edge=max(0.0,1.0-length(vParticle.xy));
                    gl_FragColor=vec4(vColor,edge*edge*vParticle.z*0.32);
                    return;
                }
                vec3 detail=vec3(1.0);
                if(vMaterial>0.5 && vMaterial<4.5) {
                    vec2 plane=vTexturePosition.xz;
                    if(vMaterial>3.5) plane=vec2(vTexturePosition.x+vTexturePosition.z*0.43,vTexturePosition.y+vTexturePosition.z*0.71);
                    else plane+=uOrigin;
                    vec2 uv=plane*(vMaterial>3.5 ? 0.8 : 0.25);
                    vec3 tex;
                    if(vMaterial<1.5) tex=texture2D(uSand,uv).rgb;
                    else if(vMaterial<2.5) tex=texture2D(uEarth,uv).rgb;
                    else if(vMaterial<3.5) tex=texture2D(uAsphalt,uv).rgb;
                    else tex=texture2D(uMetal,uv).rgb;
                    float gray=dot(tex,vec3(0.299,0.587,0.114));
                    detail=vec3(0.73)+mix(vec3(gray),tex,0.15)*0.58;
                }
                vec2 delta=vPosition.xz-uCar.xz;
                float distanceToCar=length(delta);
                vec2 direction=vec2(cos(uCar.w),sin(uCar.w));
                float cone=pow(max(dot(delta/max(distanceToCar,0.01),direction),0.0),20.0);
                float headlight=uHeadlights*cone*exp(-distanceToCar*0.065)*1.4;
                vec3 lit=vColor*detail+vec3(1.0,0.9,0.63)*headlight;
                if(vMaterial>4.5 && vMaterial<6.5) {
                    vec3 normal=normalize(vNormal);
                    vec3 toEye=normalize(uEye-vPosition);
                    float fresnel=pow(1.0-abs(dot(normal,toEye)),3.0);
                    lit=mix(lit,vec3(0.49,0.62,0.65)*(0.35+fresnel*0.65),vMaterial<5.5 ? 0.55 : 0.28);
                }
                if(vMaterial>6.5 && vMaterial<7.5) lit+=vec3(1.0,0.91,0.64)*uHeadlights*0.65;
                if(vMaterial>7.5 && vMaterial<8.5) lit+=vec3(0.95,0.06,0.01)*(uBrakes+uHeadlights*0.12);
                float fog=1.0-exp(-vDistance*vDistance*0.000016*(1.0+uFog.w*10.0));
                gl_FragColor=vec4(mix(lit,uFog.rgb,min(fog,0.78)),1.0);
            }
        """
    }
}
