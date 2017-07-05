package hellovr

import com.jogamp.newt.event.KeyEvent
import com.jogamp.newt.event.KeyListener
import com.jogamp.newt.opengl.GLWindow
import com.jogamp.opengl.*
import com.jogamp.opengl.GL.GL_DONT_CARE
import com.jogamp.opengl.GL2ES2.GL_DEBUG_OUTPUT_SYNCHRONOUS
import com.jogamp.opengl.GL2ES3.GL_COLOR
import com.jogamp.opengl.util.Animator
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import glm_.mat4x4.Mat4
import openvr.*
import glm_.vec2.Vec2i
import kotlin.properties.Delegates


/**
 * Created by GBarbieri on 02.02.2017.
 */

fun main(args: Array<String>) {
    App()
}

var window = GLWindow.create(GLCapabilities(GLProfile.get(GLProfile.GL3)))
var animator = Animator(window)

var showCubes = true

val projection = Array<Mat4>(EVREye.MAX, { Mat4() })
val eyePos = Array<Mat4>(EVREye.MAX, { Mat4() })

var hmdPose = Mat4() // TODO val

var hmd: IVRSystem by Delegates.notNull<IVRSystem>()

val trackedDevicePosesReference = TrackedDevicePose_t.ByReference()
@Suppress("UNCHECKED_CAST")
val trackedDevicePose = trackedDevicePosesReference.toArray(vr.maxTrackedDeviceCount) as Array<TrackedDevicePose_t>

val devicesPoses = mutableMapOf<Int, Mat4>()

val nearClip = .1f
val farClip = 30f

val trackedDeviceToRenderModel = mutableMapOf<Int, CGLRenderModel>()
val renderModels = mutableMapOf<String, CGLRenderModel>()

val showTrackedDevice = BooleanArray(vr.maxTrackedDeviceCount)

var scene: Scene by Delegates.notNull<Scene>()

var eyeDesc: Array<FrameBufferDesc> by Delegates.notNull<Array<FrameBufferDesc>>()
var companionWindow: CompanionWindow by Delegates.notNull<CompanionWindow>()

val companionWindowSize = Vec2i(640, 320)

var controllerCount = 0
var controllerCountLast = -1
var validPoseCount = 0
var validPoseCountLast = -1

class App : GLEventListener, KeyListener {

    val debugOpengl = false

    var poseClasses = ""    // what classes we saw poses for this frame
    val devClassChar = Array(vr.maxTrackedDeviceCount, { '\u0000' })  // for each device, a character representing its class

    // TODO glm .c

    val eyeTexture = Array<Texture_t.ByReference>(EVREye.MAX, { Texture_t.ByReference() })

    init {

        with(window) {

            val error = EVRInitError_ByReference(EVRInitError.None)
            hmd = vr.init(error, EVRApplicationType.Scene)!!

            if (error.value != EVRInitError.None)
                throw Error("Unable to init VR runtime: ${vr.getVRInitErrorAsEnglishDescription(error.value)}")

            if (vr.getGenericInterface(vr.IVRRenderModels_Version, error) == Pointer.NULL)
                throw Error("Unable to get render model interface: ${vr.getVRInitErrorAsEnglishDescription(error.value)}")


            setPosition(700, 100)
            setSize(companionWindowSize.x, companionWindowSize.y)

            if (debugOpengl)
                contextCreationFlags = GLContext.CTX_OPTION_DEBUG

            title = "hellovr"

            addGLEventListener(this@App)
            addKeyListener(this@App)

            autoSwapBufferMode = false

            // our initGL()
            isVisible = true

            // init compositor
            if (vr.compositor == null)
                System.err.println("Compositor initialization failed. See log file for details")

            animator.start()
        }
    }

    /**
     * Purpose: Initialize OpenGL. Returns true if OpenGL has been successfully initialized, false if shaders could not be created.
     *          If failure occurred in a module other than shaders, the function may return true or throw an error.     */
    override fun init(drawable: GLAutoDrawable) {

        val gl = drawable.gl.gL3

        if (debugOpengl) {

            gl.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true)
            gl.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS)
        }

        scene = Scene(gl)   // textureMap inside
        setupCameras()
        setupStereoRenderTargets(gl)
        companionWindow = CompanionWindow(gl)
        // setup renderModels
        for (i in vr.trackedDeviceIndex_Hmd + 1 until vr.maxTrackedDeviceCount)
            if (hmd.isTrackedDeviceConnected(i))
                setupRenderModelForTrackedDevice (gl, i)

//        for (eye in EVREye.values())
//            eyeTextures[eye.i].set(eyeDesc[eye.i].textureName[FrameBufferDesc.Target.RESOLVE],ETextureType.OpenGL.i)
    }

    fun setupCameras() {
        for (eye in EVREye.values()) {
            projection[eye.i] = getHmdMatrixProjectionEye(eye)
            eyePos[eye.i] = getHmdMatrixPoseEye(eye)
        }
    }

    fun setupStereoRenderTargets(gl: GL3) {

        val width = IntByReference()
        val height = IntByReference()

        hmd.getRecommendedRenderTargetSize(width, height)

        eyeDesc = Array(EVREye.MAX, { FrameBufferDesc(gl, width, height) })
    }

    /** Purpose: Create/destroy GL a Render Model for a single tracked device   */
    //    fun setupRenderModelForTrackedDevice(gl: GL3, trackedDeviceIndex: TrackedDeviceIndex_t) {
    fun setupRenderModelForTrackedDevice(gl: GL3, trackedDeviceIndex: Int) {    // TODO

        if (trackedDeviceIndex >= vr.maxTrackedDeviceCount)
            return

        // try to find a model we've already set up
        val renderModelName = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex, ETrackedDeviceProperty.RenderModelName_String)
        val renderModel = findOrLoadRenderModel(gl, renderModelName)
        if (renderModel == null) {
            val trackingSystemName = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex,
                    ETrackedDeviceProperty.TrackingSystemName_String)
            println("Unable to load render model for tracked device $trackedDeviceIndex ($trackingSystemName.$renderModelName)")
        } else {
            trackedDeviceToRenderModel[trackedDeviceIndex] = renderModel
            showTrackedDevice[trackedDeviceIndex] = true
        }
    }

    /** Purpose: Finds a render model we've already loaded or loads a new one   */
    fun findOrLoadRenderModel(gl: GL3, renderModelName: String): CGLRenderModel? {

        renderModels[renderModelName]?.let {
            return it
        }

        // load the model if we didn't find one
        val ppModel = PointerByReference()
        val error = EVRRenderModelError.None

        while (true) {
            if (vr.renderModels!!.loadRenderModel_Async(renderModelName, ppModel) != EVRRenderModelError.Loading)
                break
            Thread.sleep(1)
        }

        if (error != EVRRenderModelError.None) {
            System.err.println("Unable to load render model $renderModelName - ${error.getName()}")
            return null // move on to the next tracked device
        }

        val pModel = RenderModel_t.ByReference(ppModel.value)
        val ppTexture = PointerByReference()

        while (true) {
            if (vr.renderModels!!.loadTexture_Async(pModel.diffuseTextureId, ppTexture) != EVRRenderModelError.Loading)
                break
            Thread.sleep(1)
        }

        if (error != EVRRenderModelError.None) {
            System.err.println("Unable to load render texture id:${pModel.diffuseTextureId} for render model $renderModelName")
            vr.renderModels!!.freeRenderModel(pModel)
            return null // move on to the next tracked device
        }

        val pTexture = RenderModel_TextureMap_t.ByReference(ppTexture.value)

        renderModels[renderModelName] = CGLRenderModel(renderModelName, gl, pModel, pTexture)

        vr.renderModels!!.freeRenderModel(pModel)
        vr.renderModels!!.freeTexture(pTexture)

        return renderModels[renderModelName]
    }

    /** Purpose: Gets a Matrix Projection Eye with respect to nEye. */
    fun getHmdMatrixProjectionEye(eye: EVREye) = hmd.getProjectionMatrix(eye, nearClip, farClip).toMa4()

    /** Purpose: Gets an HMDMatrixPoseEye with respect to nEye. */
    fun getHmdMatrixPoseEye(eye: EVREye) = hmd.getEyeToHeadTransform(eye).toMa4().inverse()

    override fun display(drawable: GLAutoDrawable) {

        val gl = drawable.gl.gL3

        processVREvents(gl)

        scene.controllerAxes.updateControllerAxes(gl)
        renderStereoTargets(gl)
        companionWindow.render(gl)

        for (eye in EVREye.values()) {
            eyeTexture[eye.i].put(eyeDesc[eye.i].textureName[FrameBufferDesc.Target.RESOLVE], ETextureType.OpenGL, EColorSpace.Gamma)
            vr.compositor!!.submit(eye, eyeTexture[eye.i])
        }

        drawable.swapBuffers()


        // Spew out the controller and pose count whenever they change.
        if (controllerCount != controllerCountLast || validPoseCount != validPoseCountLast) {
            validPoseCountLast = validPoseCount
            controllerCountLast = controllerCount

            println("PoseCount:$validPoseCount ($poseClasses) Controllers: $controllerCount")
        }

        updateHMDMatrixPose()
    }

    fun renderStereoTargets(gl: GL3) = with(gl) {

        glClearBufferfv(GL_COLOR, 0, clearColor)

        for (eye in EVREye.values())
            eyeDesc[eye.i].render(gl, eye)
    }

    fun updateHMDMatrixPose() {

        vr.compositor!!.waitGetPoses(trackedDevicePosesReference, vr.maxTrackedDeviceCount, null, 0)

        validPoseCount = 0
        poseClasses = ""

        repeat(vr.maxTrackedDeviceCount) {

            if (trackedDevicePose[it].bPoseIsValid) {

                validPoseCount++
                devicesPoses[it] = trackedDevicePose[it].mDeviceToAbsoluteTracking.toMa4() // TODO put
                if (devClassChar[it] == 0.toChar())
                    devClassChar[it] = when (hmd.getTrackedDeviceClass(it)) {
                        ETrackedDeviceClass.Controller -> 'C'
                        ETrackedDeviceClass.HMD -> 'H'
                        ETrackedDeviceClass.Invalid -> 'I'
                        ETrackedDeviceClass.GenericTracker -> 'G'
                        ETrackedDeviceClass.TrackingReference -> 'T'
                        else -> '?'
                    }
                poseClasses += devClassChar[it]
            }
        }

        if (trackedDevicePose[vr.trackedDeviceIndex_Hmd].bPoseIsValid)
            hmdPose = devicesPoses[vr.trackedDeviceIndex_Hmd]!!.inverse_()
    }

    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        drawable.gl.gL3.glViewport(x, y, width, height)
        companionWindowSize.put(width, height)
    }

    override fun dispose(drawable: GLAutoDrawable) {
        System.exit(1)
    }

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_C -> showCubes = !showCubes
            KeyEvent.VK_ESCAPE, KeyEvent.VK_Q -> {
                animator.remove(window)
                window.destroy()
            }
        }
    }

    fun processVREvents(gl: GL3) {

        // Process SteamVR events
        val event = VREvent_t.ByReference()
        while (hmd.pollNextEvent(event, event.size()))
            processVREvent(gl, event)

        // Process SteamVR controller state
        repeat(vr.maxTrackedDeviceCount) {
            val state = VRControllerState_t.ByReference()
            if (hmd.getControllerState(it, state, state.size()))
                showTrackedDevice[it] = state.ulButtonPressed == 0L
        }
    }

    /** Purpose: Processes a single VR event    */
    fun processVREvent(gl: GL3, event: VREvent_t) {

        val id = event.trackedDeviceIndex

        when (event.eventType) {

            EVREventType.TrackedDeviceActivated -> {
                setupRenderModelForTrackedDevice(gl, id)
                println("Device $id attached. Setting up render model.")
            }

            EVREventType.TrackedDeviceDeactivated -> println("Device $id detached.")

            EVREventType.TrackedDeviceUpdated -> println("Device $id updated.")
        }
    }

    override fun keyReleased(e: KeyEvent?) {
    }


    fun HmdMatrix44_t.toMa4() = Mat4(
            m[0], m[4], m[8], m[12],
            m[1], m[5], m[9], m[13],
            m[2], m[6], m[10], m[14],
            m[3], m[7], m[11], m[15])

    fun HmdMatrix34_t.toMa4() = Mat4(
            m[0], m[4], m[8], 0,
            m[1], m[5], m[9], 0,
            m[2], m[6], m[10], 0,
            m[3], m[7], m[11], 1)

}