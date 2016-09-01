/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import glm.mat._4.Mat4;
import glutil.BufferUtils;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import vr.IVRRenderModels_FnTable;
import vr.IVRSystem;
import vr.RenderModel_TextureMap_t;
import vr.RenderModel_t;
import vr.TrackedDevicePose_t;
import vr.VR;

/**
 *
 * @author GBarbieri
 */
public class ModelsRender {

    private final String SHADERS_SRC = "render-model";

    private List<Model> models = new ArrayList<>();
    private Program program;
    private Model[] trackedDeviceToRenderModel = new Model[VR.k_unMaxTrackedDeviceCount];
    private boolean[] showTrackedDevice = new boolean[VR.k_unMaxTrackedDeviceCount];

    public ModelsRender(GL4 gl4) {
        program = new Program(gl4);
    }

    /**
     * Create/destroy GL Render Models.
     *
     * @param gl4
     * @param hmd
     */
    public void setupRenderModels(GL4 gl4, IVRSystem hmd) {

        IntStreamEx.range(trackedDeviceToRenderModel.length).forEach(i -> trackedDeviceToRenderModel[i] = null);

        if (hmd == null) {
            return;
        }

        for (int trackedDevice = VR.k_unTrackedDeviceIndex_Hmd + 1; trackedDevice < VR.k_unMaxTrackedDeviceCount;
                trackedDevice++) {

            if (hmd.IsTrackedDeviceConnected.apply(trackedDevice) == 0) {
                continue;
            }
            setupRenderModelForTrackedDevice(gl4, trackedDevice, hmd);
        }
    }

    /**
     * Create/destroy GL a Render Model for a single tracked device.
     *
     * @param trackedDeviceIndex
     */
    private void setupRenderModelForTrackedDevice(GL4 gl4, int trackedDeviceIndex, IVRSystem hmd) {

        if (trackedDeviceIndex >= VR.k_unMaxTrackedDeviceCount) {
            return;
        }
        // try to find a model we've already set up
        String renderModelName = getTrackedDeviceString(hmd, trackedDeviceIndex,
                VR.ETrackedDeviceProperty.Prop_RenderModelName_String);

        Model model = findOrLoad(gl4, renderModelName);

        if (model == null) {
            String trackingSystemName = getTrackedDeviceString(hmd, trackedDeviceIndex,
                    VR.ETrackedDeviceProperty.Prop_TrackingSystemName_String);
            System.err.println("Unable to load render model for tracked device " + trackedDeviceIndex + "("
                    + trackingSystemName + "." + renderModelName + ")");
        } else {
            trackedDeviceToRenderModel[trackedDeviceIndex] = model;
            showTrackedDevice[trackedDeviceIndex] = true;
        }
    }

    /**
     * Helper to get a string from a tracked device property and turn it into a String.
     *
     * @param hmd
     * @param device
     * @return
     */
    private String getTrackedDeviceString(IVRSystem hmd, int device, int prop) {

        IntBuffer propError = GLBuffers.newDirectIntBuffer(new int[]{VR.ETrackedPropertyError.TrackedProp_Success});
        String result = getTrackedDeviceString(hmd, device, prop, propError);
        BufferUtils.destroyDirectBuffer(propError);
        return result;
    }

    /**
     * Helper to get a string from a tracked device property and turn it into a String.
     *
     * @param hmd
     * @param device
     * @return
     */
    private String getTrackedDeviceString(IVRSystem hmd, int device, int prop, IntBuffer propError) {

        int requiredBufferLen = hmd.GetStringTrackedDeviceProperty.apply(device, prop, Pointer.NULL, 0, propError);

        if (requiredBufferLen == 0) {
            return "";
        }

        Pointer stringPointer = new Memory(requiredBufferLen);
        hmd.GetStringTrackedDeviceProperty.apply(device, prop, stringPointer, requiredBufferLen, propError);

        return stringPointer.getString(0);
    }

    private Model findOrLoad(GL4 gl4, String modelName) {

        return StreamEx.of(models).findAny(m -> m.name.equals(modelName)).orElseGet(() -> {
            // load the model if we didn't find one
            IntBuffer errorBuffer = GLBuffers.newDirectIntBuffer(1);
            IVRRenderModels_FnTable renderModels = new IVRRenderModels_FnTable(
                    VR.VR_GetGenericInterface(VR.IVRRenderModels_Version, errorBuffer));

            if (errorBuffer.get(0) != VR.EVRRenderModelError.VRRenderModelError_None) {
                return null;
            }

            int error;
            PointerByReference modelPtr = new PointerByReference();

            while (true) {

                Pointer stringPointer = new Memory(modelName.length() + 1);
                stringPointer.setString(0, modelName);

                error = renderModels.LoadRenderModel_Async.apply(stringPointer, modelPtr);

                if (error != VR.EVRRenderModelError.VRRenderModelError_Loading) {
                    break;
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ModelsRender.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            RenderModel_t renderModel = new RenderModel_t(modelPtr.getValue());

            if (error != VR.EVRRenderModelError.VRRenderModelError_None) {
                System.err.println("Unable to load render model " + modelName + " - "
                        + renderModels.GetRenderModelErrorNameFromEnum.apply(error));
                return null; // move on to the next tracked device
            }

            PointerByReference texturePtr = new PointerByReference();

            while (true) {

                error = renderModels.LoadTexture_Async.apply(renderModel.diffuseTextureId, texturePtr);

                if (error != VR.EVRRenderModelError.VRRenderModelError_Loading) {
                    break;
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ModelsRender.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            RenderModel_TextureMap_t renderModelTexture = new RenderModel_TextureMap_t(texturePtr.getValue());

            if (error != VR.EVRRenderModelError.VRRenderModelError_None) {
                System.err.println("Unable to load render texture id " + renderModel.diffuseTextureId
                        + " for render model " + modelName);
                return null; // move on to the next tracked device
            }

            Model model = new Model(modelName);

            if (!model.init(gl4, renderModel, renderModelTexture)) {
                System.err.println("Unable to create GL model from render model " + modelName);
                model.delete(gl4);
                model = null;
            } else {
                System.out.println("new model: " + modelName);
                int a = renderModels.GetComponentCount.apply(modelName);
                System.out.println("a "+a);
                models.add(model);
            }

            renderModels.FreeRenderModel.apply(renderModel);
            renderModels.FreeTexture.apply(renderModelTexture);

            return model;
        });
    }

    public void render(GL4 gl4, Application app) {

        // ----- Render Model rendering -----                
        gl4.glUseProgram(program.name);

        for (int trackedDevice = 0; trackedDevice < VR.k_unMaxTrackedDeviceCount; trackedDevice++) {

            if (trackedDeviceToRenderModel[trackedDevice] == null || !showTrackedDevice[trackedDevice]) {
                continue;
            }

            TrackedDevicePose_t pose = app.trackedDevicePose[trackedDevice];
            if (pose.bPoseIsValid == 0) {
                continue;
            }

            if (app.hmd.IsInputFocusCapturedByAnotherProcess.apply() == 1 && app.hmd.GetTrackedDeviceClass.apply(trackedDevice)
                    == VR.ETrackedDeviceClass.TrackedDeviceClass_Controller) {
                continue;
            }

            Mat4 matDeviceToTracking = app.mat4DevicePose[trackedDevice];
            app.vp.mul_(matDeviceToTracking).toDfb(app.matBuffer);

            gl4.glUniformMatrix4fv(program.matrixUL, 1, false, app.matBuffer);

            trackedDeviceToRenderModel[trackedDevice].render(gl4);
        }
    }
    
    public void dispose(GL4 gl4) {        
        gl4.glDeleteProgram(program.name);
        models.forEach(model -> model.delete(gl4));
        models.clear();
    }

    private class Program extends glsl.Program {

        public int matrixUL = -1;

        public Program(GL4 gl4) {

            super(gl4, Application.SHADERS_ROOT, SHADERS_SRC);

            matrixUL = gl4.glGetUniformLocation(name, "matrix");

            gl4.glUseProgram(name);
            gl4.glUniform1i(
                    gl4.glGetUniformLocation(name, "diffuse"),
                    Semantic.Sampler.DIFFUSE);
            gl4.glUseProgram(0);
        }
    }
}
