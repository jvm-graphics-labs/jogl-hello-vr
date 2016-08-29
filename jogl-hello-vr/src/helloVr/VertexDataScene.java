/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import glm.vec._2.Vec2;
import glm.vec._3.Vec3;
import glm.vec._3.funcCommon;
import java.nio.ByteBuffer;

/**
 *
 * @author GBarbieri
 */
public class VertexDataScene {
    
    public static final int SIZE = Vec3.SIZE + Vec2.SIZE;
    public static final int OFFSET_POSITION = 0;
    public static final int OFFSET_TEX_COORD = Vec3.SIZE;

    public Vec3 position;
    public Vec2 texCoord;

    public VertexDataScene() {
    }

    public VertexDataScene(Vec3 position, Vec2 texCoord) {
        this.position = position;
        this.texCoord = texCoord;
    }

    public void toDbb(ByteBuffer bb, int index) {

        position.toDbb(bb, index + OFFSET_POSITION);
        texCoord.toDbb(bb, index + OFFSET_TEX_COORD);
    }
}
