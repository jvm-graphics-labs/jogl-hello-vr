/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import static com.jogamp.opengl.GL2ES3.GL_DEPTH;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import glm.mat._4.Mat4;
import glm.vec._3.Vec3;
import glm.vec._3.i.Vec3i;
import glm.vec._4.Vec4;
import glutil.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

/**
 *
 * @author GBarbieri
 */
public class Scene {

    private float scaleSpacing = 4.0f, scale = 0.3f;
    private int sceneVolumeInit = 20, vertexCount;
    private Vec3i sceneVolume = new Vec3i(sceneVolumeInit);
    private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(1),
            vertexBufferName = GLBuffers.newDirectIntBuffer(1);
    private Mat4 mvp = new Mat4();
    private FloatBuffer matBuffer = GLBuffers.newDirectFloatBuffer(16);

    public Scene(GL4 gl4) {

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
        for (int i = 0; i < vertexDataArray.size(); i++) {
            vertexBuffer.putFloat(i * Float.BYTES, vertexDataArray.get(i));
        }

        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glGenBuffers(1, vertexBufferName);
        gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));
        gl4.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, GL_STATIC_DRAW);

        //int stride = VertexDataLens.SIZE, offset = 0;
        int stride = 5 * Float.BYTES, offset = 0; 
        
        gl4.glEnableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, stride, offset);

        offset += Vec3.SIZE;
        gl4.glEnableVertexAttribArray(Semantic.Attr.TERX_COORD);
        gl4.glVertexAttribPointer(Semantic.Attr.TERX_COORD, 2, GL_FLOAT, false, stride, offset);

        gl4.glBindVertexArray(0);

        BufferUtils.destroyDirectBuffer(vertexBuffer);
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

    void render(GL4 gl4, int eye) {

        gl4.glClearBufferfv(GL_COLOR, 0, Application.clearColor);
        gl4.glClearBufferfv(GL_DEPTH, 0, Application.clearDepth);
        gl4.glEnable(GL_DEPTH_TEST);

        if (Application.showCubes) {

            gl4.glUseProgram(Application.programName[Application.Program.SCENE]);
            gl4.glUniformMatrix4fv(Application.matrixLocation[Application.Program.SCENE], 1, false, getCurrentViewProjectionMatrix(eye));
            gl4.glBindVertexArray(Application.vertexArrayName.get(Application.VertexArray.SCENE));
            gl4.glBindTexture(GL_TEXTURE_2D, Application.textureName.get(0));
            
            gl4.glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            int error = gl4.glGetError();
            if (error != GL4.GL_NO_ERROR) {
            	//msg GL_INVALID_OPERATION error generated. Array object is not active.
                System.out.println("GL error " + error);
            }
            
            
            gl4.glBindVertexArray(0);
        }
    }

    private FloatBuffer getCurrentViewProjectionMatrix(int eye) {
            
        return Application.projection[eye]
                .mul(Application.eyePos[eye], mvp)
                .mul(Application.hmdPose)
                .toDfb(matBuffer);        
    }
}
