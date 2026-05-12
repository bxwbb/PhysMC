package com.bxwbb.force;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class Gravity implements ForceGenerator {

    public Vector3 gravity;

    public Gravity(Vector3 gravity) {
        this.gravity = gravity;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        if (!body.hasFiniteMass()) return;
        body.addForce(gravity.mulNew(body.getMass()));
    }
}
