/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import com.sun.jna.Pointer;
import java.nio.IntBuffer;
import vr.CharArray;
import vr.IVRSystem;
import vr.VR;

/**
 *
 * @author GBarbieri
 */
public class ModelsRender {
    
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
            setupRenderModelForTrackedDevice(trackedDevice, app);
        }
    }
    
    /**
     * Create/destroy GL a Render Model for a single tracked device.
     * @param trackedDeviceIndex 
     */
    private void setupRenderModelForTrackedDevice(int trackedDeviceIndex, Application app) {
        
        if(trackedDeviceIndex >= VR.k_unMaxTrackedDeviceCount) {
            return;
        }
        // try to find a model we've already set up
        String renderModelName = getTrackedDeviceString(app.hmd, trackedDeviceIndex, 
                VR.ETrackedDeviceProperty.Prop_RenderModelName_String);
    }
    
    /**
     * Helper to get a string from a tracked device property and turn it into a String.
     * @param hmd
     * @param device
     * @return 
     */
    private String getTrackedDeviceString(IVRSystem hmd, int device, int prop) {
        return getTrackedDeviceString(hmd, device, prop, 
                GLBuffers.newDirectIntBuffer(new int[]{VR.ETrackedPropertyError.TrackedProp_Success}));
    }
    
    /**
     * Helper to get a string from a tracked device property and turn it into a String.
     * @param hmd
     * @param device
     * @return 
     */
    private String getTrackedDeviceString(IVRSystem hmd, int device, int prop, IntBuffer propError) {
        
        int requiredBufferLen = hmd.GetStringTrackedDeviceProperty.apply(device, prop, Pointer.NULL, 0, propError);
        
        if(requiredBufferLen == 0) {
            return "";
        }
        
        
        CharArray.ByReference charArray = new CharArray.ByReference();
//        char[] cs = charArray.toArray(requiredBufferLen);
        
        return null;
    }

}
