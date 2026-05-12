package com.bxwbb.phy.particle;

import com.bxwbb.force.particle.ParticleForceGenerator;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleBungee implements ParticleForceGenerator {

    public PhysObject other;
    public double springConstant;
    public double restLength;

    public ParticleBungee(PhysObject other, double springConstant, double restLength) {
        this.other = other;
        this.springConstant = springConstant;
        this.restLength = restLength;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        Vector3 force = new Vector3();
        force.add(physObject.getPosition());
        force.sub(other.getPosition());
        double magnitude = force.magnitude();
        if (magnitude <= restLength) return;
        magnitude = springConstant * (restLength- magnitude);
        force.normalise();
        force.mul(-magnitude);
        physObject.addForce(force);
    }
}
