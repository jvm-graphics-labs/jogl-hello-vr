/**
 * Created by GBarbieri on 01.02.2017.
 */

package hellovr

import com.jogamp.opengl.GL
import com.jogamp.opengl.GL2ES3.*
import com.jogamp.opengl.GL3
import com.sun.jna.ptr.IntByReference
import glm_.*
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec3.Vec3i
import glm_.vec4.Vec4
import openvr.*
import uno.buffer.*
import uno.glsl.Program
import uno.kotlin.url
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO


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

class CGLRenderModel(val renderModelName: String, gl: GL3, vrModel: RenderModel_t.ByReference,
                     vrDiffuseTexture: RenderModel_TextureMap_t.ByReference) {

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
            val vertexBuffer = vrModel.vertices!!.toByteBuffer()
            glGenBuffers(Buffer.MAX, bufferName)
            glBindBuffer(GL_ARRAY_BUFFER, bufferName[Buffer.VERTEX])
            glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity().L, vertexBuffer, GL_STATIC_DRAW)

            // Identify the components in the vertex buffer
            glEnableVertexAttribArray(Semantic.Attr.POSITION)
            glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, RenderModel_Vertex_t.SIZE,
                    RenderModel_Vertex_t.POSITION_OFFSET.L)
            glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
            glVertexAttribPointer(Semantic.Attr.TEX_COORD, 2, GL_FLOAT, false, RenderModel_Vertex_t.SIZE,
                    RenderModel_Vertex_t.TEX_COORD_OFFSET.L)

            // Create and populate the index buffer
            val indexBuffer = vrModel.indices!!.toByteBuffer()
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferName[Buffer.INDEX])
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer.capacity().L, indexBuffer, GL_STATIC_DRAW)

            glBindVertexArray(0)

            // create and populate the texture
            val texBuffer = vrDiffuseTexture.textureMapData!!.toByteBuffer()
            glGenTextures(1, textureName)
            glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE)
            glBindTexture(GL_TEXTURE_2D, textureName[0])

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, vrDiffuseTexture.unWidth.i, vrDiffuseTexture.unHeight.i, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, texBuffer)

            // If this renders black ask McJohn what's wrong.
            glGenerateMipmap(GL_TEXTURE_2D)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)

            val largest = floatBufferBig(1)
            glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, largest)
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, largest[0])

            glBindTexture(GL_TEXTURE_2D, 0)

            vertexCount = vrModel.unTriangleCount * 3

            vertexBuffer.destroy()
            indexBuffer.destroy()
            texBuffer.destroy()
            largest.destroy()
        }
    }

    /**
     * Purpose: Draws the render model
     */
    fun draw(gl: GL3) = with(gl) {

        glBindVertexArray(vertexArrayName[0])

        glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE)
        glBindTexture(GL_TEXTURE_2D, textureName[0])

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_SHORT, 0)

        glBindVertexArray(0)
    }

    /**
     * Purpose: Frees the GL resources for a render model
     */
    fun cleanUp(gl: GL3) {

        with(gl) {

            glDeleteBuffers(Buffer.MAX, bufferName)
            glDeleteVertexArrays(1, vertexArrayName)

            bufferName.destroy()
            vertexArrayName.destroy()
        }
    }
}

class Scene(gl: GL3) {

    class ProgramScene(gl: GL3, shader: String) : ProgramA(gl, shader) {
        val myTexture = gl.glGetUniformLocation(name, "myTexture")
    }

    class ProgramModel(gl: GL3, shader: String) : ProgramA(gl, shader) {
        val diffuse = gl.glGetUniformLocation(name, "diffuse")
    }

    val program = ProgramScene(gl, "scene")
    val scale = .3f
    val scaleSpacing = 4f
    val sceneVolume = Vec3i(20)     // if you want something other than the default 20x20x20
    var vertexCount = 0
    val vertexArrayName = intBufferBig(1)
    val bufferName = intBufferBig(1)
    val textureName = intBufferBig(1)
    val controllerAxes = ControllerAxes(gl)
    val modelProgram = ProgramModel(gl, "render-model")

    init {
        setup(gl)
        setupTextureMaps(gl)
        gl.glUseProgram(program.name)
        gl.glUniform1i(program.myTexture, Semantic.Sampler.DIFFUSE)
        gl.glUseProgram(modelProgram.name)
        gl.glUniform1i(modelProgram.diffuse, Semantic.Sampler.DIFFUSE)
        gl.glUseProgram(0)
    }

    /** Purpose: create a sea of cubes  */
    fun setup(gl: GL3) {

        val vertDataArray = ArrayList<Float>()

        val matScale = Mat4().scale(scale)
        val matTranlate = Mat4().translate(
                -sceneVolume.x * scaleSpacing / 2f,
                -sceneVolume.y * scaleSpacing / 2f,
                -sceneVolume.z * scaleSpacing / 2f)

        var mat = matScale * matTranlate

        repeat(sceneVolume.x) {

            repeat(sceneVolume.y) {

                repeat(sceneVolume.z) {
                    addCube(mat, vertDataArray)
                    mat *= Mat4().translate_(scaleSpacing, 0f, 0f)
                }
                mat *= Mat4().translate_(-sceneVolume.x * scaleSpacing, scaleSpacing, 0f)
            }
            mat *= Mat4().translate_(0f, -sceneVolume.y * scaleSpacing, scaleSpacing)
        }
        vertexCount = vertDataArray.size / 5

        with(gl) {

            glGenVertexArrays(1, vertexArrayName)
            glBindVertexArray(vertexArrayName[0])

            val buffer = vertDataArray.toFloatArray().toByteBuffer()

            glGenBuffers(1, bufferName)
            glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])
            glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertDataArray.size.L, buffer, GL_STATIC_DRAW)

            val stride = Vertex.SIZE
            var offset = 0L

            glEnableVertexAttribArray(Semantic.Attr.POSITION)
            glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, stride, offset)

            offset += Vec3.size
            glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
            glVertexAttribPointer(Semantic.Attr.TEX_COORD, 2, GL_FLOAT, false, stride, offset)

            glBindVertexArray(0)
            glDisableVertexAttribArray(Semantic.Attr.POSITION)
            glDisableVertexAttribArray(Semantic.Attr.TEX_COORD)

            buffer.destroy()
        }
    }

    fun setupTextureMaps(gl: GL3) = with(gl) {

        val bufferedImage = ImageIO.read("cube_texture.png".url)
        val dataBuffer = (bufferedImage.raster.dataBuffer as DataBufferByte).data.toByteBuffer()

        glGenTextures(1, textureName)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureName[0])

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, bufferedImage.width, bufferedImage.height, 0, GL_RGB, GL_UNSIGNED_BYTE, dataBuffer)

        glGenerateMipmap(GL_TEXTURE_2D)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val fLargest = floatBufferBig(1)
        glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, fLargest)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, fLargest[0])

        glBindTexture(GL_TEXTURE_2D, 0)

        dataBuffer.destroy()
        fLargest.destroy()
    }

    fun addCube(mat: Mat4, vertData: ArrayList<Float>) {

        val A = mat * Vec4(0, 0, 0, 1)
        val B = mat * Vec4(1, 0, 0, 1)
        val C = mat * Vec4(1, 1, 0, 1)
        val D = mat * Vec4(0, 1, 0, 1)
        val E = mat * Vec4(0, 0, 1, 1)
        val F = mat * Vec4(1, 0, 1, 1)
        val G = mat * Vec4(1, 1, 1, 1)
        val H = mat * Vec4(0, 1, 1, 1)

        // triangles instead of quads
        addCubeVertex(E.x, E.y, E.z, 0, 1, vertData) //Front
        addCubeVertex(F.x, F.y, F.z, 1, 1, vertData)
        addCubeVertex(G.x, G.y, G.z, 1, 0, vertData)
        addCubeVertex(G.x, G.y, G.z, 1, 0, vertData)
        addCubeVertex(H.x, H.y, H.z, 0, 0, vertData)
        addCubeVertex(E.x, E.y, E.z, 0, 1, vertData)

        addCubeVertex(B.x, B.y, B.z, 0, 1, vertData) //Back
        addCubeVertex(A.x, A.y, A.z, 1, 1, vertData)
        addCubeVertex(D.x, D.y, D.z, 1, 0, vertData)
        addCubeVertex(D.x, D.y, D.z, 1, 0, vertData)
        addCubeVertex(C.x, C.y, C.z, 0, 0, vertData)
        addCubeVertex(B.x, B.y, B.z, 0, 1, vertData)

        addCubeVertex(H.x, H.y, H.z, 0, 1, vertData) //Top
        addCubeVertex(G.x, G.y, G.z, 1, 1, vertData)
        addCubeVertex(C.x, C.y, C.z, 1, 0, vertData)
        addCubeVertex(C.x, C.y, C.z, 1, 0, vertData)
        addCubeVertex(D.x, D.y, D.z, 0, 0, vertData)
        addCubeVertex(H.x, H.y, H.z, 0, 1, vertData)

        addCubeVertex(A.x, A.y, A.z, 0, 1, vertData) //Bottom
        addCubeVertex(B.x, B.y, B.z, 1, 1, vertData)
        addCubeVertex(F.x, F.y, F.z, 1, 0, vertData)
        addCubeVertex(F.x, F.y, F.z, 1, 0, vertData)
        addCubeVertex(E.x, E.y, E.z, 0, 0, vertData)
        addCubeVertex(A.x, A.y, A.z, 0, 1, vertData)

        addCubeVertex(A.x, A.y, A.z, 0, 1, vertData) //Left
        addCubeVertex(E.x, E.y, E.z, 1, 1, vertData)
        addCubeVertex(H.x, H.y, H.z, 1, 0, vertData)
        addCubeVertex(H.x, H.y, H.z, 1, 0, vertData)
        addCubeVertex(D.x, D.y, D.z, 0, 0, vertData)
        addCubeVertex(A.x, A.y, A.z, 0, 1, vertData)

        addCubeVertex(F.x, F.y, F.z, 0, 1, vertData) //Right
        addCubeVertex(B.x, B.y, B.z, 1, 1, vertData)
        addCubeVertex(C.x, C.y, C.z, 1, 0, vertData)
        addCubeVertex(C.x, C.y, C.z, 1, 0, vertData)
        addCubeVertex(G.x, G.y, G.z, 0, 0, vertData)
        addCubeVertex(F.x, F.y, F.z, 0, 1, vertData)
    }

    fun addCubeVertex(fl0: Float, fl1: Float, fl2: Float, fl3: Int, fl4: Int, vertData: ArrayList<Float>) {

        vertData.add(fl0)
        vertData.add(fl1)
        vertData.add(fl2)
        vertData.add(fl3.f)
        vertData.add(fl4.f)
    }

    /** Purpose: Renders a scene with respect to nEye.  */
    fun render(gl: GL3, eye: EVREye) = with(gl) {

        glClearBufferfv(GL_COLOR, 0, clearColor)
        glClearBufferfv(GL_DEPTH, 0, clearDepth)
        glEnable(GL_DEPTH_TEST)

        if (showCubes) {
            glUseProgram(program.name)
            glUniformMatrix4fv(program.matrix, 1, false, getCurrentViewProjectionMatrix(eye) to bufferMat)
            glBindVertexArray(vertexArrayName[0])
            glActiveTexture(GL_TEXTURE0 + Semantic.Sampler.DIFFUSE)
            glBindTexture(GL_TEXTURE_2D, textureName[0])
            glDrawArrays(GL_TRIANGLES, 0, vertexCount)
            glBindVertexArray(0)
        }

        val isInputCapturedByAnotherProcess = hmd.isInputFocusCapturedByAnotherProcess()

        if (!isInputCapturedByAnotherProcess)
            controllerAxes.render(gl, eye)  // draw the controller axis lines

        // ----- Render Model rendering -----
        glUseProgram(modelProgram.name)

        for (trackedDevice in 0 until vr.maxTrackedDeviceCount) {

            if (!trackedDeviceToRenderModel.contains(trackedDevice) || !showTrackedDevice[trackedDevice])
                continue

            val pose = trackedDevicePose[trackedDevice]
            if (!pose.bPoseIsValid)
                continue

            if (isInputCapturedByAnotherProcess && hmd.getTrackedDeviceClass(trackedDevice) == ETrackedDeviceClass.Controller)
                continue

            val deviceToTracking = devicesPoses[trackedDevice]!!
            val mvp = getCurrentViewProjectionMatrix(eye) * deviceToTracking
            glUniformMatrix4fv(modelProgram.matrix, 1, false, mvp to bufferMat)

            trackedDeviceToRenderModel[trackedDevice]!!.draw(gl)
        }
        glUseProgram(0)
    }

    class Vertex(val position: Vec3, val texCoord: Vec2) {
        companion object {
            val SIZE = Vec3.size + Vec2.size
        }
    }

    open class ProgramA(gl: GL3, shader: String) : Program(gl, shader) {
        val matrix = gl.glGetUniformLocation(name, "matrix")
    }

    class ControllerAxes(gl: GL3) {

        val program = ProgramA(gl, "controller")
        val vertexArrayName = intBufferBig(1)
        val bufferName = intBufferBig(1)
        val bufferMat = floatBufferBig(16)
        val vertexBuffer = floatBufferBig(3 * 4 * 3 + 3 * 4)
        var vertCount = 0

        /** Purpose: Draw all of the controllers as X/Y/Z lines */
        fun updateControllerAxes(gl: GL3) {

            // don't draw controllers if somebody else has input focus
            if (hmd.isInputFocusCapturedByAnotherProcess()) return

            val vertDataArray = FloatArray(3 * 4 * Vec3.length + 3 * 4)

            vertCount = 0
            trackedControllerCount = 0


            for (trackedDevice in vr.trackedDeviceIndex_Hmd + 1 until vr.maxTrackedDeviceCount) {

                if (!hmd.isTrackedDeviceConnected(trackedDevice)) continue

                if (hmd.getTrackedDeviceClass(trackedDevice) != ETrackedDeviceClass.Controller) continue

                trackedControllerCount++

                if (!trackedDevicePose[trackedDevice].bPoseIsValid)
                    continue

                val mat = devicesPoses[trackedDevice]!! // TODO check

                val center = mat * Vec4(0, 0, 0, 1)

                val stride = 4 * Vec3.length

                repeat(3) {

                    val color = Vec3(0)
                    val point = Vec4(0, 0, 0, 1)
                    point[it] += .05f  // offset in X, Y, Z
                    color[it] = 1  // R, G, B
                    point put (mat * point)

                    center.to(vertDataArray, stride * it)
                    color.to(vertDataArray, stride * it + 3)
                    point.to(vertDataArray, stride * it + 6)
                    color.to(vertDataArray, stride * it + 9)

                    vertCount += 2
                }

                val start = mat * Vec4(0, 0, -.02f, 1)
                val end = mat * Vec4(0, 0, -39f, 1)
                val color = Vec3(.92f, .92f, .71f)

                start.to(vertDataArray, stride * 3)
                color.to(vertDataArray, stride * 3 + 3)

                end.to(vertDataArray, stride * 3 + 6)
                color.to(vertDataArray, stride * 3 + 9)

                vertCount += 2
            }

            with(gl) {

                // Setup the VAO the first time through.
                if (vertexArrayName[0] == 0) {
                    glGenVertexArrays(1, vertexArrayName)
                    glBindVertexArray(vertexArrayName[0])

                    glGenBuffers(1, bufferName)
                    glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])

                    glBufferData(GL.GL_ARRAY_BUFFER, vertexBuffer.capacity() * Float.BYTES.L, null, GL_STREAM_DRAW)

                    val stride = 2 * Vec3.size
                    var offset = 0L

                    glEnableVertexAttribArray(Semantic.Attr.POSITION)
                    glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, stride, offset)

                    offset += Vec3.size
                    glEnableVertexAttribArray(Semantic.Attr.COLOR)
                    glVertexAttribPointer(Semantic.Attr.COLOR, 3, GL_FLOAT, false, stride, offset)

                    glBindVertexArray(0)

                } else {

                    glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])

                    if (vertDataArray.isNotEmpty()) {   // set vertex data if we have some
                        vertDataArray.forEachIndexed { i, it ->
                            vertexBuffer[i] = it
//                            println("vertexBuffer[$i] = $it")
                        }
                        glBufferSubData(GL_ARRAY_BUFFER, 0, Float.BYTES * vertexBuffer.capacity().L, vertexBuffer)
                    }
                }
            }
        }

        fun render(gl: GL3, eye: EVREye) = with(gl) {
            glUseProgram(program.name)
            glUniformMatrix4fv(program.matrix, 1, false, getCurrentViewProjectionMatrix(eye) to bufferMat)
            glBindVertexArray(vertexArrayName[0])
            glDrawArrays(GL_LINES, 0, vertCount)
            glBindVertexArray(0)
        }
    }
}

/** Purpose: Gets a Current View Projection Matrix with respect to nEye, which may be an Eye_Left or an Eye_Right.  */
fun getCurrentViewProjectionMatrix(eye: EVREye) = projection[eye.i] * eyePos[eye.i] * hmdPose

class FrameBufferDesc(gl: GL3, width: IntByReference, height: IntByReference) {

    object Target {
        val RENDER = 0
        val RESOLVE = 1
        val MAX = 2
    }

    val size = Vec2i(width.value, height.value)

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

    fun render(gl: GL3, eye: EVREye) = with(gl) {

        glEnable(GL_MULTISAMPLE)

        glBindFramebuffer(GL_FRAMEBUFFER, frameBufferName[Target.RENDER])
        glViewport(0, 0, size.x, size.y)
        scene.render(gl, eye)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        glDisable(GL_MULTISAMPLE)

        glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBufferName[Target.RENDER])
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBufferName[Target.RESOLVE])

        glBlitFramebuffer(0, 0, size.x, size.y, 0, 0, size.x, size.y, GL_COLOR_BUFFER_BIT, GL_LINEAR)

        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
    }
}

class CompanionWindow(gl: GL3) {

    class ProgramWindow(gl: GL3, shader: String) : Program(gl, shader) {
        val myTexture = gl.glGetUniformLocation(name, "myTexture")
    }

    object Buffer {
        val VERTEX = 0
        val INDEX = 1
        val MAX = 2
    }

    var bufferName = intBufferBig(Buffer.MAX)
    val vertexArrayName = intBufferBig(1)
    val indexSize: Int
    val program = ProgramWindow(gl, "companion-window")

    init {

        val vertices = floatBufferOf(
                // left eye verts
                -1, -1, 0, 0,
                +0, -1, 1, 0,
                -1, +1, 0, 1,
                +0, +1, 1, 1,
                // right eye verts
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
            glVertexAttribPointer(Semantic.Attr.POSITION, 2, GL_FLOAT, false, Vertex.SIZE, Vertex.OFFSET_POSITION)

            glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
            glVertexAttribPointer(Semantic.Attr.TEX_COORD, 2, GL_FLOAT, false, Vertex.SIZE, Vertex.OFFSET_TEXCOORD)

            glBindVertexArray(0)

            glDisableVertexAttribArray(Semantic.Attr.POSITION)
            glDisableVertexAttribArray(Semantic.Attr.TEX_COORD)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)

            glUseProgram(program.name)
        }
    }

    fun render(gl: GL3) = with(gl) {

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

    class Vertex {
        companion object {
            val SIZE = Vec2.size * 2
            val OFFSET_POSITION = 0.L
            val OFFSET_TEXCOORD = Vec2.size.L
        }
    }
}