package com.bxwbb.cd;

import com.bxwbb.math.Matrix4;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class CollisionPrimitive {

    public RigidBody body;
    public Matrix4 offset = new Matrix4();
    public Matrix4 transform = new Matrix4();

    public void calculateInternals() {
        transform = body.transformMatrix.left_mul(offset);
    }

    public Vector3 getAxis(int index) {
        return transform.getAxisVector(index);
    }

    public Matrix4 getTransform() {
        return transform;
    }
}
