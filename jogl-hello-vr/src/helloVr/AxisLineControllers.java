/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL2ES2.GL_STREAM_DRAW;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import glm.mat._4.Mat4;
import glm.vec._3.Vec3;
import glm.vec._4.Vec4;
import glutil.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import one.util.streamex.IntStreamEx;
import vr.VR;

/**
 * Draw all of the controllers as X/Y/Z lines.
 *
 * @author GBarbieri
 */
public class AxisLineControllers {

    private final String SHADERS_SRC = "controller-transform";

    private int vertCount = 0;
    private final int SIZE = 2 * (3 * 4 * Vec3.SIZE + 4 * Vec3.SIZE);
    private IntBuffer vertexArrayName = GLBuffers.newDirectIntBuffer(1),
            vertexBufferName = GLBuffers.newDirectIntBuffer(1);
    public Program program;
    private List<Float> vertDataArray = new ArrayList<>();
    private ByteBuffer vertexBuffer = GLBuffers.newDirectByteBuffer(SIZE);

    public AxisLineControllers(GL4 gl4) {

        program = new Program(gl4);

        initBuffer(gl4);

        initVertexArray(gl4);
    }

    private void initBuffer(GL4 gl4) {

        gl4.glGenBuffers(1, vertexBufferName);
        gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));

        gl4.glBufferData(GL_ARRAY_BUFFER, SIZE, null, GL_STREAM_DRAW);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void initVertexArray(GL4 gl4) {

        gl4.glGenVertexArrays(1, vertexArrayName);
        gl4.glBindVertexArray(vertexArrayName.get(0));

        gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));

        int stride = 2 * 3 * Float.BYTES;
        int offset = 0;

        gl4.glEnableVertexAttribArray(Semantic.Attr.POSITION);
        gl4.glVertexAttribPointer(Semantic.Attr.POSITION, 3, GL_FLOAT, false, stride, offset);

        offset += Vec3.SIZE;
        gl4.glEnableVertexAttribArray(Semantic.Attr.COLOR);
        gl4.glVertexAttribPointer(Semantic.Attr.COLOR, 3, GL_FLOAT, false, stride, offset);

        gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl4.glBindVertexArray(0);
    }

    public void render(GL4 gl4, Application app) {

        if (app.hmd.IsInputFocusCapturedByAnotherProcess.apply() == 0) {

            gl4.glUseProgram(program.name);
            gl4.glUniformMatrix4fv(program.matrixUL, 1, false, app.matBuffer);
            gl4.glBindVertexArray(vertexArrayName.get(0));
            gl4.glDrawArrays(GL_LINES, 0, vertCount);
            gl4.glBindVertexArray(0);
        }
    }

    public void update(GL4 gl4, Application app) {

        // don't draw controllers if somebody else has input focus
        if (app.hmd.IsInputFocusCapturedByAnotherProcess.apply() == 1) {
            return;
        }

        vertDataArray.clear();
        vertCount = 0;
        app.trackedControllerCount = 0;

        for (int trackedDevice = VR.k_unTrackedDeviceIndex_Hmd + 1; trackedDevice < VR.k_unMaxTrackedDeviceCount;
                trackedDevice++) {

            if (app.hmd.IsTrackedDeviceConnected.apply(trackedDevice) == 0) {
                continue;
            }

            if (app.hmd.GetTrackedDeviceClass.apply(trackedDevice)
                    != VR.ETrackedDeviceClass.TrackedDeviceClass_Controller) {
                continue;
            }

            app.trackedControllerCount++;

            if (app.trackedDevicePose[trackedDevice].bPoseIsValid == 0) {
                continue;
            }

            Mat4 mat = app.mat4DevicePose[trackedDevice];

            Vec4 center = mat.mul(new Vec4(0, 0, 0, 1));

            for (int i = 0; i < 3; i++) {

                Vec3 color = new Vec3(0, 0, 0);
                Vec4 point = new Vec4(0, 0, 0, 1);

                switch (i) {

                    case 0:
                        point.x += 0.05f;   // offset in X, Y, Z
                        color.x = 1.0f;     // R, G, B
                        break;

                    case 1:
                        point.y += 0.05f;   // offset in X, Y, Z
                        color.y = 1.0f;     // R, G, B
                        break;

                    case 2:
                        point.z += 0.05f;   // offset in X, Y, Z
                        color.z = 1.0f;     // R, G, B
                        break;
                }

                mat.mul(point);

                vertDataArray.add(center.x);
                vertDataArray.add(center.y);
                vertDataArray.add(center.z);

                vertDataArray.add(color.x);
                vertDataArray.add(color.y);
                vertDataArray.add(color.z);

                vertDataArray.add(point.x);
                vertDataArray.add(point.y);
                vertDataArray.add(point.z);

                vertDataArray.add(color.x);
                vertDataArray.add(color.y);
                vertDataArray.add(color.z);

                vertCount += 2;
            }

            Vec4 start = mat.mul(new Vec4(0, 0, -0.02f, 1));
            Vec4 end = mat.mul(new Vec4(0, 0, -39.f, 1));
            Vec3 color = new Vec3(.92f, .92f, .71f);

            vertDataArray.add(start.x);
            vertDataArray.add(start.y);
            vertDataArray.add(start.z);

            vertDataArray.add(color.x);
            vertDataArray.add(color.y);
            vertDataArray.add(color.z);

            vertDataArray.add(end.x);
            vertDataArray.add(end.y);
            vertDataArray.add(end.z);

            vertDataArray.add(color.x);
            vertDataArray.add(color.y);
            vertDataArray.add(color.z);

            vertCount += 2;
        }

        // set vertex data if we have some
        if (!vertDataArray.isEmpty()) {

            IntStreamEx.range(vertDataArray.size()).forEach(i
                    -> vertexBuffer.putFloat(i * Float.BYTES, vertDataArray.get(i)));

            gl4.glBindBuffer(GL_ARRAY_BUFFER, vertexBufferName.get(0));
            gl4.glBufferSubData(GL_ARRAY_BUFFER, 0, SIZE, vertexBuffer);
            gl4.glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
    }

    public void dispose(GL4 gl4) {
        
        gl4.glDeleteProgram(program.name);
        gl4.glDeleteVertexArrays(1, vertexArrayName);
        gl4.glDeleteBuffers(1, vertexBufferName);
        
        BufferUtils.destroyDirectBuffer(vertexArrayName);
        BufferUtils.destroyDirectBuffer(vertexBufferName);
        BufferUtils.destroyDirectBuffer(vertexBuffer);
    } 
    
    private class Program extends glsl.Program {

        public int matrixUL = -1;

        public Program(GL4 gl4) {

            super(gl4, Application.SHADERS_ROOT, SHADERS_SRC);

            matrixUL = gl4.glGetUniformLocation(name, "matrix");
        }
    }
}
