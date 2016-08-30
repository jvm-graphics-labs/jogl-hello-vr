/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import com.jogamp.opengl.GL4;
import glsl.Program;

/**
 *
 * @author GBarbieri
 */
public class ProgramDistortion extends Program {

    public ProgramDistortion(GL4 gl4, String shadersRoot, String shadersSrc) {

        super(gl4, shadersRoot, shadersSrc);

        gl4.glUseProgram(name);
        gl4.glUniform1i(
                gl4.glGetUniformLocation(name, "myTexture"),
                Semantic.Sampler.MY_TEXTURE);
        gl4.glUseProgram(0);
    }
}
