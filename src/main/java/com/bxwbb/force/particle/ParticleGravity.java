package com.bxwbb.force.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleGravity implements ParticleForceGenerator {

    public Vector3 gravity;

    public ParticleGravity(Vector3 gravity) {
        this.gravity = gravity;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        if (!physObject.hasFiniteMass()) return;

        physObject.addForce(gravity.mulNew(physObject.getMass()));
    }
}
