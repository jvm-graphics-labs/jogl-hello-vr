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
public class ProgramScene extends Program{
    
    private int matrixUL = -1;
    
    public ProgramScene(GL4 gl4, String shadersRoot, String shadersSrc) {
        
        super(gl4, shadersRoot, shadersSrc);
        
        matrixUL = gl4.glGetUniformLocation(name, "matrix");
        
        gl4.glUseProgram(name);
        gl4.glUniform1i(
                gl4.glGetUniformLocation(name, "myTexture"), 
                Semantic.Sampler.MY_TEXTURE);
        gl4.glUseProgram(0);
    }
    
    public int matrixUL() {
        return matrixUL;
    }
}
