package com.bxwbb.cd;

import org.joml.Vector3d;

public class CollisionPlane extends CollisionPrimitive {

    public Vector3d direction;
    public double offset;

    public CollisionPlane(Vector3d direction, double offset) {
        this.direction = direction.normalize(new Vector3d());
        this.offset = offset;
    }

}
