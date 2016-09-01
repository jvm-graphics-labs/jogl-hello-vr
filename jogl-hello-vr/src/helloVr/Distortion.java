/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_LINEAR_MIPMAP_LINEAR;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_S;
import static com.jogamp.opengl.GL.GL_TEXTURE_WRAP_T;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import glm.vec._2.Vec2;
import glutil.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import vr.DistortionCoordinates_t;
import vr.IVRSystem;
import vr.VR;
import one.util.streamex.IntStreamEx;

/**
 *
 * @author GBarbieri
 */
public class Distortion {

    private final String SHADERS_SRC = "distortion";

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int ELEMENT = 1;
        public static final int MAX = 2;
    }

    private int indexSize;

    private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(1),
            bufferName = GLBuffers.newDirectIntBuffer(Buffer.MAX);

    private Program program;

    public Distortion(GL4 gl4, IVRSystem hmd) {

        initBuffers(gl4, hmd);

        initVertexArray(gl4);

        program = new Program(gl4);
    }

    private void initBuffers(GL4 gl4, IVRSystem hmd) {

        short lensGridSegmentCountH = 43;
        short lensGridSegmentCountV = 43;

        float w = 1.0f / (lensGridSegmentCountH - 1);
        float h = 1.0f / (lensGridSegmentCountV - 1);

        float u, v;

        List<VertexDataLens> verts = new ArrayList<>();

        //left eye distortion verts
        float xOffset = -1;
        for (int y = 0; y < lensGridSegmentCountV; y++) {

            for (int x = 0; x < lensGridSegmentCountH; x++) {

                u = x * w;
                v = 1 - y * h;

                VertexDataLens vert = new VertexDataLens();

                vert.position = new Vec2(xOffset + u, -1 + 2 * y * h);

                DistortionCoordinates_t dc0 = hmd.ComputeDistortion.apply(VR.EVREye.EYE_Left, u, v);

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

        List<Short> indices = new ArrayList<>();
        short a, b, c, d;
        short offset = 0;

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
        IntStreamEx.range(verts.size()).forEach(i -> verts.get(i).toDbb(vertexBuffer, i * VertexDataLens.SIZE));

        ByteBuffer elementBuffer = GLBuffers.newDirectByteBuffer(indices.size() * Short.BYTES);
        IntStreamEx.range(indices.size()).forEach(i -> elementBuffer.putShort(i * Short.BYTES, indices.get(i)));

        gl4.glGenBuffers(Buffer.MAX, bufferName);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX));
        gl4.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, GL_STATIC_DRAW);

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName.get(Buffer.ELEMENT));
        gl4.glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer.capacity(), elementBuffer, GL_STATIC_DRAW);

        BufferUtils.destroyDirectBuffer(vertexBuffer);
        BufferUtils.destroyDirectBuffer(elementBuffer);
    }

    private void initVertexArray(GL4 gl4) {

        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glBindBuffer(GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX));

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

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName.get(Buffer.ELEMENT));

        gl4.glBindVertexArray(0);
    }

    public void render(GL4 gl4, Application app) {

        gl4.glDisable(GL_DEPTH_TEST);
        gl4.glViewport(0, 0, app.windowSize.x, app.windowSize.y);

        gl4.glBindVertexArray(vertexArrayName.get(0));
        gl4.glUseProgram(program.name);

        for (int eye = 0; eye < VR.EVREye.Max; eye++) {

            gl4.glBindTexture(GL_TEXTURE_2D, app.eyeDesc[eye].textureName.get(FramebufferDesc.Target.RESOLVE));
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            // left lens (first half of index array ), right lens (second half of index array )
            gl4.glDrawElements(GL_TRIANGLES, indexSize / 2, GL_UNSIGNED_SHORT,
                    eye == VR.EVREye.EYE_Left ? 0 : indexSize); // indexSize / 2 * Short.Bytes = indexSize
        }
        gl4.glBindVertexArray(0);
        gl4.glUseProgram(0);
    }

    public void dispose(GL4 gl4) {
        
        gl4.glDeleteProgram(program.name);
        gl4.glDeleteVertexArrays(1, vertexArrayName);
        gl4.glDeleteBuffers(Buffer.MAX, bufferName);
        
        BufferUtils.destroyDirectBuffer(vertexArrayName);
        BufferUtils.destroyDirectBuffer(bufferName);
    }
    
    private class VertexDataLens {

        public static final int SIZE = 4 * Vec2.SIZE;
        public static final int OFFSET_POSITION = 0;
        public static final int OFFSET_TEX_COORD_RED = 1 * Vec2.SIZE;
        public static final int OFFSET_TEX_COORD_GREEN = 2 * Vec2.SIZE;
        public static final int OFFSET_TEX_COORD_BLUE = 3 * Vec2.SIZE;

        public Vec2 position;
        public Vec2 texCoordRed;
        public Vec2 texCoordGreen;
        public Vec2 texCoordBlue;

        public VertexDataLens() {
        }

        public void toDbb(ByteBuffer bb, int index) {

            position.toDbb(bb, index + OFFSET_POSITION);
            texCoordRed.toDbb(bb, index + OFFSET_TEX_COORD_RED);
            texCoordGreen.toDbb(bb, index + OFFSET_TEX_COORD_GREEN);
            texCoordBlue.toDbb(bb, index + OFFSET_TEX_COORD_BLUE);
        }
    }

    private class Program extends glsl.Program {

        public Program(GL4 gl4) {

            super(gl4, Application.SHADERS_ROOT, SHADERS_SRC);

            gl4.glUseProgram(name);
            gl4.glUniform1i(
                    gl4.glGetUniformLocation(name, "myTexture"),
                    Semantic.Sampler.MY_TEXTURE);
            gl4.glUseProgram(0);
        }
    }
}
