package com.bxwbb.phy;

import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Matrix4;
import com.bxwbb.math.Quaternion;
import com.bxwbb.math.Vector3;

public abstract class RigidBody extends PhysObject {

    public Vector3 position = new Vector3();
    public Quaternion orientation = new Quaternion(0, 0, 0, 1);
    public Vector3 velocity = new Vector3();
    public Vector3 rotation = new Vector3();
    public Vector3 torqueAccum = new Vector3();
    public Vector3 lastFrameAcceleration = new Vector3();
    public Matrix4 transformMatrix = new Matrix4();
    public Matrix3 inverseInertiaTensor = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public Matrix3 inverseInertiaTensorWorld = new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public double angularDamping = 0.9;
    public double linearDamping = 0.9;
    public double motion = 0.995;
    public boolean isAwake = false;
    public boolean canSleep = false;
    public double sleepEpsilon = 0.005;

    public void calculateDerivedData() {
        orientation.normalize();
        _calculateTransformMatrix(transformMatrix, position, orientation);
        _transformInertiaTensor(inverseInertiaTensorWorld,
                orientation,
                inverseInertiaTensor,
                transformMatrix);
    }

    public void _calculateTransformMatrix(Matrix4 transformMatrix,
                                          Vector3 position,
                                          Quaternion orientation) {
        transformMatrix.data[0] = 1 - 2 * orientation.j * orientation.j -
                2 * orientation.k * orientation.k;
        transformMatrix.data[1] = 2 * orientation.i * orientation.j -
                2 * orientation.r * orientation.k;
        transformMatrix.data[2] = 2 * orientation.i * orientation.k +
                2 * orientation.r * orientation.j;
        transformMatrix.data[3] = position.x;
        transformMatrix.data[4] = 2 * orientation.i * orientation.j +
                2 * orientation.r * orientation.k;
        transformMatrix.data[5] = 1 - 2 * orientation.i * orientation.i -
                2 * orientation.k * orientation.k;
        transformMatrix.data[6] = 2 * orientation.j * orientation.k -
                2 * orientation.r * orientation.i;
        transformMatrix.data[7] = position.y;
        transformMatrix.data[8] = 2 * orientation.i * orientation.k -
                2 * orientation.r * orientation.j;
        transformMatrix.data[9] = 2 * orientation.j * orientation.k +
                2 * orientation.r * orientation.i;
        transformMatrix.data[10] = 1 - 2 * orientation.i * orientation.i -
                2 * orientation.j * orientation.j;
        transformMatrix.data[11] = position.z;
    }

    public void setInertiaTensor(Matrix3 inertiaTensor) {
        inverseInertiaTensor.setInverse(inertiaTensor);
    }

    public static void _transformInertiaTensor(Matrix3 iitWorld,
                                               Quaternion q,
                                               Matrix3 iitBody,
                                               Matrix4 rotmat) {
        double t4 = rotmat.data[0] * iitBody.data[0] +
                rotmat.data[1] * iitBody.data[3] +
                rotmat.data[2] * iitBody.data[6];
        double t9 = rotmat.data[0] * iitBody.data[1] +
                rotmat.data[1] * iitBody.data[4] +
                rotmat.data[2] * iitBody.data[7];
        double t14 = rotmat.data[0] * iitBody.data[2] +
                rotmat.data[1] * iitBody.data[5] +
                rotmat.data[2] * iitBody.data[8];
        double t28 = rotmat.data[4] * iitBody.data[0] +
                rotmat.data[5] * iitBody.data[3] +
                rotmat.data[6] * iitBody.data[6];
        double t33 = rotmat.data[4] * iitBody.data[1] +
                rotmat.data[5] * iitBody.data[4] +
                rotmat.data[6] * iitBody.data[7];
        double t38 = rotmat.data[4] * iitBody.data[2] +
                rotmat.data[5] * iitBody.data[5] +
                rotmat.data[6] * iitBody.data[8];
        double t52 = rotmat.data[8] * iitBody.data[0] +
                rotmat.data[9] * iitBody.data[3] +
                rotmat.data[10] * iitBody.data[6];
        double t57 = rotmat.data[8] * iitBody.data[1] +
                rotmat.data[9] * iitBody.data[4] +
                rotmat.data[10] * iitBody.data[7];
        double t62 = rotmat.data[8] * iitBody.data[2] +
                rotmat.data[9] * iitBody.data[5] +
                rotmat.data[10] * iitBody.data[8];
        iitWorld.data[0] = t4 * rotmat.data[0] +
                t9 * rotmat.data[1] +
                t14 * rotmat.data[2];
        iitWorld.data[1] = t4 * rotmat.data[4] +
                t9 * rotmat.data[5] +
                t14 * rotmat.data[6];
        iitWorld.data[2] = t4 * rotmat.data[8] +
                t9 * rotmat.data[9] +
                t14 * rotmat.data[10];
        iitWorld.data[3] = t28 * rotmat.data[0] +
                t33 * rotmat.data[1] +
                t38 * rotmat.data[2];
        iitWorld.data[4] = t28 * rotmat.data[4] +
                t33 * rotmat.data[5] +
                t38 * rotmat.data[6];
        iitWorld.data[5] = t28 * rotmat.data[8] +
                t33 * rotmat.data[9] +
                t38 * rotmat.data[10];
        iitWorld.data[6] = t52 * rotmat.data[0] +
                t57 * rotmat.data[1] +
                t62 * rotmat.data[2];
        iitWorld.data[7] = t52 * rotmat.data[4] +
                t57 * rotmat.data[5] +
                t62 * rotmat.data[6];
        iitWorld.data[8] = t52 * rotmat.data[8] +
                t57 * rotmat.data[9] +
                t62 * rotmat.data[10];
    }

    public void addForce(Vector3 force) {
        super.addForce(force);
    }

    public void integrate(double duration) {
        lastFrameAcceleration = new Vector3();
        lastFrameAcceleration.add(acceleration);
        lastFrameAcceleration.addScaledVector(forceAccum, inverseMass);
        Vector3 angularAcceleration = inverseInertiaTensorWorld.transform(torqueAccum);
        velocity.addScaledVector(lastFrameAcceleration, duration);
        rotation.addScaledVector(angularAcceleration, duration);
        velocity.mul(Math.pow(linearDamping, duration));
        rotation.mul(Math.pow(angularDamping, duration));
        position.addScaledVector(velocity, duration);
        Quaternion q = new Quaternion(rotation.x, rotation.y, rotation.z, 0);
        q.mul(orientation);
        orientation.i += q.i * 0.5 * duration;
        orientation.j += q.j * 0.5 * duration;
        orientation.k += q.k * 0.5 * duration;
        orientation.r += q.r * 0.5 * duration;
        calculateDerivedData();
        clearAccumulators();
        if (canSleep) {
            double currentMotion = velocity.scalarProduct(velocity) + rotation.scalarProduct(rotation);
            double bias = Math.pow(0.5, duration);
            motion = bias * motion + (1 - bias) * currentMotion;
            if (motion < sleepEpsilon) setAwake(false);
            else if (motion > 10 * sleepEpsilon) motion = 10 * sleepEpsilon;
        }
    }

    public void clearAccumulators() {
        acceleration.clear();
        forceAccum.clear();
        torqueAccum.clear();
    }

    public void addForceAtBodyPoint(Vector3 force, Vector3 point) {
        Vector3 pt = getPointInWorldSpace(point);
        addForceAtPoint(force, pt);
    }

    public void addForceAtPoint(Vector3 force, Vector3 point) {
        Vector3 pt = new Vector3();
        pt.add(point);
        pt.sub(position);

        forceAccum.add(force);
        torqueAccum.add(pt.vectorProductNew(force));

        isAwake = true;
    }

    public Vector3 getPointInWorldSpace(Vector3 point) {
        return transformMatrix.transform(point);
    }

    public static Vector3 worldToLocal(Vector3 world, Matrix4 transform) {
        Matrix4 inverseTransform = new Matrix4();
        inverseTransform.setInverse(transform);
        return inverseTransform.transform(world);
    }

    public static Vector3 localToWorld(Vector3 local, Matrix4 transform) {
        return transform.transform(local);
    }

    public void addTorque(Vector3 torque) {
        torqueAccum.add(torque);
        isAwake = true;
    }

    public Matrix3 getInverseInertiaTensorWorld() {
        return inverseInertiaTensorWorld;
    }

    public void addVelocity(Vector3 deltaVelocity) {
        velocity.add(deltaVelocity);
    }

    public void addRotation(Vector3 deltaRotation) {
        rotation.add(deltaRotation);
    }

    public void setAwake() {
        setAwake(true);
    }

    public void setAwake(boolean awake) {
        if (awake) {
            isAwake = true;
            motion = sleepEpsilon * 2.0f;
        } else {
            isAwake = false;
            velocity.clear();
            rotation.clear();
        }
    }
}