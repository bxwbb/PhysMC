package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class Aero implements ForceGenerator {

    public Matrix3d tensor;
    public Vector3d position;
    public Vector3d windspeed;

    public Aero(Matrix3d tensor, Vector3d position, Vector3d windspeed) {
        this.position = position;
        this.windspeed = windspeed;
        this.tensor = tensor;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        updateForceFromTensor(body, duration, tensor);
    }

    public void updateForceFromTensor(RigidBody body, double duration, Matrix3d tensor) {
        Vector3d velocity = new Vector3d(body.velocity).add(windspeed);
        Vector3d bodyVelocity = body.transformMatrix
                .invert(new org.joml.Matrix4d())
                .transformDirection(velocity);
        Vector3d bodyForce = tensor.transform(bodyVelocity);
        Vector3d force = body.transformMatrix.transformDirection(bodyForce);
        body.addForceAtBodyPoint(force, position);
    }
}
