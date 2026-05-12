package com.bxwbb.math;

public class Matrix3 {

    public double[] data = new double[9];

    public Matrix3() {
        this(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public Matrix3(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        data[0] = a;
        data[1] = b;
        data[2] = c;
        data[3] = d;
        data[4] = e;
        data[5] = f;
        data[6] = g;
        data[7] = h;
        data[8] = i;
    }

    public Matrix3(Matrix3 other) {
        this(other.data[0], other.data[1], other.data[2], other.data[3], other.data[4], other.data[5], other.data[6], other.data[7], other.data[8]);
    }

    public Vector3 transform(Vector3 vector3) {
        return new Vector3(
                data[0] * vector3.x + data[1] * vector3.y + data[2] * vector3.z,
                data[3] * vector3.x + data[4] * vector3.y + data[5] * vector3.z,
                data[6] * vector3.x + data[7] * vector3.y + data[8] * vector3.z
        );
    }

    public Matrix3 left_mul(Matrix3 o) {
        return new Matrix3(
                data[0] * o.data[0] + data[1] * o.data[3] + data[2] * o.data[6],
                data[0] * o.data[1] + data[1] * o.data[4] + data[2] * o.data[7],
                data[0] * o.data[2] + data[1] * o.data[5] + data[2] * o.data[8],
                data[3] * o.data[0] + data[4] * o.data[3] + data[5] * o.data[6],
                data[3] * o.data[1] + data[4] * o.data[4] + data[5] * o.data[7],
                data[3] * o.data[2] + data[4] * o.data[5] + data[5] * o.data[8],
                data[6] * o.data[0] + data[7] * o.data[3] + data[8] * o.data[6],
                data[6] * o.data[1] + data[7] * o.data[4] + data[8] * o.data[7],
                data[6] * o.data[2] + data[7] * o.data[5] + data[8] * o.data[8]
        );
    }

    public void setInverse(Matrix3 m) {
        double t4 = m.data[0] * m.data[4];
        double t6 = m.data[0] * m.data[5];
        double t8 = m.data[1] * m.data[3];
        double t10 = m.data[2] * m.data[3];
        double t12 = m.data[1] * m.data[6];
        double t14 = m.data[2] * m.data[6];
        double t16 = (t4 * m.data[8] - t6 * m.data[7] - t8 * m.data[8] +
                t10 * m.data[7] + t12 * m.data[5] - t14 * m.data[4]);
        if (t16 == 0.0d) return;
        double t17 = 1 / t16;
        data[0] = (m.data[4] * m.data[8] - m.data[5] * m.data[7]) * t17;
        data[1] = -(m.data[1] * m.data[8] - m.data[2] * m.data[7]) * t17;
        data[2] = (m.data[1] * m.data[5] - m.data[2] * m.data[4]) * t17;
        data[3] = -(m.data[3] * m.data[8] - m.data[5] * m.data[6]) * t17;
        data[4] = (m.data[0] * m.data[8] - t14) * t17;
        data[5] = -(t6 - t10) * t17;
        data[6] = (m.data[3] * m.data[7] - m.data[4] * m.data[6]) * t17;
        data[7] = -(m.data[0] * m.data[7] - t12) * t17;
        data[8] = (t4 - t8) * t17;
    }

    public Matrix3 inverse() {
        Matrix3 result = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
        result.setInverse(this);
        return result;
    }

    public void invert() {
        setInverse(this);
    }

    public void setTranspose(Matrix3 m) {
        data[0] = m.data[0];
        data[1] = m.data[3];
        data[2] = m.data[6];
        data[3] = m.data[1];
        data[4] = m.data[4];
        data[5] = m.data[7];
        data[6] = m.data[2];
        data[7] = m.data[5];
        data[8] = m.data[8];
    }

    public Matrix3 transpose() {
        Matrix3 result = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
        result.setTranspose(this);
        return result;
    }

    public void setOrientation(Quaternion q) {
        data[0] = 1 - (2 * q.j * q.j + 2 * q.k * q.k);
        data[1] = 2 * q.i * q.j + 2 * q.k * q.r;
        data[2] = 2 * q.i * q.k - 2 * q.j * q.r;
        data[3] = 2 * q.i * q.j - 2 * q.k * q.r;
        data[4] = 1 - (2 * q.i * q.i + 2 * q.k * q.k);
        data[5] = 2 * q.j * q.k + 2 * q.i * q.r;
        data[6] = 2 * q.i * q.k + 2 * q.j * q.r;
        data[7] = 2 * q.j * q.k - 2 * q.i * q.r;
        data[8] = 1 - (2 * q.i * q.i + 2 * q.j * q.j);
    }

    @Override
    public String toString() {
        return "Matrix3{" +
                "data=" + data[0] +
                ", " + data[1] +
                ", " + data[2] +
                ", " + data[3] +
                ", " + data[4] +
                ", " + data[5] +
                ", " + data[6] +
                ", " + data[7] +
                ", " + data[8] +
                '}';
    }

    public static Matrix3 linearInterpolate(Matrix3 a, Matrix3 b, double prop) {
        Matrix3 result = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
        for (int i = 0; i < 9; i++) {
            result.data[i] = a.data[i] * (1 - prop) + b.data[i] * prop;
        }
        return result;
    }

    public void setComponents(Vector3 compOne, Vector3 compTwo, Vector3 compThree) {
        data[0] = compOne.x;
        data[1] = compTwo.x;
        data[2] = compThree.x;
        data[3] = compOne.y;
        data[4] = compTwo.y;
        data[5] = compThree.y;
        data[6] = compOne.z;
        data[7] = compTwo.z;
        data[8] = compThree.z;
    }

    public void mul(double scalar) {
        data[0] *= scalar;
        data[1] *= scalar;
        data[2] *= scalar;
        data[3] *= scalar;
        data[4] *= scalar;
        data[5] *= scalar;
        data[6] *= scalar;
        data[7] *= scalar;
        data[8] *= scalar;
    }

    public void mul(Matrix3 o) {
        double t1;
        double t2;
        double t3;

        t1 = data[0] * o.data[0] + data[1] * o.data[3] + data[2] * o.data[6];
        t2 = data[0] * o.data[1] + data[1] * o.data[4] + data[2] * o.data[7];
        t3 = data[0] * o.data[2] + data[1] * o.data[5] + data[2] * o.data[8];
        data[0] = t1;
        data[1] = t2;
        data[2] = t3;

        t1 = data[3] * o.data[0] + data[4] * o.data[3] + data[5] * o.data[6];
        t2 = data[3] * o.data[1] + data[4] * o.data[4] + data[5] * o.data[7];
        t3 = data[3] * o.data[2] + data[4] * o.data[5] + data[5] * o.data[8];
        data[3] = t1;
        data[4] = t2;
        data[5] = t3;

        t1 = data[6] * o.data[0] + data[7] * o.data[3] + data[8] * o.data[6];
        t2 = data[6] * o.data[1] + data[7] * o.data[4] + data[8] * o.data[7];
        t3 = data[6] * o.data[2] + data[7] * o.data[5] + data[8] * o.data[8];
        data[6] = t1;
        data[7] = t2;
        data[8] = t3;
    }

    public void add(Matrix3 o) {
        data[0] += o.data[0];
        data[1] += o.data[1];
        data[2] += o.data[2];
        data[3] += o.data[3];
        data[4] += o.data[4];
        data[5] += o.data[5];
        data[6] += o.data[6];
        data[7] += o.data[7];
        data[8] += o.data[8];
    }

    public void setSkewSymmetric(Vector3 vector) {
        data[0] = data[4] = data[8] = 0;
        data[1] = -vector.z;
        data[2] = vector.y;
        data[3] = vector.z;
        data[5] = -vector.x;
        data[6] = -vector.y;
        data[7] = vector.x;
    }

    public Vector3 transformTranspose(Vector3 vector) {
        return new Vector3(
                vector.x * data[0] + vector.y * data[3] + vector.z * data[6],
                vector.x * data[1] + vector.y * data[4] + vector.z * data[7],
                vector.x * data[2] + vector.y * data[5] + vector.z * data[8]
        );
    }
}
