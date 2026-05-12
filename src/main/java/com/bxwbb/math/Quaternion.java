package com.bxwbb.math;

import java.util.Arrays;

public class Quaternion {

    public double r;
    public double i;
    public double j;
    public double k;
    public double[] data = new double[4];

    public Quaternion(double r, double i, double j, double k) {
        this.r = r;
        this.i = i;
        this.j = j;
        this.k = k;
    }

    public void normalize() {
        double d = r * r + i * i + j * j + k * k;
        if (d == 0) {
            r = 1;
            return;
        }
        d = (1.0d) / Math.sqrt(d);
        r *= d;
        i *= d;
        j *= d;
        k *= d;
    }

    public void mul(Quaternion multiplier) {
        double nr = r * multiplier.r - i * multiplier.i - j * multiplier.j - k * multiplier.k;
        double ni = r * multiplier.i + i * multiplier.r + j * multiplier.k - k * multiplier.j;
        double nj = r * multiplier.j + j * multiplier.r + k * multiplier.i - i * multiplier.k;
        double nk = r * multiplier.k + k * multiplier.r + i * multiplier.j - j * multiplier.i;

        r = nr;
        i = ni;
        j = nj;
        k = nk;
    }

    public void rotateByVector(Vector3 vector, double scale) {
        Quaternion q = new Quaternion(0, vector.x * scale, vector.y * scale, vector.z * scale);
        this.mul(q);
    }

    public void addScaledVector(Vector3 vector, double scale) {
        Quaternion q = new Quaternion(0,
                vector.x * scale,
                vector.y * scale,
                vector.z * scale);
        q.mul(this);
        r += q.r * (0.5d);
        i += q.i * (0.5d);
        j += q.j * (0.5d);
        k += q.k * (0.5d);
    }

    @Override
    public String toString() {
        return "Quaternion{" +
                "r=" + r +
                ", i=" + i +
                ", j=" + j +
                ", k=" + k +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
