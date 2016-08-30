/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_DONT_CARE;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_OUTPUT_SYNCHRONOUS;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.opengl.GLWindow;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DRAW_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_MULTISAMPLE;
import static com.jogamp.opengl.GL.GL_NO_ERROR;
import static com.jogamp.opengl.GL.GL_READ_FRAMEBUFFER;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.GLBuffers;

import glm.mat._4.Mat4;
import glm.vec._2.i.Vec2i;
import glutil.BufferUtils;
import glutil.GlDebugOutput;
import one.util.streamex.IntStreamEx;
import vr.HmdMatrix34_t;
import vr.HmdMatrix44_t;
import vr.IVRCompositor_FnTable;
import vr.IVRSystem;
import vr.Texture_t;
import vr.TrackedDevicePose_t;
import vr.VR;

/**
 *
 * @author GBarbieri
 */
public class Application implements GLEventListener, KeyListener {

    public static final String SHADERS_ROOT = "/helloVr/shaders";

    private static GLWindow glWindow;
    private static Animator animator;
    private boolean debugOpenGL = false;
    public Vec2i windowSize = new Vec2i(1280, 720);

    public static void main(String[] args) {

        Application app = new Application();

        glWindow.addGLEventListener(app);
        glWindow.addKeyListener(app);
    }

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int INDEX = 1;
        public static final int MAX = 2;
    }

    public IVRSystem hmd;
    private TrackedDevicePose_t.ByReference trackedDevicePosesReference = new TrackedDevicePose_t.ByReference();
    private TrackedDevicePose_t[] trackedDevicePoses
            = (TrackedDevicePose_t[]) trackedDevicePosesReference.toArray(VR.k_unMaxTrackedDeviceCount);
    private static Mat4[] poseMatrices;

    private Vec2i renderSize = new Vec2i();
    private boolean vBlank = false;
    private boolean glFinishHack = true;
    private int vertexCount = 0, indexSize;
    private float nearClip = 0.1f, farClip = 30.0f;
    private IntBuffer buffername = GLBuffers.newDirectIntBuffer(Buffer.MAX);
    public FramebufferDesc[] eyeDesc = new FramebufferDesc[VR.EVREye.Max];
    private Scene scene;

    public FloatBuffer clearColor = GLBuffers.newDirectFloatBuffer(4), clearDepth = GLBuffers.newDirectFloatBuffer(1),
            matBuffer = GLBuffers.newDirectFloatBuffer(16);
    public boolean showCubes = true;

    public Mat4[] projection = new Mat4[VR.EVREye.Max], eyePos = new Mat4[VR.EVREye.Max];
    public Mat4 hmdPose = new Mat4();

    private IntBuffer errorBuffer = GLBuffers.newDirectIntBuffer(1);
    private IVRCompositor_FnTable compositor;

    private Texture_t[] eyeTexture = new Texture_t[VR.EVREye.Max];

    private Distortion distortion;

    public Application() {

        Display display = NewtFactory.createDisplay(null);
        Screen screen = NewtFactory.createScreen(display, 0);
        GLProfile glProfile = GLProfile.get(GLProfile.GL4);
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glWindow = GLWindow.create(screen, glCapabilities);

        glWindow.setSize(windowSize.x, windowSize.y);
        glWindow.setPosition(700, 100);
        glWindow.setUndecorated(false);
        glWindow.setAlwaysOnTop(false);
        glWindow.setFullscreen(false);
        glWindow.setPointerVisible(true);
        glWindow.confinePointer(false);
        glWindow.setTitle("hellovr_jogl");

        if (debugOpenGL) {
            glWindow.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);
        }
        glWindow.setVisible(true);
        if (debugOpenGL) {
            glWindow.getContext().addGLDebugListener(new GlDebugOutput());
        }

        glWindow.setAutoSwapBufferMode(false);
        animator = new Animator(glWindow);
        animator.start();
    }

    @Override
    public void init(GLAutoDrawable drawable) {

        // Loading the SteamVR Runtime
        hmd = VR.VR_Init(errorBuffer, VR.EVRApplicationType.VRApplication_Scene);
        //        HmdMatrix44_t mat = app.hmd.GetProjectionMatrix.apply(0, app.nearClip, app.farClip,
        //                VR.EGraphicsAPIConvention.API_OpenGL);
        if (errorBuffer.get(0) != VR.EVRInitError.VRInitError_None) {
            hmd = null;
            String s = "Unable to init VR runtime: " + VR.VR_GetVRInitErrorAsEnglishDescription(errorBuffer.get(0));
            throw new Error("VR_Init Failed, " + s);
        }

        hmd.read();

        //TODO:
        // init controllers for the first time
        //VRInput._updateConnectedControllers();
        // init bounds & chaperone info
        //VRBounds.init();
        GL4 gl4 = drawable.getGL().getGL4();

        gl4.setSwapInterval(vBlank ? 1 : 0);

        if (!initGL(gl4)) {
            System.err.println("Unable to initialize OpenGL!");
            quit();
        }

        if (!initCompositor()) {
            System.err.println("Failed to initialize VR Compositor!");
            quit();
        }

        //based on initialize from https://github.com/phr00t/jMonkeyVR/blob/76acf51383d9325b493aa8648494850d184e7a2b/src/jmevr/input/OpenVR.java
        //not sure main method is my favorite place for all this stuff, TODO: cleanup (when I'm dead)
//        hmdTrackedDevicePoseReference = new TrackedDevicePose_t.ByReference();
//        hmdTrackedDevicePoses = (TrackedDevicePose_t[]) hmdTrackedDevicePoseReference.toArray(VR.k_unMaxTrackedDeviceCount);
//        poseMatrices = new Mat4[VR.k_unMaxTrackedDeviceCount];
//        for (int i = 0; i < poseMatrices.length; i++) {
//            poseMatrices[i] = new Mat4();
//        }
//        //hmdPose = new Mat4();
//
//        // disable all this stuff which kills performance
//        hmdTrackedDevicePoseReference.setAutoRead(false);
//        hmdTrackedDevicePoseReference.setAutoWrite(false);
//        hmdTrackedDevicePoseReference.setAutoSynch(false);
//        for (int i = 0; i < VR.k_unMaxTrackedDeviceCount; i++) {
//            hmdTrackedDevicePoses[i].setAutoRead(false);
//            hmdTrackedDevicePoses[i].setAutoWrite(false);
//            hmdTrackedDevicePoses[i].setAutoSynch(false);
//        }
        checkError(gl4, "init");
    }

    private boolean initGL(GL4 gl4) {

        if (debugOpenGL) {

            gl4.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true);
            gl4.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }

        setupScene(gl4); // setupTextureMaps() inside
        setupCameras();
        setupStereoRenderTargets(gl4);
        setupDistortion(gl4);

        setupRenderModels(gl4);

        IntStreamEx.range(VR.EVREye.Max).forEach(eye -> eyeDesc[eye] = new FramebufferDesc(gl4, renderSize));

        return true;
    }

    private void setupScene(GL4 gl4) {
        if (hmd == null) {
            return;
        }
        scene = new Scene(gl4);
    }

    private void setupCameras() {
        for (int eye = 0; eye < VR.EVREye.Max; eye++) {
            projection[eye] = getHmdMatrixProjection(eye);
            eyePos[eye] = getHmdMatrixPoseEye(eye);
        }
    }

    private Mat4 getHmdMatrixProjection(int eye) {
        if (hmd == null) {
            return new Mat4();
        }
        HmdMatrix44_t mat = hmd.GetProjectionMatrix.apply(eye, nearClip, farClip, VR.EGraphicsAPIConvention.API_OpenGL);
        return new Mat4(mat.m);
    }

    private Mat4 getHmdMatrixPoseEye(int eye) {
        if (hmd == null) {
            return new Mat4();
        }
        HmdMatrix34_t mat = hmd.GetEyeToHeadTransform.apply(eye);
        Mat4 matrixObj = new Mat4(
                mat.m[0], mat.m[1], mat.m[2], mat.m[3],
                mat.m[4], mat.m[5], mat.m[6], mat.m[7],
                mat.m[8], mat.m[9], mat.m[10], mat.m[11],
                0, 0, 0, 1);
        return matrixObj.inverse();
    }

    private boolean setupStereoRenderTargets(GL4 gl4) {
        if (hmd == null) {
            return false;
        }
        IntBuffer width = GLBuffers.newDirectIntBuffer(1), height = GLBuffers.newDirectIntBuffer(1);

        hmd.GetRecommendedRenderTargetSize.apply(width, height);
        renderSize.set(width.get(0), height.get(0));

        BufferUtils.destroyDirectBuffer(width);
        BufferUtils.destroyDirectBuffer(height);

        IntStreamEx.range(VR.EVREye.Max).forEach(eye -> eyeTexture[eye] = new Texture_t());

        return true;
    }

    private void setupDistortion(GL4 gl4) {
        if (hmd == null) {
            return;
        }
        distortion = new Distortion(gl4, hmd);
    }

    private boolean setupRenderModels(GL4 gl4) {
        return true;
    }

    private boolean initCompositor() {

        compositor = new IVRCompositor_FnTable(VR.VR_GetGenericInterface(VR.IVRCompositor_Version, errorBuffer));
        compositor.setAutoSynch(false); // TODO necessary?
        compositor.read();
//        compositor.write();

        if (compositor == null || errorBuffer.get(0) != VR.EVRInitError.VRInitError_None) {
            System.err.println("Compositor initialization failed. See log file for details");
            return false;
        }
        return true;
    }

    @Override
    public void display(GLAutoDrawable drawable) {

        GL4 gl4 = drawable.getGL().getGL4();

        // for now as fast as possible
        if (hmd != null) {
//		DrawControllers();
            renderStereoTargets(gl4);
            distortion.render(gl4, this);

            for (int eye = 0; eye < VR.EVREye.Max; eye++) {
                eyeTexture[eye].set(eyeDesc[eye].textureName.get(FramebufferDesc.Target.RESOLVE),
                        VR.EGraphicsAPIConvention.API_OpenGL, VR.EColorSpace.ColorSpace_Gamma);
                compositor.Submit.apply(eye, eyeTexture[eye], null, VR.EVRSubmitFlags.Submit_Default);
            }
        }

        if (vBlank && glFinishHack) {
            /**
             * $ HACKHACK. From gpuview profiling, it looks like there is a bug where two renders and a present
             * happen right before and after the vsync causing all kinds of jittering issues. This glFinish()
             * appears to clear that up. Temporary fix while I try to get nvidia to investigate this problem.
             * 1/29/2014 mikesart.
             */
            gl4.glFinish();
        }

        // SwapWindow
        {
            glWindow.swapBuffers();
        }
        // Clear
        {
//            gl4.glBindFramebuffer(GL_FRAMEBUFFER, eyeDesc[0].framebufferName.get(FramebufferDesc.Target.RESOLVE));
//            gl4.glViewport(0, 0, renderSize.x, renderSize.y);
//            clearColor.put(0, 0.95f).put(1, 0.15f).put(2, 0.18f).put(3, 1.0f);
//            gl4.glClearBufferfv(GL_COLOR, 0, clearColor);
//
//            gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, eyeDesc[0].framebufferName.get(FramebufferDesc.Target.RESOLVE));
//            gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
//            gl4.glBlitFramebuffer(0, 0, renderSize.x, renderSize.y, 0, 0, renderSize.x, renderSize.y,
//                    GL_COLOR_BUFFER_BIT, GL_LINEAR);
//            gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);
            /**
             * We want to make sure the glFinish waits for the entire present to complete, not just the submission
             * of the command. So, we do a clear here right here so the glFinish will wait fully for the swap.
             */
            gl4.glClearBufferfv(GL_COLOR, 0, clearColor);
        }
        // Flush and wait for swap.
        if (vBlank) {
            gl4.glFlush();
            gl4.glFinish();
        }

        // Spew out the controller and pose count whenever they change.
//	if ( m_iTrackedControllerCount != m_iTrackedControllerCount_Last || m_iValidPoseCount != m_iValidPoseCount_Last )
//	{
//		m_iValidPoseCount_Last = m_iValidPoseCount;
//		m_iTrackedControllerCount_Last = m_iTrackedControllerCount;
//		
//		dprintf( "PoseCount:%d(%s) Controllers:%d\n", m_iValidPoseCount, m_strPoseClasses.c_str(), m_iTrackedControllerCount );
//	}
        updateHMDMatrixPose();
        checkError(gl4, "display");
    }

    private void renderStereoTargets(GL4 gl4) {

        clearColor.put(0, 0.15f).put(1, 0.15f).put(2, 0.18f).put(3, 1.0f);  // nice background color, but not black

        for (int eye = 0; eye < VR.EVREye.Max; eye++) {

            gl4.glEnable(GL_MULTISAMPLE);

            gl4.glBindFramebuffer(GL_FRAMEBUFFER, eyeDesc[eye].framebufferName.get(FramebufferDesc.Target.RENDER));
            gl4.glViewport(0, 0, renderSize.x, renderSize.y);
            scene.render(gl4, this, eye);
            gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);

            gl4.glDisable(GL_MULTISAMPLE);

            gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, eyeDesc[eye].framebufferName.get(FramebufferDesc.Target.RENDER));
            gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, eyeDesc[eye].framebufferName.get(FramebufferDesc.Target.RESOLVE));

            gl4.glBlitFramebuffer(0, 0, renderSize.x, renderSize.y, 0, 0, renderSize.x, renderSize.y,
                    GL_COLOR_BUFFER_BIT,
                    GL_LINEAR);
        }
        gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    }

    private void updateHMDMatrixPose() {
        if (hmd == null) {
            return;
        }

        //nb, in jMonkeyVR there is a lengthy case for null compositor that seems irrelevant
        //(pretty sure we can happily just use compositor, not sure we even have a choice)
        compositor.WaitGetPoses.apply(trackedDevicePosesReference, VR.k_unMaxTrackedDeviceCount, null, 0);
        //TODO VRInput._updateControllerStates();

        // read pose data from native (copying from jMonkeyVR)
//        for (int nDevice = 0; nDevice < VR.k_unMaxTrackedDeviceCount; ++nDevice) {
//            TrackedDevicePose_t pose_t = hmdTrackedDevicePoses[nDevice];
//            pose_t.readField("bPoseIsValid");
//            if (pose_t.bPoseIsValid != 0) {
//                pose_t.readField("mDeviceToAbsoluteTracking");
//                // OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(pose_t.mDeviceToAbsoluteTracking, poseMatrices[nDevice]);
//                convertSteamVRMatrix3ToMat4(pose_t.mDeviceToAbsoluteTracking, poseMatrices[nDevice]);
//            }
//        }
//
//        if (hmdTrackedDevicePoses[VR.k_unTrackedDeviceIndex_Hmd].bPoseIsValid != 0) {
//            hmdPose.set(poseMatrices[VR.k_unTrackedDeviceIndex_Hmd]);
//            //Mat4 could really use a toString() override...
//            //hmdPose.print(true);
//        } else {
//            hmdPose.identity();
//        }
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        System.exit(0);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

        GL4 gl4 = drawable.getGL().getGL4();

        gl4.glViewport(0, 0, width, height);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
                quit();
                break;
        }
    }

    private void quit() {
        animator.remove(glWindow);
        glWindow.destroy();
        System.exit(0);
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    public static void checkError(GL4 gl4, String string) {
        int error = gl4.glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("error " + error + ", " + string);
        }
    }
}
