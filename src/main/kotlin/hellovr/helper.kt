/**
 * Created by GBarbieri on 01.02.2017.
 */

package hellovr

import com.jogamp.opengl.GL2ES3.*
import com.jogamp.opengl.GL3
import glm_.BYTES
import glm_.L
import glm_.i
import glm_.size
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import openvr.lib.EVREye
import openvr.lib.RenderModel_TextureMap
import openvr.lib.RenderModel_Vertex
import openvr.lib.RenderModel
import uno.buffer.*
import uno.glsl.Program


val bufferMat = floatBufferBig(16)
val clearColor = floatBufferOf(0f, 0f, 0f, 1f)
val clearDepth = floatBufferOf(1f)


object Semantic {
    object Attr {
        val COLOR = 0
        val NORMAL = 1
        val POSITION = 2
        val TEX_COORD = 3
    }

    object Sampler {
        val DIFFUSE = 0
    }
}

class CGLRenderModel(val renderModelName: String, vrModel: RenderModel.ByReference, vrDiffuseTexture: RenderModel_TextureMap.ByReference) {

    object Buffer {
        val VERTEX = 0
        val INDEX = 1
        val MAX = 2
    }

    var bufferName = intBufferBig(Buffer.MAX)
    val vertexArrayName = intBufferBig(1)
    val textureName = intBufferBig(1)
    var vertexCount = 0

    /**
     * Purpose: Allocates and populates the GL resources for a render model
     */
    init {

        with(gl) {

            // create and bind a VAO to hold state for this model
            glGenVertexArrays(1, vertexArrayName)
            glBindVertexArray(vertexArrayName[0])

            // Populate a vertex buffer
            vrModel.vertices!!.toBuffer().use {
                glGenBuffers(Buffer.MAX, bufferName)
                glBindBuffer(GL_ARRAY_BUFFER, bufferName[Buffer.VERTEX])
                glBufferData(GL_ARRAY_BUFFER, it.capacity().L, it, GL_STATIC_DRAW)
            }

            // Identify the components in the vertex buffer
            glEnableVertexAttribArray(Semantic.Attr.POSITION)
            glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, RenderModel_Vertex.SIZE,
                    RenderModel_Vertex.POSITION_OFFSET.L)
            glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
            glVertexAttribPointer(Semantic.Attr.TEX_COORD, 2, GL_FLOAT, false, RenderModel_Vertex.SIZE,
                    RenderModel_Vertex.TEX_COORD_OFFSET.L)

            // Create and populate the index buffer
            vrModel.indices!!.toBuffer().use {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName[Buffer.INDEX])
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, it.capacity().L, it, GL_STATIC_DRAW)
            }

            glBindVertexArray(0)

            // create and populate the texture
            vrDiffuseTexture.textureMapData!!.toBuffer().use {
                glGenTextures(1, textureName)
                glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE)
                glBindTexture(GL_TEXTURE_2D, textureName[0])

                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, vrDiffuseTexture.width.i, vrDiffuseTexture.height.i, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, it)
            }

            // If this renders black ask McJohn what's wrong.
            glGenerateMipmap(GL_TEXTURE_2D)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)

            floatBufferBig(1).use {
                glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, it)
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, it[0])
            }

            glBindTexture(GL_TEXTURE_2D, 0)

            vertexCount = vrModel.triangleCount * 3
        }
    }

    /**
     * Purpose: Draws the render model
     */
    fun draw() = with(gl) {

        glBindVertexArray(vertexArrayName[0])

        glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE)
        glBindTexture(GL_TEXTURE_2D, textureName[0])

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_SHORT, 0)

        glBindVertexArray(0)
    }

    /**
     * Purpose: Frees the GL resources for a render model
     */
    fun cleanUp() {

        with(gl) {

            glDeleteBuffers(Buffer.MAX, bufferName)
            glDeleteVertexArrays(1, vertexArrayName)

            bufferName.destroy()
            vertexArrayName.destroy()
        }
    }
}

/** Purpose: Gets a Current View Projection Matrix with respect to nEye, which may be an Eye_Left or an Eye_Right.  */
fun getCurrentViewProjectionMatrix(eye: EVREye) = projection[eye.i] * eyePos[eye.i] * hmdPose

class FrameBufferDesc(val size: Vec2i) {

    object Target {
        val RENDER = 0
        val RESOLVE = 1
        val MAX = 2
    }

    val depthRenderbufferName = intBufferBig(1)
    val textureName = intBufferBig(Target.MAX)
    val frameBufferName = intBufferBig(Target.MAX)

    /** Purpose: Creates a frame buffer. Returns true if the buffer was set up. Throw error if the setup failed.    */
    init {

        with(gl) {

            glGenFramebuffers(Target.MAX, frameBufferName)
            glGenRenderbuffers(1, depthRenderbufferName)
            glGenTextures(Target.MAX, textureName)

            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferName[Target.RENDER])

            glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbufferName[0])
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_DEPTH_COMPONENT, size.x, size.y)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRenderbufferName[0])

            glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, textureName[Target.RENDER])
            glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 4, GL_RGBA8, size.x, size.y, true)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE, textureName[Target.RENDER], 0)


            glBindFramebuffer(GL_FRAMEBUFFER, frameBufferName[Target.RESOLVE])

            glBindTexture(GL_TEXTURE_2D, textureName[Target.RESOLVE])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size.x, size.y, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureName[Target.RESOLVE], 0)

            // check FBO status
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw Error("framebuffer incomplete!")

            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }
    }

    fun render(eye: EVREye) = with(gl) {

        glEnable(GL_MULTISAMPLE)

        glBindFramebuffer(GL_FRAMEBUFFER, frameBufferName[Target.RENDER])
        glViewport(0, 0, size.x, size.y)
        scene.render(eye)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        glDisable(GL_MULTISAMPLE)

        glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBufferName[Target.RENDER])
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBufferName[Target.RESOLVE])

        glBlitFramebuffer(0, 0, size.x, size.y, 0, 0, size.x, size.y, GL_COLOR_BUFFER_BIT, GL_LINEAR)

        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
    }
}

class CompanionWindow() {

    object Buffer {
        val VERTEX = 0
        val INDEX = 1
        val MAX = 2
    }

    var bufferName = intBufferBig(Buffer.MAX)
    val vertexArrayName = intBufferBig(1)
    val indexSize: Int
    val program = ProgramWindow()

    init {

        val vertices = floatBufferOf(
                /* left eye verts
                | Pos | TexCoord    */
                -1, -1, 0, 0,
                +0, -1, 1, 0,
                -1, +1, 0, 1,
                +0, +1, 1, 1,
                /*  right eye verts
                | Pos | TexCoord    */
                +0, -1, 0, 0,
                +1, -1, 1, 0,
                +0, +1, 0, 1,
                +1, +1, 1, 1)

        val indices = shortBufferOf(
                0, 1, 3,
                0, 3, 2,
                4, 5, 7,
                4, 7, 6)

        indexSize = indices.size / Short.BYTES

        with(gl) {

            glGenVertexArrays(1, vertexArrayName)
            glBindVertexArray(vertexArrayName[0])

            glGenBuffers(Buffer.MAX, bufferName)

            glBindBuffer(GL_ARRAY_BUFFER, bufferName[Buffer.VERTEX])
            glBufferData(GL_ARRAY_BUFFER, vertices.size.L, vertices, GL_STATIC_DRAW)

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName[Buffer.INDEX])
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size.L, indices, GL_STATIC_DRAW)

            glEnableVertexAttribArray(Semantic.Attr.POSITION)
            glVertexAttribPointer(Semantic.Attr.POSITION, Vec2.length, GL_FLOAT, false, VertexData.SIZE, VertexData.OFFSET_POSITION)

            glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
            glVertexAttribPointer(Semantic.Attr.TEX_COORD, Vec2.length, GL_FLOAT, false, VertexData.SIZE, VertexData.OFFSET_TEXCOORD)

            glBindVertexArray(0)

            glDisableVertexAttribArray(Semantic.Attr.POSITION)
            glDisableVertexAttribArray(Semantic.Attr.TEX_COORD)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    fun render() = with(gl) {

        glDisable(GL_DEPTH_TEST)
        glViewport(0, 0, companionWindowSize.x, companionWindowSize.y)

        glBindVertexArray(vertexArrayName[0])
        glUseProgram(program.name)

        // render left eye with first half of index array and right eye with the second half.
        for (eye in EVREye.values()) {
            glBindTexture(GL_TEXTURE_2D, eyeDesc[eye.i].textureName[FrameBufferDesc.Target.RESOLVE])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            // offset is in bytes, so indexSize points to half of indices, because short -> int
            glDrawElements(GL_TRIANGLES, indexSize / 2, GL_UNSIGNED_SHORT, if (eye == EVREye.Left) 0 else indexSize.L)
        }

        glBindVertexArray(0)
        glUseProgram(0)
    }

    object VertexData {
        val SIZE = Vec2.size * 2
        val OFFSET_POSITION = 0.L
        val OFFSET_TEXCOORD = Vec2.size.L
    }

    class ProgramWindow : Program(gl, "companion-window") {
        init {
            with(gl) {
                glUseProgram(name)
                glUniform1i(glGetUniformLocation(name, "myTexture"), Semantic.Sampler.DIFFUSE)
                glUseProgram(0)
            }
        }
    }
}