/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_CLAMP_TO_EDGE;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_LINEAR_MIPMAP_LINEAR;
import static com.jogamp.opengl.GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static com.jogamp.opengl.GL.GL_RGBA;
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
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import glutil.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import one.util.streamex.IntStreamEx;
import vr.RenderModel_TextureMap_t;
import vr.RenderModel_Vertex_t;
import vr.RenderModel_t;

/**
 *
 * @author GBarbieri
 */
public class Model {

    public String name;
    private int vertexCount;

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int ELEMENT = 1;
        public static final int MAX = 2;
    }

    private IntBuffer bufferName = GLBuffers.newDirectIntBuffer(Buffer.MAX),
            vertexArrayName = GLBuffers.newDirectIntBuffer(1), textureName = GLBuffers.newDirectIntBuffer(1);

    public Model(String name) {
        this.name = name;
    }

    /**
     * Allocates and populates the GL resources for a render model.
     *
     * @param gl4
     * @param model
     * @param textureReference
     * @return
     */
    public boolean init(GL4 gl4, RenderModel_t model, RenderModel_TextureMap_t textureReference) {

        initBuffers(gl4, model);

        initVertexArray(gl4);

        initTexture(gl4, textureReference);

        vertexCount = model.unTriangleCount * 3;
        
        return true;
    }

    private void initBuffers(GL4 gl4, RenderModel_t model) {

        gl4.glGenBuffers(Buffer.MAX, bufferName);

        // Populate a vertex buffer	
        ByteBuffer vertexBuffer = GLBuffers.newDirectByteBuffer(vr.RenderModel_Vertex_t.SIZE * model.unVertexCount);
        RenderModel_Vertex_t[] vertices = (RenderModel_Vertex_t[]) model.rVertexData.toArray(model.unVertexCount);

        IntStreamEx.range(model.unVertexCount).forEach(i -> vertices[i].toDbb(vertexBuffer, i * RenderModel_Vertex_t.SIZE));

        gl4.glBindBuffer(GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX));
        gl4.glBufferData(GL_ARRAY_BUFFER, vr.RenderModel_Vertex_t.SIZE * model.unVertexCount, vertexBuffer, GL_STATIC_DRAW);
        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);

        BufferUtils.destroyDirectBuffer(vertexBuffer);

        // Create and populate the index buffer
        ByteBuffer elementBuffer = GLBuffers.newDirectByteBuffer(Short.BYTES * model.unTriangleCount * 3);
        short[] elements = model.rIndexData.getPointer().getShortArray(0, model.unTriangleCount * 3);

        IntStreamEx.range(elements.length).forEach(i -> elementBuffer.putShort(i * Short.BYTES, elements[i]));

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName.get(Buffer.ELEMENT));
        gl4.glBufferData(GL_ELEMENT_ARRAY_BUFFER, Short.BYTES * model.unTriangleCount * 3, elementBuffer, GL_STATIC_DRAW);
        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        BufferUtils.destroyDirectBuffer(elementBuffer);
    }

    private void initVertexArray(GL4 gl4) {

        // create and bind a VAO to hold state for this model
        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glBindBuffer(GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX));

        // Identify the components in the vertex buffer
        gl4.glEnableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, RenderModel_Vertex_t.SIZE,
                RenderModel_Vertex_t.OFFSET_POSITION);
        gl4.glEnableVertexAttribArray(Semantic.Attr.NORMAL);
        gl4.glVertexAttribPointer(Semantic.Attr.NORMAL, 3, GL_FLOAT, false, RenderModel_Vertex_t.SIZE,
                RenderModel_Vertex_t.OFFSET_NORMAL);
        gl4.glEnableVertexAttribArray(Semantic.Attr.TEXT_COORD);
        gl4.glVertexAttribPointer(Semantic.Attr.TEXT_COORD, 2, GL_FLOAT, false, RenderModel_Vertex_t.SIZE,
                RenderModel_Vertex_t.OFFSET_TEXT_COORD);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);

        gl4.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName.get(Buffer.ELEMENT));

        gl4.glBindVertexArray(0);
    }

    private void initTexture(GL4 gl4, RenderModel_TextureMap_t diffuseTexture) {

        // create and populate the texture
        gl4.glGenTextures(1, textureName);
        gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(0));

        ByteBuffer buffer = GLBuffers.newDirectByteBuffer(diffuseTexture.dataSize());
        byte[] data = diffuseTexture.rubTextureMapData.getByteArray(0, diffuseTexture.dataSize());

        IntStreamEx.range(diffuseTexture.dataSize()).forEach(i -> buffer.put(i, data[i]));

        gl4.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, diffuseTexture.unWidth, diffuseTexture.unHeight,
                0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // If this renders black ask McJohn what's wrong.
        gl4.glGenerateMipmap(GL_TEXTURE_2D);

        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

        FloatBuffer largest = GLBuffers.newDirectFloatBuffer(1);
        gl4.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, largest);
        gl4.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, largest.get(0));

        gl4.glBindTexture(GL_TEXTURE_2D, 0);

        BufferUtils.destroyDirectBuffer(buffer);
        BufferUtils.destroyDirectBuffer(largest);
    }

    /**
     * Draws the render model.
     * @param gl4 
     */
    public void render(GL4 gl4) {
        
        gl4.glBindVertexArray(vertexArrayName.get(0));
        
        gl4.glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE);
        gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(0));
        
        gl4.glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_SHORT, 0);
        
        gl4.glBindVertexArray(0);
    }
    
    /**
     * Frees the GL resources for a render model.
     * @param gl4 
     */
    public void delete(GL4 gl4) {
        if (gl4.glIsBuffer(bufferName.get(0))) {
            gl4.glDeleteBuffers(Buffer.MAX, bufferName);
        }
        if (gl4.glIsVertexArray(vertexArrayName.get(0))) {
            gl4.glDeleteVertexArrays(1, vertexArrayName);
        }
        if (gl4.glIsTexture(textureName.get(0))) {
            gl4.glDeleteTextures(1, textureName);
        }
        BufferUtils.destroyDirectBuffer(bufferName);
        BufferUtils.destroyDirectBuffer(vertexArrayName);
        BufferUtils.destroyDirectBuffer(textureName);
    }
}
