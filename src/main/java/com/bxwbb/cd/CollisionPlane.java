package com.bxwbb.cd;

import com.bxwbb.math.Vector3;

public class CollisionPlane extends CollisionPrimitive {

    public Vector3 direction;
    public double offset;

    public CollisionPlane(Vector3 direction, double offset) {
        this.direction = direction;
        this.offset = offset;
    }

}
