/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import java.nio.IntBuffer;
import vr.RenderModel_TextureMap_t;
import vr.RenderModel_t;

/**
 *
 * @author GBarbieri
 */
public class Model {

    public String name;

    private interface Buffer {

        public static final int VERTEX = 0;
        public static final int ELEMENT = 1;
        public static final int MAX = 2;
    }

    private IntBuffer bufferName = GLBuffers.newDirectIntBuffer(Buffer.MAX);

    public Model(String name) {
        this.name = name;
    }

    public boolean init(GL4 gl4, RenderModel_t.ByReference modelReference,
            RenderModel_TextureMap_t.ByReference textureReference) {

        initBuffers(gl4, modelReference);

        return false;
    }

    private void initBuffers(GL4 gl4, RenderModel_t.ByReference modelReference) {

        gl4.glGenBuffers( Buffer.MAX, bufferName );
        
        // Populate a vertex buffer	
	gl4.glBindBuffer( GL_ARRAY_BUFFER, bufferName.get(Buffer.VERTEX) );
        modelReference.read();
        int size = modelReference.unVertexCount;
        System.out.println("");
//	gl4.glBufferData( GL_ARRAY_BUFFER, sizeof( vr::RenderModel_Vertex_t ) * vrModel.unVertexCount, vrModel.rVertexData, GL_STATIC_DRAW );
    }
}
