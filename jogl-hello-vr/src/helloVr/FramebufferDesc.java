/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import static com.jogamp.opengl.GL.GL_COLOR_ATTACHMENT0;
import static com.jogamp.opengl.GL.GL_DEPTH_ATTACHMENT;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER_COMPLETE;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_RENDERBUFFER;
import static com.jogamp.opengl.GL.GL_RGBA;
import static com.jogamp.opengl.GL.GL_RGBA8;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL2ES2.GL_DEPTH_COMPONENT;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_2D_MULTISAMPLE;
import static com.jogamp.opengl.GL2ES3.GL_TEXTURE_BASE_LEVEL;
import static com.jogamp.opengl.GL2ES3.GL_TEXTURE_MAX_LEVEL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.GLBuffers;
import glm.vec._2.i.Vec2i;
import glutil.BufferUtils;
import java.nio.IntBuffer;

/**
 *
 * @author GBarbieri
 */
public class FramebufferDesc {

    public interface Target {

        public static final int RENDER = 0;
        public static final int RESOLVE = 1;
        public static final int MAX = 2;
    }

    public IntBuffer depthBufferName = GLBuffers.newDirectIntBuffer(1),
            textureName = GLBuffers.newDirectIntBuffer(Target.MAX),
            framebufferName = GLBuffers.newDirectIntBuffer(Target.MAX);

    public FramebufferDesc(GL4 gl4, Vec2i size) {

        gl4.glGenFramebuffers(Target.MAX, framebufferName);
        gl4.glGenRenderbuffers(1, depthBufferName);
        gl4.glGenTextures(Target.MAX, textureName);

        gl4.glBindFramebuffer(GL_FRAMEBUFFER, framebufferName.get(Target.RENDER));

        gl4.glBindRenderbuffer(GL_RENDERBUFFER, depthBufferName.get(0));
        gl4.glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_DEPTH_COMPONENT, size.x, size.y);
        gl4.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBufferName.get(0));

        gl4.glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, textureName.get(Target.RENDER));
        gl4.glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 4, GL_RGBA8, size.x, size.y, true);
        gl4.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE,
                textureName.get(Target.RENDER), 0);

        if (!checkFramebufferStatus(gl4)) {
            return;
        }

        gl4.glBindFramebuffer(GL_FRAMEBUFFER, framebufferName.get(Target.RESOLVE));

        gl4.glBindTexture(GL_TEXTURE_2D, textureName.get(Target.RESOLVE));
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        gl4.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        gl4.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size.x, size.y, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        gl4.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 
                textureName.get(Target.RESOLVE), 0);

        gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);

        if (!checkFramebufferStatus(gl4)) {
            return;
        }

        gl4.glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private boolean checkFramebufferStatus(GL4 gl4) {

        // check FBO status
        int status = gl4.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("Framebuffer incomplete, status " + status);
            return false;
        }
        return true;
    }

    public void dispose(GL4 gl4) {

        gl4.glDeleteFramebuffers(Target.MAX, framebufferName);
        gl4.glDeleteTextures(Target.MAX, textureName);
        gl4.glDeleteRenderbuffers(1, depthBufferName);
        
        BufferUtils.destroyDirectBuffer(framebufferName);
        BufferUtils.destroyDirectBuffer(textureName);
        BufferUtils.destroyDirectBuffer(depthBufferName);
    }
}
