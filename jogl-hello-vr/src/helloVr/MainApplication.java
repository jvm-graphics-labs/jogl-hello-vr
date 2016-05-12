/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.opengl.GLWindow;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_DONT_CARE;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_LINEAR_MIPMAP_LINEAR;
import static com.jogamp.opengl.GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_OUTPUT_SYNCHRONOUS;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;
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
import glm.vec._2.i.Vec2i;
import glm.vec._3.i.Vec3i;
import glutil.GlDebugOutput;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jgli.Texture2d;
import jopenvr.IVRSystem;
import jopenvr.VR;
import jopenvr.VR_IVRSystem_FnTable;

/**
 *
 * @author GBarbieri
 */
public class MainApplication implements GLEventListener {

    public static GLWindow glWindow;
    public static Animator animator;
    private final String TEXTURE_PATH = "/asset/cube_texture.png";
    private final String SHADERS_ROOT = "/helloVr/shaders";
    private final String[] SHADERS_NAME = {"scene", "controller", "render-model", "distortion"};

    public static void main(String[] args) {

        
        MainApplication mainApplication = new MainApplication();
        
        // Loading the SteamVR Runtime
        IntBuffer error = GLBuffers.newDirectIntBuffer(new int[]{VR.EVRInitError.VRInitError_None});
        mainApplication.hmd = VR.VR_Init(error, VR.EVRApplicationType.VRApplication_Scene);
//        VR.VR_InitInternal(error, VR.EVRApplicationType.VRApplication_Scene);

        if (error.get(0) != VR.EVRInitError.VRInitError_None) {

            String s = "Unable to init VR runtime: " + VR.VR_GetVRInitErrorAsEnglishDescription(error.get(0));
            throw new Error("VR_Init Failed, " + s);
        }


        Display display = NewtFactory.createDisplay(null);
        Screen screen = NewtFactory.createScreen(display, 0);
        GLProfile glProfile = GLProfile.get(GLProfile.GL4);
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glWindow = GLWindow.create(screen, glCapabilities);

        glWindow.setSize(mainApplication.windowSize.x, mainApplication.windowSize.y);
        glWindow.setPosition(50, 50);
        glWindow.setUndecorated(false);
        glWindow.setAlwaysOnTop(false);
        glWindow.setFullscreen(false);
        glWindow.setPointerVisible(true);
        glWindow.confinePointer(false);
        glWindow.setTitle("Hello VR");

        if (mainApplication.debugOpenGL) {
            glWindow.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);
        }
        glWindow.setVisible(true);
        if (mainApplication.debugOpenGL) {
            glWindow.getContext().addGLDebugListener(new GlDebugOutput());
        }

        glWindow.addGLEventListener(mainApplication);
//        glWindow.addKeyListener(mainApplication);

        glWindow.getContext().setSwapInterval(mainApplication.vBlank ? 1 : 0);

        animator = new Animator(glWindow);
        animator.start();
    }

    private interface Program {

        public static final int SCENE = 0;
        public static final int LENS = 1;
        public static final int CONTROLLER_TRANSFORM = 2;
        public static final int RENDER_MODEL = 3;
        public static final int MAX = 4;
    }

    private IVRSystem hmd;
    private Vec2i windowSize = new Vec2i(1280, 720);
    private boolean vBlank = false, debugOpenGL = false;
    private int sceneVolumeInit = 20, vertexCount = 0;
    private Vec3i sceneVolume = new Vec3i(sceneVolumeInit);
    private float scale = 0.3f, scaleSpacing = 4.0f, nearClip = 0.1f, farClip = 30.0f;
    private int[] programName = new int[Program.MAX], matrixLocation = new int[Program.MAX];
    private IntBuffer textureName = GLBuffers.newDirectIntBuffer(1);
    private Mat4[] projection = new Mat4[VR.EVREye.Max], eyePos = new Mat4[VR.EVREye.Max];

    @Override
    public void init(GLAutoDrawable drawable) {

        GL4 gl4 = drawable.getGL().getGL4();

        if (debugOpenGL) {

            gl4.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true);
            gl4.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }

        boolean validated = createAllShaders(gl4);
        if (validated) {
            validated = setupTextureMaps(gl4);
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
            Logger.getLogger(MainApplication.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    private boolean setupCameras(GL4 gl4) {
        
        for (int eye = 0; eye < VR.EVREye.Max; eye++) {
            
        }
    }
    
    private Mat4 getHmdMatrixProjection(int eye) {
        
    }
    
    @Override
    public void dispose(GLAutoDrawable drawable) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
