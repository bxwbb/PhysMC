package com.bxwbb.cd;

import com.bxwbb.math.Vector3;

public class CollisionBox extends CollisionPrimitive {

    public Vector3 halfSize;

    public CollisionBox(Vector3 halfSize) {
        this.halfSize = halfSize;
    }

}
