package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;
import org.joml.Vector3d;

public class Gravity implements ForceGenerator {

    public Vector3d gravity;

    public Gravity(Vector3d gravity) {
        this.gravity = gravity;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        if (!body.hasFiniteMass()) return;
        body.addForce(new Vector3d(gravity).mul(body.getMass()));
    }
}
