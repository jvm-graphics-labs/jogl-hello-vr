/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.opengl.GLWindow;
import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DONT_CARE;
import static com.jogamp.opengl.GL.GL_DRAW_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_LINEAR_MIPMAP_LINEAR;
import static com.jogamp.opengl.GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_MULTISAMPLE;
import static com.jogamp.opengl.GL.GL_READ_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_OUTPUT_SYNCHRONOUS;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import glm.mat._4.Mat4;
import glm.vec._2.Vec2;
import glm.vec._2.i.Vec2i;
import glutil.BufferUtils;
import glutil.GlDebugOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jgli.Texture2d;
import jopenvr.DistortionCoordinates_t;
import jopenvr.HmdMatrix34_t;
import jopenvr.HmdMatrix44_t;
import jopenvr.IVRCompositor;
import jopenvr.VR;
import jopenvr.IVRSystem;
import jopenvr.Texture_t;

/**
 *
 * @author GBarbieri
 */
public class App implements GLEventListener, KeyListener {

    private static GLWindow glWindow;
    private static Animator animator;
    private static final boolean debugOpenGL = false;
    private static Vec2i windowSize = new Vec2i(1280, 720);

    public static void main(String[] args) {

        App app = new App();

        // Loading the SteamVR Runtime
        IntBuffer error = GLBuffers.newDirectIntBuffer(new int[]{VR.EVRInitError.VRInitError_None});
        app.hmd = VR.VR_Init(error, VR.EVRApplicationType.VRApplication_Scene);

//        HmdMatrix44_t mat = app.hmd.GetProjectionMatrix.apply(0, app.nearClip, app.farClip,
//                VR.EGraphicsAPIConvention.API_OpenGL);
        if (error.get(0) != VR.EVRInitError.VRInitError_None) {

            app.hmd = null;
            String s = "Unable to init VR runtime: " + VR.VR_GetVRInitErrorAsEnglishDescription(error.get(0));
            throw new Error("VR_Init Failed, " + s);
        }
        compositor = new IVRCompositor(VR.VR_GetGenericInterface(VR.IVRCompositor_Version, error));
        compositor.read();
        
        Display display = NewtFactory.createDisplay(null);
        Screen screen = NewtFactory.createScreen(display, 0);
        GLProfile glProfile = GLProfile.get(GLProfile.GL4);
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glWindow = GLWindow.create(screen, glCapabilities);

        glWindow.setSize(windowSize.x, windowSize.y);
        glWindow.setPosition(50, 50);
        glWindow.setUndecorated(false);
        glWindow.setAlwaysOnTop(false);
        glWindow.setFullscreen(false);
        glWindow.setPointerVisible(true);
        glWindow.confinePointer(false);
        glWindow.setTitle("Hello VR");

        if (debugOpenGL) {
            glWindow.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);
        }
        glWindow.setVisible(true);
        if (debugOpenGL) {
            glWindow.getContext().addGLDebugListener(new GlDebugOutput());
        }

        glWindow.addGLEventListener(app);
        glWindow.addKeyListener(app);

        animator = new Animator(glWindow);
        animator.start();
    }

    private final String TEXTURE_PATH = "/helloVr/asset/cube_texture.png", SHADERS_ROOT = "/helloVr/shaders";
    private final String[] SHADERS_NAME = {"scene", "controller", "render-model", "distortion"};

    interface Program {

        public static final int SCENE = 0;
        public static final int CONTROLLER_TRANSFORM = 1;
        public static final int RENDER_MODEL = 2;
        public static final int LENS = 3;
        public static final int MAX = 4;
    }

    interface VertexArray {

        public static final int SCENE = 0;
        public static final int LENS = 1;
        public static final int CONTROLLER = 2;
        public static final int MAX = 3;
    }

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int INDEX = 1;
        public static final int MAX = 2;
    }

    private IVRSystem hmd;
    private Vec2i renderSize = new Vec2i();
    private boolean vBlank = false;
    private int vertexCount = 0, indexSize;
    private float nearClip = 0.1f, farClip = 30.0f;
    private IntBuffer buffername = GLBuffers.newDirectIntBuffer(Buffer.MAX);
    private FramebufferDesc leftEyeDesc, rightEyeDesc;
    private Scene scene;

    static FloatBuffer clearColor = GLBuffers.newDirectFloatBuffer(4),
            clearDepth = GLBuffers.newDirectFloatBuffer(new float[]{1.0f});
    static boolean showCubes = true;
    static int[] programName = new int[Program.MAX], matrixLocation = new int[Program.MAX];
    static Mat4[] projection = new Mat4[VR.EVREye.Max], eyePos = new Mat4[VR.EVREye.Max];
    static Mat4 hmdPose = new Mat4();
    static IntBuffer textureName = GLBuffers.newDirectIntBuffer(1),
            vertexArrayName = GLBuffers.newDirectIntBuffer(VertexArray.MAX);
    static IVRCompositor compositor;

    @Override
    public void init(GLAutoDrawable drawable) {

        GL4 gl4 = drawable.getGL().getGL4();

        gl4.setSwapInterval(vBlank ? 1 : 0);

        if (debugOpenGL) {

            gl4.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true);
            gl4.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }

        boolean validated = createAllShaders(gl4);

        if (validated) {
//            validated = setupTextureMaps(gl4);
        }

        setupCameras();

        scene = new Scene(gl4);

        if (validated) {
            validated = setupStereoRenderTargets(gl4);
        }
        if (validated) {
            validated = setupDistortion(gl4);
        }
        if (validated) {
            validated = setupRenderModels(gl4);
        }
        if (!validated) {
//            animator.remove(glWindow);
//            glWindow.destroy();
        }
    }

    private boolean createAllShaders(GL4 gl4) {

        for (int i = 0; i < Program.MAX; i++) {

            ShaderCode vertShader = ShaderCode.create(gl4, GL_VERTEX_SHADER, this.getClass(), SHADERS_ROOT, null,
                    SHADERS_NAME[i], "vert", null, true);
            ShaderCode fragShader = ShaderCode.create(gl4, GL_FRAGMENT_SHADER, this.getClass(), SHADERS_ROOT, null,
                    SHADERS_NAME[i], "frag", null, true);

            ShaderProgram shaderProgram = new ShaderProgram();
            shaderProgram.add(vertShader);
            shaderProgram.add(fragShader);

            shaderProgram.link(gl4, System.out);

            programName[i] = shaderProgram.program();
            matrixLocation[i] = gl4.glGetUniformLocation(programName[i], "matrix");
            if (matrixLocation[i] == -1 && i != Program.LENS) {
                System.err.println("Unable to find matrix uniform in " + SHADERS_NAME[i] + " shader");
                return false;
            }
            vertShader.destroy(gl4);
            fragShader.destroy(gl4);
        }
        return true;
    }

    private boolean setupTextureMaps(GL4 gl4) {

        try {
            jgli.Texture2d texture = new Texture2d(jgli.Load.load(TEXTURE_PATH));
            if (texture.empty()) {
                return false;
            }

//            gl4.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            gl4.glGenTextures(1, textureName);
//            gl4.glActiveTexture(GL_TEXTURE0);
            gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(0));

            jgli.Gl.Format format = jgli.Gl.translate(texture.format());

            for (int level = 0; level < texture.levels(); ++level) {

                gl4.glTexImage2D(GL_TEXTURE_2D, level,
                        format.internal.value,
                        texture.dimensions(level)[0], texture.dimensions(level)[1],
                        0,
                        format.external.value, format.type.value,
                        texture.data(level));
            }

            gl4.glGenerateMipmap(GL_TEXTURE_2D);

//            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
//            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, texture.levels() - 1);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
//            int[] swizzle = {GL_RED, GL_GREEN, GL_BLUE, GL_ALPHA};
//            gl4.glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, swizzle, 0);

            FloatBuffer largest = GLBuffers.newDirectFloatBuffer(1);

            gl4.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, largest);
            gl4.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, largest.get(0));

            gl4.glBindTexture(GL_TEXTURE_2D, 0);

        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    private boolean setupCameras() {

        for (int eye = 0; eye < VR.EVREye.Max; eye++) {
            projection[eye] = getHmdMatrixProjection(eye);
            eyePos[eye] = getHmdMatrixPoseEye(eye);
        }
        return false;
    }

    private Mat4 getHmdMatrixProjection(int eye) {
        HmdMatrix44_t mat = hmd.GetProjectionMatrix.apply(eye, nearClip, farClip, VR.EGraphicsAPIConvention.API_OpenGL);
        return new Mat4(mat.m);
    }

    private Mat4 getHmdMatrixPoseEye(int eye) {
        HmdMatrix34_t mat = hmd.GetEyeToHeadTransform.apply(eye);
        return new Mat4(mat.m[0], mat.m[1], mat.m[2], mat.m[3], mat.m[4], mat.m[5], mat.m[6], mat.m[7],
                mat.m[8], mat.m[9], mat.m[10], mat.m[11], 0, 0, 0, 1);
    }

    private boolean setupStereoRenderTargets(GL4 gl4) {

        IntBuffer width = GLBuffers.newDirectIntBuffer(1), height = GLBuffers.newDirectIntBuffer(1);

        hmd.GetRecommendedRenderTargetSize.apply(width, height);
        renderSize.set(width.get(0), height.get(0));

        leftEyeDesc = FramebufferDesc.create(gl4, renderSize);
        rightEyeDesc = FramebufferDesc.create(gl4, renderSize);

        BufferUtils.destroyDirectBuffer(width);
        BufferUtils.destroyDirectBuffer(height);

        return true;
    }

    private boolean setupDistortion(GL4 gl4) {

        short lensGridSegmentCountH = 43, lensGridSegmentCountV = 43;

        float w = 1.0f / (lensGridSegmentCountH - 1), h = 1.0f / (lensGridSegmentCountV - 1), u = 0, v = 0;

        ArrayList<VertexDataLens> verts = new ArrayList<>();

        //left eye distortion verts
        float xOffset = -1;
        for (int y = 0; y < lensGridSegmentCountV; y++) {

            for (int x = 0; x < lensGridSegmentCountH; x++) {

                u = x * w;
                v = 1 - y * h;

                VertexDataLens vert = new VertexDataLens();

                vert.position = new Vec2(xOffset + u, -1 + 2 * y * h);

                DistortionCoordinates_t dc0 = hmd.ComputeDistortion.apply(VR.EVREye.Eye_Left, u, v);

                vert.texCoordRed = new Vec2(dc0.rfRed[0], 1 - dc0.rfRed[1]);
                vert.texCoordGreen = new Vec2(dc0.rfGreen[0], 1 - dc0.rfGreen[1]);
                vert.texCoordBlue = new Vec2(dc0.rfBlue[0], 1 - dc0.rfBlue[1]);

                verts.add(vert);
            }
        }

        //right eye distortion verts
        xOffset = 0;
        for (int y = 0; y < lensGridSegmentCountV; y++) {

            for (int x = 0; x < lensGridSegmentCountH; x++) {

                u = x * w;
                v = 1 - y * h;

                VertexDataLens vert = new VertexDataLens();

                vert.position = new Vec2(xOffset + u, -1 + 2 * y * h);

                DistortionCoordinates_t dc0 = hmd.ComputeDistortion.apply(VR.EVREye.Eye_Right, u, v);

                vert.texCoordRed = new Vec2(dc0.rfRed[0], 1 - dc0.rfRed[1]);
                vert.texCoordGreen = new Vec2(dc0.rfGreen[0], 1 - dc0.rfGreen[1]);
                vert.texCoordBlue = new Vec2(dc0.rfBlue[0], 1 - dc0.rfBlue[1]);

                verts.add(vert);
            }
        }

        ArrayList<Short> indices = new ArrayList<>();
        short a, b, c, d, offset = 0;

        for (short y = 0; y < lensGridSegmentCountV - 1; y++) {

            for (short x = 0; x < lensGridSegmentCountH - 1; x++) {

                a = (short) (lensGridSegmentCountH * y + x + offset);
                b = (short) (lensGridSegmentCountH * y + x + 1 + offset);
                c = (short) ((y + 1) * lensGridSegmentCountH + x + 1 + offset);
                d = (short) ((y + 1) * lensGridSegmentCountH + x + offset);

                indices.add(a);
                indices.add(b);
                indices.add(c);

                indices.add(a);
                indices.add(c);
                indices.add(d);
            }
        }

        offset = (short) (lensGridSegmentCountH * lensGridSegmentCountV);
        for (short y = 0; y < lensGridSegmentCountV - 1; y++) {

            for (short x = 0; x < lensGridSegmentCountH - 1; x++) {

                a = (short) (lensGridSegmentCountH * y + x + offset);
                b = (short) (lensGridSegmentCountH * y + x + 1 + offset);
                c = (short) ((y + 1) * lensGridSegmentCountH + x + 1 + offset);
                d = (short) ((y + 1) * lensGridSegmentCountH + x + offset);

                indices.add(a);
                indices.add(b);
                indices.add(c);

                indices.add(a);
                indices.add(c);
                indices.add(d);
            }
        }
        indexSize = indices.size();

        ByteBuffer vertexBuffer = GLBuffers.newDirectByteBuffer(verts.size() * VertexDataLens.SIZE);
        for (int i = 0; i < verts.size(); i++) {
            verts.get(i).toDbb(vertexBuffer, i * VertexDataLens.SIZE);
        }
        ByteBuffer indexBuffer = GLBuffers.newDirectByteBuffer(indices.size() * Short.BYTES);
        for (int i = 0; i < indices.size(); i++) {
            indexBuffer.putShort(i * Short.BYTES, indices.get(i));
        }

        gl4.glGenVertexArrays(VertexArray.MAX, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(VertexArray.LENS));

        gl4.glGenBuffers(Buffer.MAX, buffername);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, buffername.get(Buffer.VERTEX));
        gl4.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, GL_STATIC_DRAW);

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffername.get(Buffer.INDEX));
        gl4.glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer.capacity(), indexBuffer, GL_STATIC_DRAW);

        gl4.glEnableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glVertexAttribPointer(Semantic.Attr.POSITION, 2, GL_FLOAT, false, VertexDataLens.SIZE,
                VertexDataLens.OFFSET_POSITION);

        gl4.glEnableVertexAttribArray(Semantic.Attr.UV_RED);
        gl4.glVertexAttribPointer(Semantic.Attr.UV_RED, 2, GL_FLOAT, false, VertexDataLens.SIZE,
                VertexDataLens.OFFSET_TEX_COORD_RED);

        gl4.glEnableVertexAttribArray(Semantic.Attr.UV_GREEN);
        gl4.glVertexAttribPointer(Semantic.Attr.UV_GREEN, 2, GL_FLOAT, false, VertexDataLens.SIZE,
                VertexDataLens.OFFSET_TEX_COORD_GREEN);

        gl4.glEnableVertexAttribArray(Semantic.Attr.UV_BLUE);
        gl4.glVertexAttribPointer(Semantic.Attr.UV_BLUE, 2, GL_FLOAT, false, VertexDataLens.SIZE,
                VertexDataLens.OFFSET_TEX_COORD_BLUE);

        gl4.glBindVertexArray(0);

        gl4.glDisableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glDisableVertexAttribArray(Semantic.Attr.UV_RED);
        gl4.glDisableVertexAttribArray(Semantic.Attr.UV_GREEN);
        gl4.glDisableVertexAttribArray(Semantic.Attr.UV_BLUE);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        BufferUtils.destroyDirectBuffer(vertexBuffer);
        BufferUtils.destroyDirectBuffer(indexBuffer);

        return true;
    }

    private boolean setupRenderModels(GL4 gl4) {
        return true;
    }

    @Override
    public void display(GLAutoDrawable drawable) {

        GL4 gl4 = drawable.getGL().getGL4();

        renderStereoTargets(gl4);

        Texture_t leftEyeTexture = new Texture_t(
                leftEyeDesc.textureName.get(FramebufferDesc.Target.RESOLVE), 
                VR.EGraphicsAPIConvention.API_OpenGL, 
                VR.EColorSpace.ColorSpace_Gamma);
        compositor.Submit.apply(VR.EVREye.Eye_Left, leftEyeTexture, null, VR.EVRSubmitFlags.Submit_Default);
        Texture_t rightEyeTexture = new Texture_t(
                rightEyeDesc.textureName.get(FramebufferDesc.Target.RESOLVE), 
                VR.EGraphicsAPIConvention.API_OpenGL, 
                VR.EColorSpace.ColorSpace_Gamma);
        compositor.Submit.apply(VR.EVREye.Eye_Right, rightEyeTexture, null, VR.EVRSubmitFlags.Submit_Default);
        
        gl4.glFinish();
        drawable.swapBuffers();
        
        clearColor.put(0, 0.15f).put(1, 0.15f).put(2, 0.18f).put(3, 1.0f);
        gl4.glClearBufferfv(GL2ES3.GL_COLOR, 0, clearColor);
        
        gl4.glFlush();
        gl4.glFinish();
    }

    private void renderStereoTargets(GL4 gl4) {

        clearColor.put(0, 0.15f).put(1, 0.15f).put(2, 0.18f).put(3, 1.0f);  // nice background color, but not black
        gl4.glEnable(GL_MULTISAMPLE);

        // Left Eye
        gl4.glBindFramebuffer(GL_FRAMEBUFFER, leftEyeDesc.framebufferName.get(FramebufferDesc.Target.RENDER));
        gl4.glViewport(0, 0, renderSize.x, renderSize.y);
        scene.render(gl4, VR.EVREye.Eye_Left);
        gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);

        gl4.glDisable(GL_MULTISAMPLE);

        gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, leftEyeDesc.framebufferName.get(FramebufferDesc.Target.RENDER));
        gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, leftEyeDesc.framebufferName.get(FramebufferDesc.Target.RESOLVE));

        gl4.glBlitFramebuffer(0, 0, renderSize.x, renderSize.y, 0, 0, renderSize.x, renderSize.y,
                GL_COLOR_BUFFER_BIT,
                GL_LINEAR);

        gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);

        gl4.glEnable(GL_MULTISAMPLE);

        // Right Eye
        gl4.glBindFramebuffer(GL_FRAMEBUFFER, rightEyeDesc.framebufferName.get(FramebufferDesc.Target.RENDER));
        gl4.glViewport(0, 0, renderSize.x, renderSize.y);
        scene.render(gl4, VR.EVREye.Eye_Right);
        gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);

        gl4.glDisable(GL_MULTISAMPLE);

        gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, rightEyeDesc.framebufferName.get(FramebufferDesc.Target.RENDER));
        gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, rightEyeDesc.framebufferName.get(FramebufferDesc.Target.RESOLVE));

        gl4.glBlitFramebuffer(0, 0, renderSize.x, renderSize.y, 0, 0, renderSize.x, renderSize.y,
                GL_COLOR_BUFFER_BIT,
                GL_LINEAR);

        gl4.glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        gl4.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        System.exit(0);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
                animator.remove(glWindow);
                glWindow.destroy();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
