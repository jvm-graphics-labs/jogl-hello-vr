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
import com.sun.jna.ptr.PointerByReference
import glm_.L
import glm_.i
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2i
import openvr.lib.*
import kotlin.properties.Delegates


/**
 * Created by GBarbieri on 02.02.2017.
 */

fun main(args: Array<String>) {
    App()
}

val window = GLWindow.create(GLCapabilities(GLProfile.get("GL3")))
val animator = Animator(window)
lateinit var gl: GL3

var showCubes = true

lateinit var projection: Array<Mat4>
lateinit var eyePos: Array<Mat4>

operator fun <T> Array<T>.get(eye: EVREye) = get(eye.i)
operator fun <T> Array<T>.set(eye: EVREye, element: T) = set(eye.i, element)

val hmdPose = Mat4()

var hmd: IVRSystem by Delegates.notNull()

val trackedDevicePosesReference = TrackedDevicePose.ByReference()
val trackedDevicePose = trackedDevicePosesReference.toArray(maxTrackedDeviceCount) as Array<TrackedDevicePose>

val devicesPoses = Array(maxTrackedDeviceCount) { Mat4() }

val nearClip = .1f
val farClip = 30f

val trackedDeviceToRenderModel = mutableMapOf<Int, CGLRenderModel>()
val renderModels = mutableMapOf<String, CGLRenderModel>()

val showTrackedDevice = BooleanArray(maxTrackedDeviceCount)

var scene: Scene by Delegates.notNull()

var eyeDesc: Array<FrameBufferDesc> by Delegates.notNull()
var companionWindow: CompanionWindow by Delegates.notNull()

val companionWindowSize = Vec2i(640, 320)

var trackedControllerCount = 0
var controllerCountLast = -1
var validPoseCount = 0
var validPoseCountLast = -1

class App : GLEventListener, KeyListener {

    val debugOpengl = true
    /** what classes we saw poses for this frame    */
    var poseClasses = ""
    /** for each device, a character representing its class */
    val devClassChar = CharArray(maxTrackedDeviceCount)

    // TODO glm .c

    val eyeTexture = Array(EVREye.MAX) { Texture.ByReference() }

    init {

        with(window) {

            // Loading the SteamVR Runtime
            val error = EVRInitError_ByReference()  // default None
            hmd = vrInit(error, EVRApplicationType.Scene)!!

            if (error.value != EVRInitError.None)
                throw Error("Unable to init VR runtime: ${vrGetVRInitErrorAsEnglishDescription(error.value)}")

            if (vrGetGenericInterface(IVRRenderModels_Version, error) == Pointer.NULL)
                throw Error("Unable to get render model interface: ${vrGetVRInitErrorAsEnglishDescription(error.value)}")


            setPosition(700, 100)
            setSize(companionWindowSize.x, companionWindowSize.y)

            if (debugOpengl)
                contextCreationFlags = GLContext.CTX_OPTION_DEBUG

            val driver = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex_Hmd, ETrackedDeviceProperty.TrackingSystemName_String)
            val display = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex_Hmd, ETrackedDeviceProperty.SerialNumber_String)

            title = "hellovr - $driver $display"

            addGLEventListener(this@App)
            addKeyListener(this@App)

            autoSwapBufferMode = false

            isVisible = true

            if (vrCompositor == null)
                throw Error("Compositor initialization failed. See log file for details")

            animator.start()
        }
    }

    /**
     * Purpose: Initialize OpenGL. Returns true if OpenGL has been successfully initialized, false if shaders could not be created.
     * If failure occurred in a module other than shaders, the function may return true or throw an error.     */
    override fun init(drawable: GLAutoDrawable) {

        gl = drawable.gl.gL3

        if (debugOpengl) {

            gl.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true)
            gl.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS)
        }

        scene = Scene()   // textureMap inside

        setupCameras()

        setupStereoRenderTargets()

        companionWindow = CompanionWindow()

        setupRenderModels()
    }

    fun setupCameras() {
        projection = Array(EVREye.MAX) { getHmdMatrixProjectionEye(EVREye.of(it)) }
        eyePos = Array(EVREye.MAX) { getHmdMatrixPoseEye(EVREye.of(it)) }
    }

    fun setupStereoRenderTargets() {
        val size = hmd.recommendedRenderTargetSize
        eyeDesc = Array(EVREye.MAX) { FrameBufferDesc(size) }
    }

    /** Purpose: Create/destroy GL Render Models    */
    fun setupRenderModels() {
        for (i in trackedDeviceIndex_Hmd + 1 until maxTrackedDeviceCount)
            if (hmd isTrackedDeviceConnected i)
                setupRenderModelForTrackedDevice(i)
    }

    /** Purpose: Create/destroy GL a Render Model for a single tracked device   */
    fun setupRenderModelForTrackedDevice(trackedDeviceIndex: Int) {

        if (trackedDeviceIndex >= maxTrackedDeviceCount) return

        // try to find a model we've already set up
        val renderModelName = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex, ETrackedDeviceProperty.RenderModelName_String)
        val renderModel = findOrLoadRenderModel(gl, renderModelName)
        if (renderModel == null) {
            val trackingSystemName = hmd.getStringTrackedDeviceProperty(trackedDeviceIndex, ETrackedDeviceProperty.TrackingSystemName_String)
            println("Unable to load render model for tracked device $trackedDeviceIndex ($trackingSystemName.$renderModelName)")
        } else {
            trackedDeviceToRenderModel[trackedDeviceIndex] = renderModel
            showTrackedDevice[trackedDeviceIndex] = true
        }
    }

    /** Purpose: Finds a render model we've already loaded or loads a new one   */
    fun findOrLoadRenderModel(gl: GL3, renderModelName: String): CGLRenderModel? {

        renderModels[renderModelName]?.let { return it }

        // load the model if we didn't find one
        val error = EVRRenderModelError.None

        val vrRM = vrRenderModels!!

        val pModel = PointerByReference()
        while (true) {
            if (vrRM.loadRenderModel_Async(renderModelName, pModel) != EVRRenderModelError.Loading)
                break
            Thread.sleep(1)
        }
        val model = RenderModel.ByReference(pModel.value)

        if (error != EVRRenderModelError.None) {
            System.err.println("Unable to load render model $renderModelName - ${error.getName()}")
            return null // move on to the next tracked device
        }

        val pTexture = PointerByReference()
        while (true) {
            if (vrRM.loadTexture_Async(model.diffuseTextureId, pTexture) != EVRRenderModelError.Loading)
                break
            Thread.sleep(1)
        }
        val texture = RenderModel_TextureMap.ByReference(pTexture.value)

        if (error != EVRRenderModelError.None) {
            System.err.println("Unable to load render texture id:${model.diffuseTextureId} for render model $renderModelName")
            vrRM freeRenderModel model
            return null // move on to the next tracked device
        }

        renderModels[renderModelName] = CGLRenderModel(renderModelName, model, texture)

        vrRM freeRenderModel model
        vrRM freeTexture texture

        return renderModels[renderModelName]
    }

    /** Purpose: Gets a Matrix Projection Eye with respect to nEye. */
    fun getHmdMatrixProjectionEye(eye: EVREye) = hmd.getProjectionMatrix(eye, nearClip, farClip)

    /** Purpose: Gets an HMDMatrixPoseEye with respect to nEye. */
    fun getHmdMatrixPoseEye(eye: EVREye) = hmd.getEyeToHeadTransform(eye).inverse()

    override fun display(drawable: GLAutoDrawable) {

        gl = drawable.gl.gL3

        handleInputs()

        // for now as fast as possible
        scene.controllerAxes.update()
        renderStereoTargets()
        companionWindow.render()

        for (eye in EVREye.values()) {
            eyeTexture[eye].put(eyeDesc[eye].textureName[FrameBufferDesc.Target.RESOLVE].L, ETextureType.OpenGL, EColorSpace.Gamma)
            vrCompositor!!.submit(eye, eyeTexture[eye.i])
        }

        drawable.swapBuffers()

        // Spew out the controller and pose count whenever they change.
        if (trackedControllerCount != controllerCountLast || validPoseCount != validPoseCountLast) {
            validPoseCountLast = validPoseCount
            controllerCountLast = trackedControllerCount

            println("PoseCount:$validPoseCount ($poseClasses) Controllers: $trackedControllerCount")
        }

        updateHMDMatrixPose()
    }

    fun renderStereoTargets() = with(gl) {

        glClearBufferfv(GL_COLOR, 0, clearColor)

        for (eye in EVREye.values())
            eyeDesc[eye].render(eye)
    }

    fun updateHMDMatrixPose() {

        vrCompositor!!.waitGetPoses(trackedDevicePosesReference, maxTrackedDeviceCount, null, 0)

        validPoseCount = 0
        poseClasses = ""

        for(device in 0 until maxTrackedDeviceCount)

            if (trackedDevicePose[device].poseIsValid) {

                validPoseCount++
                devicesPoses[device] = trackedDevicePose[device].deviceToAbsoluteTracking to Mat4()
                if (devClassChar[device] == '\u0000')
                    devClassChar[device] = when (hmd.getTrackedDeviceClass(device)) {
                        ETrackedDeviceClass.Controller -> 'C'
                        ETrackedDeviceClass.HMD -> 'H'
                        ETrackedDeviceClass.Invalid -> 'I'
                        ETrackedDeviceClass.GenericTracker -> 'G'
                        ETrackedDeviceClass.TrackingReference -> 'T'
                        else -> '?'
                    }
                poseClasses += devClassChar[device]
            }

        if (trackedDevicePose[trackedDeviceIndex_Hmd].poseIsValid)
            devicesPoses[trackedDeviceIndex_Hmd].inverse(hmdPose)
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

    fun handleInputs() {

        // Process SteamVR events
        val event = VREvent.ByReference()
        while (hmd.pollNextEvent(event, event.size()))
            event.process()

        // Process SteamVR controller state
        for (device in 0 until maxTrackedDeviceCount) {
            val state = VRControllerState.ByReference()
            if (hmd.getControllerState(device, state, state.size()))
                showTrackedDevice[device] = state.buttonPressed == 0L
        }
    }

    /** Purpose: Processes a single VR event    */
    fun VREvent.process() {

        val id = trackedDeviceIndex

        when (eventType) {
            EVREventType.TrackedDeviceActivated -> {
                setupRenderModelForTrackedDevice(id)
                println("Device $id attached. Setting up render model.")
            }
            EVREventType.TrackedDeviceDeactivated -> println("Device $id detached.")
            EVREventType.TrackedDeviceUpdated -> println("Device $id updated.")
            else -> Unit
        }
    }

    override fun keyReleased(e: KeyEvent?) {
    }
}