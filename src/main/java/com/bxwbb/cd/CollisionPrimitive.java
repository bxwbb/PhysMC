package com.bxwbb.cd;

import com.bxwbb.phy.RigidBody;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public class CollisionPrimitive {

    public RigidBody body;
    public Matrix4d offset = new Matrix4d();
    public Matrix4d transform = new Matrix4d();

    public void calculateInternals() {
        transform = new Matrix4d(body.transformMatrix).mul(offset);
    }

    public Vector3d getAxis(int index) {
        if (index == 0) return new Vector3d(transform.m00(), transform.m01(), transform.m02());
        if (index == 1) return new Vector3d(transform.m10(), transform.m11(), transform.m12());
        if (index == 2) return new Vector3d(transform.m20(), transform.m21(), transform.m22());
        return new Vector3d(transform.m30(), transform.m31(), transform.m32());
    }

    public Matrix4d getTransform() {
        return transform;
    }
}
