/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_LINEAR_MIPMAP_LINEAR;
import static com.jogamp.opengl.GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import static com.jogamp.opengl.GL2ES3.GL_DEPTH;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import glm.mat._4.Mat4;
import glm.vec._2.Vec2;
import glm.vec._3.Vec3;
import glm.vec._3.i.Vec3i;
import glm.vec._4.Vec4;
import glutil.BufferUtils;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import one.util.streamex.IntStreamEx;

/**
 *
 * @author GBarbieri
 */
public class Scene {

    private final String TEXTURE_PATH = "src/helloVr/asset/cube_texture.png", SHADERS_SRC = "scene";

    private ProgramScene program;

    private int vertexCount;

    private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(1),
            vertexBufferName = GLBuffers.newDirectIntBuffer(1), textureName = GLBuffers.newDirectIntBuffer(1);

    private Mat4 mvp = new Mat4();

    public Scene(GL4 gl4) {

        program = new ProgramScene(gl4, Application.SHADERS_ROOT, SHADERS_SRC);

        initTextureMaps(gl4);

        initBuffer(gl4);

        initVertexArray(gl4);
    }

    private boolean initTextureMaps(GL4 gl4) {

        try {
            BufferedImage bufferedImage = ImageIO.read(new File(TEXTURE_PATH));
            byte[] dataBytes = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
            ByteBuffer bufferedImageBuffer = GLBuffers.newDirectByteBuffer(dataBytes);

            gl4.glGenTextures(1, textureName);
            gl4.glActiveTexture(GL_TEXTURE0);
            gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(0));

            gl4.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, bufferedImage.getWidth(), bufferedImage.getHeight(), 0,
                    GL_RGB, GL_UNSIGNED_BYTE, bufferedImageBuffer);

            gl4.glGenerateMipmap(GL_TEXTURE_2D);

            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            FloatBuffer maxAnis = GLBuffers.newDirectFloatBuffer(1);
            gl4.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, maxAnis);
            gl4.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAnis.get(0));

            gl4.glBindTexture(GL_TEXTURE_2D, 0);

            BufferUtils.destroyDirectBuffer(maxAnis);
            BufferUtils.destroyDirectBuffer(bufferedImageBuffer);

        } catch (IOException ex) {
            Logger.getLogger(Application.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }

    private void initBuffer(GL4 gl4) {

        float scaleSpacing = 4.0f, scale = 0.3f;
        int sceneVolumeInit = 20;
        Vec3i sceneVolume = new Vec3i(sceneVolumeInit);

        ArrayList<Float> vertexDataArray = new ArrayList<>();

        Mat4 matScale = new Mat4().scale(scale);
        Mat4 matTransform = new Mat4().translate(
                -(sceneVolume.x * scaleSpacing) / 2.f,
                -(sceneVolume.y * scaleSpacing) / 2.f,
                -(sceneVolume.z * scaleSpacing) / 2.f);

        Mat4 mat = matScale.mul(matTransform);

        for (int z = 0; z < sceneVolume.z; z++) {

            for (int y = 0; y < sceneVolume.y; y++) {

                for (int x = 0; x < sceneVolume.x; x++) {

                    addCubeToScene(mat, vertexDataArray);
                    mat.translate(scaleSpacing, 0, 0);
                }
                mat.translate(-sceneVolume.x * scaleSpacing, scaleSpacing, 0);
            }
            mat.translate(0, -sceneVolume.y * scaleSpacing, scaleSpacing);
        }
        vertexCount = vertexDataArray.size() / 5;

        ByteBuffer vertexBuffer = GLBuffers.newDirectByteBuffer(vertexDataArray.size() * Float.BYTES);
        IntStreamEx.range(vertexDataArray.size()).forEach(i
                -> vertexBuffer.putFloat(i * Float.BYTES, vertexDataArray.get(i)));

        gl4.glGenBuffers(1, vertexBufferName);
        gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));
        gl4.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, GL_STATIC_DRAW);

        BufferUtils.destroyDirectBuffer(vertexBuffer);
    }

    private void initVertexArray(GL4 gl4) {

        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));

        int stride = VertexDataScene.SIZE, offset = 0;

        gl4.glEnableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, stride, offset);

        offset += Vec3.SIZE;
        gl4.glEnableVertexAttribArray(Semantic.Attr.TERX_COORD);
        gl4.glVertexAttribPointer(Semantic.Attr.TERX_COORD, 2, GL_FLOAT, false, stride, offset);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);

        gl4.glBindVertexArray(0);
    }

    private void addCubeToScene(Mat4 mat, ArrayList<Float> vertexDataArray) {

        Vec4 a = mat.mul(new Vec4(0, 0, 0, 1));
        Vec4 b = mat.mul(new Vec4(1, 0, 0, 1));
        Vec4 c = mat.mul(new Vec4(1, 1, 0, 1));
        Vec4 d = mat.mul(new Vec4(0, 1, 0, 1));
        Vec4 e = mat.mul(new Vec4(0, 0, 1, 1));
        Vec4 f = mat.mul(new Vec4(1, 0, 1, 1));
        Vec4 g = mat.mul(new Vec4(1, 1, 1, 1));
        Vec4 h = mat.mul(new Vec4(0, 1, 1, 1));

        // triangles instead of quads
        addCubeVertex(e.x, e.y, e.z, 0, 1, vertexDataArray); // Front
        addCubeVertex(f.x, f.y, f.z, 1, 1, vertexDataArray);
        addCubeVertex(g.x, g.y, g.z, 1, 0, vertexDataArray);
        addCubeVertex(g.x, g.y, g.z, 1, 0, vertexDataArray);
        addCubeVertex(h.x, h.y, h.z, 0, 0, vertexDataArray);
        addCubeVertex(e.x, e.y, e.z, 0, 1, vertexDataArray);

        addCubeVertex(b.x, b.y, b.z, 0, 1, vertexDataArray); // Back
        addCubeVertex(a.x, a.y, a.z, 1, 1, vertexDataArray);
        addCubeVertex(d.x, d.y, d.z, 1, 0, vertexDataArray);
        addCubeVertex(d.x, d.y, d.z, 1, 0, vertexDataArray);
        addCubeVertex(c.x, c.y, c.z, 0, 0, vertexDataArray);
        addCubeVertex(b.x, b.y, b.z, 0, 1, vertexDataArray);

        addCubeVertex(h.x, h.y, h.z, 0, 1, vertexDataArray); // Top
        addCubeVertex(g.x, g.y, g.z, 1, 1, vertexDataArray);
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertexDataArray);
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertexDataArray);
        addCubeVertex(d.x, d.y, d.z, 0, 0, vertexDataArray);
        addCubeVertex(h.x, h.y, h.z, 0, 1, vertexDataArray);

        addCubeVertex(a.x, a.y, a.z, 0, 1, vertexDataArray); // Bottom
        addCubeVertex(b.x, b.y, b.z, 1, 1, vertexDataArray);
        addCubeVertex(f.x, f.y, f.z, 1, 0, vertexDataArray);
        addCubeVertex(f.x, f.y, f.z, 1, 0, vertexDataArray);
        addCubeVertex(e.x, e.y, e.z, 0, 0, vertexDataArray);
        addCubeVertex(a.x, a.y, a.z, 0, 1, vertexDataArray);

        addCubeVertex(a.x, a.y, a.z, 0, 1, vertexDataArray); // Left
        addCubeVertex(e.x, e.y, e.z, 1, 1, vertexDataArray);
        addCubeVertex(h.x, h.y, h.z, 1, 0, vertexDataArray);
        addCubeVertex(h.x, h.y, h.z, 1, 0, vertexDataArray);
        addCubeVertex(d.x, d.y, d.z, 0, 0, vertexDataArray);
        addCubeVertex(a.x, a.y, a.z, 0, 1, vertexDataArray);

        addCubeVertex(f.x, f.y, f.z, 0, 1, vertexDataArray); // Right
        addCubeVertex(b.x, b.y, b.z, 1, 1, vertexDataArray);
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertexDataArray);
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertexDataArray);
        addCubeVertex(g.x, g.y, g.z, 0, 0, vertexDataArray);
        addCubeVertex(f.x, f.y, f.z, 0, 1, vertexDataArray);
    }

    private void addCubeVertex(float fl0, float fl1, float fl2, float fl3, float fl4, ArrayList<Float> vertexData) {
        vertexData.add(fl0);
        vertexData.add(fl1);
        vertexData.add(fl2);
        vertexData.add(fl3);
        vertexData.add(fl4);
    }

    public void render(GL4 gl4, Application app, int eye) {

        gl4.glClearBufferfv(GL_COLOR, 0, app.clearColor);
        gl4.glClearBufferfv(GL_DEPTH, 0, app.clearDepth);
        gl4.glEnable(GL_DEPTH_TEST);

        if (app.showCubes) {

            gl4.glUseProgram(program.name);

            gl4.glUniformMatrix4fv(program.matrixUL(), 1, false, getCurrentViewProjectionMatrix(app, eye));

            gl4.glBindVertexArray(vertexArrayName.get(0));

            gl4.glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.MY_TEXTURE);
            gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(0));

            gl4.glDrawArrays(GL_TRIANGLES, 0, vertexCount);

            gl4.glBindVertexArray(0);
        }

        boolean isInputCapturedByAnotherProcess = app.hmd.IsInputFocusCapturedByAnotherProcess.apply() == 1;

        if (!isInputCapturedByAnotherProcess) {

        }

        gl4.glUseProgram(0);
    }

    private FloatBuffer getCurrentViewProjectionMatrix(Application app, int eye) {
        return app.projection[eye].mul(app.eyePos[eye], mvp).mul(app.hmdPose).toDfb(app.matBuffer);
    }

    private class VertexDataScene {

        public static final int SIZE = Vec3.SIZE + Vec2.SIZE;
        public static final int OFFSET_POSITION = 0;
        public static final int OFFSET_TEX_COORD = Vec3.SIZE;

        public Vec3 position;
        public Vec2 texCoord;

        public VertexDataScene(Vec3 position, Vec2 texCoord) {
            this.position = position;
            this.texCoord = texCoord;
        }

        public void toDbb(ByteBuffer bb, int index) {

            position.toDbb(bb, index + OFFSET_POSITION);
            texCoord.toDbb(bb, index + OFFSET_TEX_COORD);
        }
    }
}
