package hellovr

import com.jogamp.opengl.GL
import com.jogamp.opengl.GL2ES3.*
import glm_.BYTES
import glm_.L
import glm_.f
import glm_.mat4x4.Mat4
import glm_.set
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec3.Vec3i
import glm_.vec4.Vec4
import openvr.lib.ETrackedDeviceClass
import openvr.lib.EVREye
import openvr.lib.maxTrackedDeviceCount
import openvr.lib.trackedDeviceIndex_Hmd
import uno.buffer.floatBufferBig
import uno.buffer.intBufferBig
import uno.buffer.toBuffer
import uno.buffer.use
import uno.glsl.Program
import uno.kotlin.url
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO

class Scene {

    val program = ProgramScene()

    val scale = .3f
    val scaleSpacing = 4f

    val sceneVolume = Vec3i(20)     // if you want something other than the default 20x20x20
    var vertexCount = 0
    val vertexArrayName = intBufferBig(1)
    val bufferName = intBufferBig(1)
    val textureName = intBufferBig(1)
    val controllerAxes = ControllerAxes()
    val modelProgram = ProgramModel()

    init {
        setupTextureMaps()
        setup()
    }

    fun setupTextureMaps() = with(gl) {

        val image = ImageIO.read("cube_texture.png".url)
        (image.raster.dataBuffer as DataBufferByte).data.toBuffer().use {

            glGenTextures(1, textureName)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, textureName[0])

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, image.width, image.height, 0, GL_RGB, GL_UNSIGNED_BYTE, it)

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
        }
    }

    /** Purpose: create a sea of cubes  */
    fun setup() {

        val vertDataArray = ArrayList<Float>()

        val matScale = Mat4() scale scale
        val matTranlate = Mat4() translate Vec3(-sceneVolume * scaleSpacing / 2f)

        val mat: Mat4 = matScale * matTranlate

        repeat(sceneVolume.x) {

            repeat(sceneVolume.y) {

                repeat(sceneVolume.z) {

                    addCube(mat, vertDataArray)
                    mat *= Mat4().translate(scaleSpacing, 0f, 0f)
                }
                mat *= Mat4().translate(-sceneVolume.x * scaleSpacing, scaleSpacing, 0f)
            }
            mat *= Mat4().translate(0f, -sceneVolume.y * scaleSpacing, scaleSpacing)
        }
        vertexCount = vertDataArray.size / 5

        with(gl) {

            glGenVertexArrays(1, vertexArrayName)
            glBindVertexArray(vertexArrayName[0])

            vertDataArray.toFloatArray().toBuffer().use {

                glGenBuffers(1, bufferName)
                glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])
                glBufferData(GL_ARRAY_BUFFER, Float.BYTES * vertDataArray.size.L, it, GL_STATIC_DRAW)

                val stride = VertexData.SIZE
                var offset = 0L

                glEnableVertexAttribArray(Semantic.Attr.POSITION)
                glVertexAttribPointer(Semantic.Attr.POSITION, Vec3.length, GL_FLOAT, false, stride, offset)

                offset += Vec3.size
                glEnableVertexAttribArray(Semantic.Attr.TEX_COORD)
                glVertexAttribPointer(Semantic.Attr.TEX_COORD, Vec2.length, GL_FLOAT, false, stride, offset)

                glBindVertexArray(0)
                glDisableVertexAttribArray(Semantic.Attr.POSITION)
                glDisableVertexAttribArray(Semantic.Attr.TEX_COORD)
            }
        }
    }

    fun addCube(mat: Mat4, vertData: ArrayList<Float>) {

        val a = mat * Vec4(0, 0, 0, 1)
        val b = mat * Vec4(1, 0, 0, 1)
        val c = mat * Vec4(1, 1, 0, 1)
        val d = mat * Vec4(0, 1, 0, 1)
        val e = mat * Vec4(0, 0, 1, 1)
        val f = mat * Vec4(1, 0, 1, 1)
        val g = mat * Vec4(1, 1, 1, 1)
        val h = mat * Vec4(0, 1, 1, 1)

        // triangles instead of quads
        addCubeVertex(e.x, e.y, e.z, 0, 1, vertData) //Front
        addCubeVertex(f.x, f.y, f.z, 1, 1, vertData)
        addCubeVertex(g.x, g.y, g.z, 1, 0, vertData)
        addCubeVertex(g.x, g.y, g.z, 1, 0, vertData)
        addCubeVertex(h.x, h.y, h.z, 0, 0, vertData)
        addCubeVertex(e.x, e.y, e.z, 0, 1, vertData)

        addCubeVertex(b.x, b.y, b.z, 0, 1, vertData) //Back
        addCubeVertex(a.x, a.y, a.z, 1, 1, vertData)
        addCubeVertex(d.x, d.y, d.z, 1, 0, vertData)
        addCubeVertex(d.x, d.y, d.z, 1, 0, vertData)
        addCubeVertex(c.x, c.y, c.z, 0, 0, vertData)
        addCubeVertex(b.x, b.y, b.z, 0, 1, vertData)

        addCubeVertex(h.x, h.y, h.z, 0, 1, vertData) //Top
        addCubeVertex(g.x, g.y, g.z, 1, 1, vertData)
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertData)
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertData)
        addCubeVertex(d.x, d.y, d.z, 0, 0, vertData)
        addCubeVertex(h.x, h.y, h.z, 0, 1, vertData)

        addCubeVertex(a.x, a.y, a.z, 0, 1, vertData) //Bottom
        addCubeVertex(b.x, b.y, b.z, 1, 1, vertData)
        addCubeVertex(f.x, f.y, f.z, 1, 0, vertData)
        addCubeVertex(f.x, f.y, f.z, 1, 0, vertData)
        addCubeVertex(e.x, e.y, e.z, 0, 0, vertData)
        addCubeVertex(a.x, a.y, a.z, 0, 1, vertData)

        addCubeVertex(a.x, a.y, a.z, 0, 1, vertData) //Left
        addCubeVertex(e.x, e.y, e.z, 1, 1, vertData)
        addCubeVertex(h.x, h.y, h.z, 1, 0, vertData)
        addCubeVertex(h.x, h.y, h.z, 1, 0, vertData)
        addCubeVertex(d.x, d.y, d.z, 0, 0, vertData)
        addCubeVertex(a.x, a.y, a.z, 0, 1, vertData)

        addCubeVertex(f.x, f.y, f.z, 0, 1, vertData) //Right
        addCubeVertex(b.x, b.y, b.z, 1, 1, vertData)
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertData)
        addCubeVertex(c.x, c.y, c.z, 1, 0, vertData)
        addCubeVertex(g.x, g.y, g.z, 0, 0, vertData)
        addCubeVertex(f.x, f.y, f.z, 0, 1, vertData)
    }

    fun addCubeVertex(fl0: Float, fl1: Float, fl2: Float, fl3: Int, fl4: Int, vertData: ArrayList<Float>) {
        vertData.add(fl0)
        vertData.add(fl1)
        vertData.add(fl2)
        vertData.add(fl3.f)
        vertData.add(fl4.f)
    }

    /** Purpose: Renders a scene with respect to nEye.  */
    fun render(eye: EVREye) = with(gl) {

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

        val isInputAvailable = hmd.isInputAvailable

        // draw the controller axis lines
        if (!isInputAvailable)
            controllerAxes.render(eye)

        // ----- Render Model rendering -----
        glUseProgram(modelProgram.name)

        for (trackedDevice in 0 until maxTrackedDeviceCount) {

            if (!trackedDeviceToRenderModel.contains(trackedDevice) || !showTrackedDevice[trackedDevice])
                continue

            if (!trackedDevicePose[trackedDevice].poseIsValid)
                continue

            if (isInputAvailable && hmd.getTrackedDeviceClass(trackedDevice) == ETrackedDeviceClass.Controller)
                continue

            val deviceToTracking = devicesPoses[trackedDevice]
            val mvp = getCurrentViewProjectionMatrix(eye) * deviceToTracking
            glUniformMatrix4fv(modelProgram.matrix, 1, false, mvp to bufferMat)

            trackedDeviceToRenderModel[trackedDevice]!!.draw()
        }
        glUseProgram(0)
    }

    class VertexData(val position: Vec3, val texCoord: Vec2) {
        companion object {
            val SIZE = Vec3.size + Vec2.size
        }
    }

    class ProgramScene : ProgramA("scene") {
        init {
            with(gl) {
                glUseProgram(name)
                glUniform1i(glGetUniformLocation(name, "myTexture"), Semantic.Sampler.DIFFUSE)
                glUseProgram(0)
            }
        }
    }

    class ProgramModel : ProgramA("render-model") {
        init {
            with(gl) {
                glUseProgram(name)
                glUniform1i(glGetUniformLocation(name, "diffuse"), Semantic.Sampler.DIFFUSE)
                glUseProgram(0)
            }
        }
    }

    open class ProgramA(shader: String) : Program(gl, shader) {
        val matrix = gl.glGetUniformLocation(name, "matrix")
    }

    class ControllerAxes {

        val program = ProgramA("controller")
        val vertexArrayName = intBufferBig(1)
        val bufferName = intBufferBig(1)
        val vertexBuffer = floatBufferBig(3 * 4 * 3 + 3 * 4)
        var vertCount = 0

        init {
            // Setup the VAO the first time through.
            with(gl) {

                glGenVertexArrays(1, vertexArrayName)
                glBindVertexArray(vertexArrayName[0])

                glGenBuffers(1, bufferName)
                glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])

                glBufferData(GL.GL_ARRAY_BUFFER, vertexBuffer.capacity() * Float.BYTES.L, null, GL_STREAM_DRAW)

                val stride = 2 * Vec3.size
                var offset = 0L

                glEnableVertexAttribArray(Semantic.Attr.POSITION)
                glVertexAttribPointer(Semantic.Attr.POSITION, Vec3.length, GL_FLOAT, false, stride, offset)

                offset += Vec3.size
                glEnableVertexAttribArray(Semantic.Attr.COLOR)
                glVertexAttribPointer(Semantic.Attr.COLOR, Vec3.length, GL_FLOAT, false, stride, offset)

                glBindVertexArray(0)
            }
        }

        /** Purpose: Draw all of the controllers as X/Y/Z lines */
        fun update() {

            // Don't attempt to update controllers if input is not available
            if (hmd.isInputAvailable) return

            val vertDataArray = FloatArray(3 * 4 * Vec3.length + 3 * 4)

            vertCount = 0
            trackedControllerCount = 0

            for (trackedDevice in trackedDeviceIndex_Hmd + 1 until maxTrackedDeviceCount) {

                if (!hmd.isTrackedDeviceConnected(trackedDevice)) continue

                if (hmd.getTrackedDeviceClass(trackedDevice) != ETrackedDeviceClass.Controller) continue

                trackedControllerCount++

                if (!trackedDevicePose[trackedDevice].poseIsValid) continue

                val mat = devicesPoses[trackedDevice]

                val center = mat * Vec4(0, 0, 0, 1)

                val stride = 4 * Vec3.length

                for (i in 0..2) {

                    val color = Vec3(0)
                    val point = Vec4(0, 0, 0, 1)
                    point[i] += .05f  // offset in X, Y, Z
                    color[i] = 1  // R, G, B
                    point put (mat * point)

                    center.to(vertDataArray, stride * i)
                    color.to(vertDataArray, stride * i + 3)
                    point.to(vertDataArray, stride * i + 6)
                    color.to(vertDataArray, stride * i + 9)

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

                glBindBuffer(GL_ARRAY_BUFFER, bufferName[0])
                // set vertex data if we have some
                if (vertDataArray.isNotEmpty()) {
                    vertDataArray.forEachIndexed { i, it -> vertexBuffer[i] = it }
                    glBufferSubData(GL_ARRAY_BUFFER, 0, Float.BYTES * vertexBuffer.capacity().L, vertexBuffer)
                }
            }
        }

        fun render(eye: EVREye) = with(gl) {
            glUseProgram(program.name)
            glUniformMatrix4fv(program.matrix, 1, false, getCurrentViewProjectionMatrix(eye) to bufferMat)
            glBindVertexArray(vertexArrayName[0])
            glDrawArrays(GL_LINES, 0, vertCount)
            glBindVertexArray(0)
        }
    }
}