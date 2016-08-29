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

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int INDEX = 1;
        public static final int MAX = 2;
    }

    private int indexSize;
    private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(1),
            bufferName = GLBuffers.newDirectIntBuffer(Buffer.MAX);

    public void setup(GL4 gl4, IVRSystem hmd) {

        short lensGridSegmentCountH = 43;
        short lensGridSegmentCountV = 43;

        float w = 1.0f / (lensGridSegmentCountH - 1);
        float h = 1.0f / (lensGridSegmentCountV - 1);

        float u = 0, v = 0;

        List<VertexDataLens> verts = new ArrayList<>();

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

        ByteBuffer indexBuffer = GLBuffers.newDirectByteBuffer(indices.size() * Short.BYTES);
        IntStreamEx.range(indices.size()).forEach(i -> indexBuffer.putShort(i * Short.BYTES, indices.get(i)));

        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glGenBuffers(Buffer.MAX, bufferName);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX));
        gl4.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, GL_STATIC_DRAW);

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName.get(Buffer.INDEX));
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
    }

    public void render(GL4 gl4, Application app) {

        gl4.glDisable(GL_DEPTH_TEST);
        gl4.glViewport(0, 0, app.windowSize.x, app.windowSize.y);

        gl4.glBindVertexArray(vertexArrayName.get(0));
        gl4.glUseProgram(app.program[Application.Program.LENS].name);

        for (int eye = 0; eye < VR.EVREye.Max; eye++) {

            gl4.glBindTexture(GL_TEXTURE_2D, app.eyeDesc[eye].textureName.get(FramebufferDesc.Target.RESOLVE));
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            // left lens (first half of index array ), right lens (second half of index array )
            gl4.glDrawElements(GL_TRIANGLES, indexSize / 2, GL_UNSIGNED_SHORT,
                    eye == VR.EVREye.Eye_Left ? 0 : indexSize * Short.BYTES);
        }
        gl4.glBindVertexArray(0);
        gl4.glUseProgram(0);
    }
}
