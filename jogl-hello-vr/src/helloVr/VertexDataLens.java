/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloVr;

import glm.vec._2.Vec2;
import java.nio.ByteBuffer;

/**
 *
 * @author GBarbieri
 */
public class VertexDataLens {

    public static final int SIZE = 4 * Vec2.SIZE;
    public static final int OFFSET_POSITION = 0 * Vec2.SIZE;
    public static final int OFFSET_TEX_COORD_RED = 1 * Vec2.SIZE;
    public static final int OFFSET_TEX_COORD_GREEN = 2 * Vec2.SIZE;
    public static final int OFFSET_TEX_COORD_BLUE = 3 * Vec2.SIZE;

    public Vec2 position;
    public Vec2 texCoordRed;
    public Vec2 texCoordGreen;
    public Vec2 texCoordBlue;

    public VertexDataLens() {
    }

    public VertexDataLens(Vec2 position, Vec2 texCoordRed, Vec2 texCoordGreen, Vec2 texCoordBlue) {
        this.position = position;
        this.texCoordRed = texCoordRed;
        this.texCoordGreen = texCoordGreen;
        this.texCoordBlue = texCoordBlue;
    }

    public void toDbb(ByteBuffer bb, int index) {

        position.toDbb(bb, index + OFFSET_POSITION);
        texCoordRed.toDbb(bb, index + OFFSET_TEX_COORD_RED);
        texCoordGreen.toDbb(bb, index + OFFSET_TEX_COORD_GREEN);
        texCoordBlue.toDbb(bb, index + OFFSET_TEX_COORD_BLUE);
    }
}
