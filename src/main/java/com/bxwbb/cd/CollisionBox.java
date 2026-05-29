package com.bxwbb.cd;

import org.joml.Vector3d;

public class CollisionBox extends CollisionPrimitive {

    public Vector3d halfSize;

    public CollisionBox(Vector3d halfSize) {
        this.halfSize = halfSize;
    }

}
