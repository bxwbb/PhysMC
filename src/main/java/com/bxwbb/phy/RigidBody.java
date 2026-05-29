package com.bxwbb.phy;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public abstract class RigidBody extends PhysObject {

    public Vector3d position = new Vector3d();
    public Quaterniond orientation = new Quaterniond();
    public Vector3d velocity = new Vector3d();
    public Vector3d rotation = new Vector3d();
    public Vector3d torqueAccum = new Vector3d();
    public Vector3d lastFrameAcceleration = new Vector3d();
    public Matrix4d transformMatrix = new Matrix4d();
    public Matrix3d inverseInertiaTensor = new Matrix3d().zero();
    public Matrix3d inverseInertiaTensorWorld = new Matrix3d().zero();
    public double angularDamping = 0.9;
    public double linearDamping = 0.9;
    public double motion = 0.995;
    public boolean isAwake = false;
    public boolean canSleep = false;
    public double sleepEpsilon = 0.005;

    public void calculateDerivedData() {
        orientation.normalize();
        transformMatrix = Maths.transformMatrix(position, orientation);
        inverseInertiaTensorWorld = Maths.transformedInertiaTensor(inverseInertiaTensor, orientation);
    }

    public void setInertiaTensor(Matrix3d inertiaTensor) {
        inverseInertiaTensor = new Matrix3d(inertiaTensor).invert();
    }

    public void addForce(Vector3d force) {
        super.addForce(force);
    }

    public void integrate(double duration) {
        lastFrameAcceleration = new Vector3d(acceleration).fma(inverseMass, forceAccum);
        Vector3d angularAcceleration = inverseInertiaTensorWorld.transform(new Vector3d(torqueAccum));

        velocity.fma(duration, lastFrameAcceleration);
        rotation.fma(duration, angularAcceleration);
        velocity.mul(Math.pow(linearDamping, duration));
        rotation.mul(Math.pow(angularDamping, duration));

        position.fma(duration, velocity);
        orientation.integrate(duration, rotation.x, rotation.y, rotation.z).normalize();

        calculateDerivedData();
        clearAccumulators();

        if (canSleep) {
            double currentMotion = velocity.lengthSquared() + rotation.lengthSquared();
            double bias = Math.pow(0.5, duration);
            motion = bias * motion + (1 - bias) * currentMotion;
            if (motion < sleepEpsilon) setAwake(false);
            else if (motion > 10 * sleepEpsilon) motion = 10 * sleepEpsilon;
        }
    }

    public void clearAccumulators() {
        acceleration.zero();
        forceAccum.zero();
        torqueAccum.zero();
    }

    public void addForceAtBodyPoint(Vector3d force, Vector3d point) {
        addForceAtPoint(force, getPointInWorldSpace(point));
    }

    public void addForceAtPoint(Vector3d force, Vector3d point) {
        Vector3d pt = new Vector3d(point).sub(position);
        forceAccum.add(force);
        torqueAccum.add(pt.cross(force, new Vector3d()));
        isAwake = true;
    }

    public Vector3d getPointInWorldSpace(Vector3d point) {
        return transformMatrix.transformPosition(new Vector3d(point));
    }

    public static Vector3d worldToLocal(Vector3d world, Matrix4d transform) {
        return new Matrix4d(transform).invert().transformPosition(new Vector3d(world));
    }

    public static Vector3d localToWorld(Vector3d local, Matrix4d transform) {
        return transform.transformPosition(new Vector3d(local));
    }

    public void addTorque(Vector3d torque) {
        torqueAccum.add(torque);
        isAwake = true;
    }

    public Matrix3d getInverseInertiaTensorWorld() {
        return inverseInertiaTensorWorld;
    }

    public void addVelocity(Vector3d deltaVelocity) {
        velocity.add(deltaVelocity);
    }

    public void addRotation(Vector3d deltaRotation) {
        rotation.add(deltaRotation);
    }

    public void setAwake() {
        setAwake(true);
    }

    public void setAwake(boolean awake) {
        if (awake) {
            isAwake = true;
            motion = sleepEpsilon * 2.0d;
        } else {
            isAwake = false;
            velocity.zero();
            rotation.zero();
        }
    }
}
