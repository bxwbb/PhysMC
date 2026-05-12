package com.bxwbb.math;

public class Vector3 {

    public double x;
    public double y;
    public double z;
    public double pad;

    public Vector3() {
        x = 0;
        y = 0;
        z = 0;
    }

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3(Vector3 o) {
        this.x = o.x;
        this.y = o.y;
        this.z = o.z;
    }

    public void invert() {
        x = -x;
        y = -y;
        x = -z;
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double squareMagnitude() {
        return x * x + y * y + z * z;
    }

    public void normalise() {
        double l = magnitude();
        if (l > 0) {
            mul(1 / l);
        }
    }

    public void mul(double d) {
        x *= d;
        y *= d;
        z *= d;
    }

    public Vector3 mulNew(double d) {
        return new Vector3(x * d, y * d, z * d);
    }

    public void add(Vector3 vector3) {
        x += vector3.x;
        y += vector3.y;
        z += vector3.z;
    }

    public Vector3 addNew(Vector3 vector3) {
        return new Vector3(x + vector3.x, y + vector3.y, z + vector3.z);
    }

    public void sub(Vector3 vector3) {
        x -= vector3.x;
        y -= vector3.y;
        z -= vector3.z;
    }

    public Vector3 subNew(Vector3 vector3) {
        return new Vector3(x - vector3.x, y - vector3.y, z - vector3.z);
    }

    public void addScaledVector(Vector3 vector3, double d) {
        x += d * vector3.x;
        y += d * vector3.y;
        z += d * vector3.z;
    }

    public void componentProduct(Vector3 vector3) {
        x *= vector3.x;
        y *= vector3.y;
        z *= vector3.z;
    }

    public Vector3 componentProductNew(Vector3 vector3) {
        return new Vector3(x * vector3.x, y * vector3.y, z * vector3.z);
    }

    public double dot(Vector3 vector3) {
        return x * vector3.x + y * vector3.y + z * vector3.z;
    }

    public void vectorProduct(Vector3 vector3) {
        double rx = y * vector3.z - z * vector3.y;
        double ry = z * vector3.x - x * vector3.z;
        double rz = x * vector3.y - y * vector3.x;
        x = rx;
        y = ry;
        z = rz;
    }

    public Vector3 vectorProductNew(Vector3 vector3) {
        return new Vector3(
                y * vector3.z - z * vector3.y,
                z * vector3.x - x * vector3.z,
                x * vector3.y - y * vector3.x
        );
    }

    public void clear() {
        x = 0;
        y = 0;
        z = 0;
    }

    @Override
    public String toString() {
        return "Vector3{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", pad=" + pad +
                '}';
    }

    public void componentProductUpdate(Vector3 vector) {
        x *= vector.x;
        y *= vector.y;
        z *= vector.z;
    }

    public double get(int i) {
        if (i == 0) return x;
        if (i == 1) return y;
        return z;
    }

    public void set(int i, double value) {
        if (i == 0) x = value;
        if (i == 1) y = value;
        z = value;
    }

    public double scalarProduct(Vector3 vector) {
        return x * vector.x + y * vector.y + z * vector.z;
    }
}
