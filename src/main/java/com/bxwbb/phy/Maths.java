package com.bxwbb.phy;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class Maths {

    private Maths() {
    }

    public static Vector3d componentProduct(Vector3d a, Vector3d b) {
        return new Vector3d(a.x * b.x, a.y * b.y, a.z * b.z);
    }

    public static Matrix3d skewSymmetric(Vector3d vector) {
        return new Matrix3d(
                0, vector.z, -vector.y,
                -vector.z, 0, vector.x,
                vector.y, -vector.x, 0
        );
    }

    public static Matrix3d linearInterpolate(Matrix3d a, Matrix3d b, double proportion) {
        Matrix3d result = new Matrix3d();
        result.m00(a.m00() * (1 - proportion) + b.m00() * proportion);
        result.m01(a.m01() * (1 - proportion) + b.m01() * proportion);
        result.m02(a.m02() * (1 - proportion) + b.m02() * proportion);
        result.m10(a.m10() * (1 - proportion) + b.m10() * proportion);
        result.m11(a.m11() * (1 - proportion) + b.m11() * proportion);
        result.m12(a.m12() * (1 - proportion) + b.m12() * proportion);
        result.m20(a.m20() * (1 - proportion) + b.m20() * proportion);
        result.m21(a.m21() * (1 - proportion) + b.m21() * proportion);
        result.m22(a.m22() * (1 - proportion) + b.m22() * proportion);
        return result;
    }

    public static Matrix4d transformMatrix(Vector3d position, Quaterniond orientation) {
        return new Matrix4d()
                .translationRotate(position, orientation);
    }

    public static Matrix3d transformedInertiaTensor(Matrix3d inverseInertiaTensor, Quaterniond orientation) {
        Matrix3d rotation = new Matrix3d().rotation(orientation);
        return new Matrix3d(rotation)
                .mul(new Matrix3d(inverseInertiaTensor))
                .mul(new Matrix3d(rotation).transpose());
    }
}
