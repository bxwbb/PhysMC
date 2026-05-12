package com.bxwbb.force;

import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class Aero implements ForceGenerator {

    public Matrix3 tensor;
    public Vector3 position;
    public Vector3 windspeed;

    public Aero(Matrix3 tensor, Vector3 position, Vector3 windspeed) {
        this.position = position;
        this.windspeed = windspeed;
        this.tensor = tensor;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        updateForceFromTensor(body, duration, tensor);
    }

    public void updateForceFromTensor(RigidBody body, double duration, Matrix3 tensor) {
        Vector3 velocity = new Vector3();
        velocity.add(body.velocity);
        velocity.add(windspeed);
        Vector3 bodyVel = body.transformMatrix.transformInverseDirection(velocity);
        Vector3 bodyForce = tensor.transform(bodyVel);
        Vector3 force = body.transformMatrix.transformDirection(bodyForce);
        body.addForceAtBodyPoint(force, position);
    }
}
