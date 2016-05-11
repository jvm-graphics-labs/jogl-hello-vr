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
import static com.jogamp.opengl.GL.GL_DONT_CARE;
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
import glm.vec._2.i.Vec2i;
import glm.vec._3.i.Vec3i;
import glutil.GlDebugOutput;
import java.nio.IntBuffer;
import jopenvr.VR;

/**
 *
 * @author GBarbieri
 */
public class MainApplication implements GLEventListener {

    public static GLWindow glWindow;
    public static Animator animator;
    private final String SHADERS_ROOT = "src/helloVr/shaders";
    private final String[] SHADERS_NAME = {"scene"};

    public static void main(String[] args) {

        // Loading the SteamVR Runtime
        IntBuffer error = GLBuffers.newDirectIntBuffer(new int[]{VR.EVRInitError.VRInitError_None});
        VR.VR_InitInternal(error, VR.EVRApplicationType.VRApplication_Scene);

        if (error.get(0) != VR.EVRInitError.VRInitError_None) {

            String s = "Unable to init VR runtime: " + VR.VR_GetVRInitErrorAsEnglishDescription(error.get(0));
            throw new Error("VR_Init Failed, " + s);
        }

        MainApplication mainApplication = new MainApplication();

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

    private Vec2i windowSize = new Vec2i(1280, 720);
    private boolean vBlank = false, debugOpenGL = false;
    private int sceneVolumeInit = 20, vertexCount = 0;
    private Vec3i sceneVolume = new Vec3i(sceneVolumeInit);
    private float scale = 0.3f, scaleSpacing = 4.0f, nearClip = 0.1f, farClip = 30.0f;
    private int[] programName = new int[Program.MAX], matrixLocation = new int[Program.MAX];

    @Override
    public void init(GLAutoDrawable drawable) {

        GL4 gl4 = drawable.getGL().getGL4();

        if (debugOpenGL) {

            gl4.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, null, true);
            gl4.glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
        }

        if(!createAllShaders(gl4))
            
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
            if(matrixLocation[i] == -1) {
                throw new Error
            }

            vertShader.destroy(gl4);
            fragShader.destroy(gl4);
        }
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
