/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import glutil.BufferUtils;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import one.util.streamex.StreamEx;
import vr.IVRRenderModels_FnTable;
import vr.IVRSystem;
import vr.RenderModel_TextureMap_t;
import vr.RenderModel_t;
import vr.VR;

/**
 *
 * @author GBarbieri
 */
public class ModelsRender {

    private List<Model> models = new ArrayList<>();

    /**
     * Create/destroy GL Render Models.
     *
     * @param gl4
     * @param app
     */
    public void setupRenderModels(GL4 gl4, Application app) {

        if (app.hmd == null) {
            return;
        }

        for (int trackedDevice = VR.k_unTrackedDeviceIndex_Hmd + 1; trackedDevice < VR.k_unMaxTrackedDeviceCount;
                trackedDevice++) {

            if (app.hmd.IsTrackedDeviceConnected.apply(trackedDevice) == 0) {
                continue;
            }
            setupRenderModelForTrackedDevice(gl4, trackedDevice, app);
        }
    }

    /**
     * Create/destroy GL a Render Model for a single tracked device.
     *
     * @param trackedDeviceIndex
     */
    private void setupRenderModelForTrackedDevice(GL4 gl4, int trackedDeviceIndex, Application app) {

        if (trackedDeviceIndex >= VR.k_unMaxTrackedDeviceCount) {
            return;
        }
        // try to find a model we've already set up
        String renderModelName = getTrackedDeviceString(app.hmd, trackedDeviceIndex,
                VR.ETrackedDeviceProperty.Prop_RenderModelName_String);

        Model model = findOrLoad(gl4, renderModelName, app.hmd);
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

        int requiredBufferLen = hmd.GetStringTrackedDeviceProperty.apply(device, prop, null, 0, propError);

        if (requiredBufferLen == 0) {
            return "";
        }

        Pointer stringPointer = new Memory(requiredBufferLen);
        hmd.GetStringTrackedDeviceProperty.apply(device, prop, stringPointer, requiredBufferLen, propError);
        
        return stringPointer.getString(0);
    }

    private Model findOrLoad(GL4 gl4, String modelName, IVRSystem hmd) {

//        return StreamEx.of(models).findAny(m -> m.name.equals(modelName)).orElseGet(() -> {
        // load the model if we didn't find one
        RenderModel_t.ByReference modelReference = new RenderModel_t.ByReference();

        IntBuffer errorBuffer = GLBuffers.newDirectIntBuffer(1);
        IVRRenderModels_FnTable renderModels = new IVRRenderModels_FnTable(
                VR.VR_GetGenericInterface(VR.IVRRenderModels_Version, errorBuffer));
        renderModels.read();

        if (errorBuffer.get(0) != VR.EVRRenderModelError.VRRenderModelError_None) {
            return null;
        }

        int error;
        byte[] bs = Native.toByteArray(modelName);
            PointerByReference modelReference_ = new PointerByReference();

        while (true) {

            Pointer stringPointer = new Memory(modelName.length());
            
            error = renderModels.LoadRenderModel_Async.apply(stringPointer, modelReference_);
            
            if (error != VR.EVRRenderModelError.VRRenderModelError_Loading) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                Logger.getLogger(ModelsRender.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
            modelReference_.
            RenderModel_t m = new RenderModel_t(modelReference_.getValue());
            m.read();
            

        if (error != VR.EVRRenderModelError.VRRenderModelError_None) {
            System.err.println("Unable to load render model " + modelName + " - "
                    + renderModels.GetRenderModelErrorNameFromEnum.apply(error));
            return null; // move on to the next tracked device
        }

        RenderModel_TextureMap_t.ByReference textureReference = new RenderModel_TextureMap_t.ByReference();

        while (true) {

            error = renderModels.LoadTexture_Async.apply(modelReference.diffuseTextureId, textureReference);

            if (error != VR.EVRRenderModelError.VRRenderModelError_Loading) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                Logger.getLogger(ModelsRender.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (error != VR.EVRRenderModelError.VRRenderModelError_None) {
            System.err.println("Unable to load render texture id " + modelReference.diffuseTextureId
                    + " for render model " + modelName);
            return null; // move on to the next tracked device
        }

        Model model = new Model(modelName);

        if(!model.init(gl4, modelReference, textureReference)) {
            System.err.println("Unable to create GL model from render model "+modelName);            
        }else {
            models.add(model);
        }
        
//        models.add(e)



        return null;
//        });

    }

}
