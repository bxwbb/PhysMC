package com.bxwbb.phy.particle;

import com.bxwbb.force.particle.ParticleForceGenerator;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleDrag implements ParticleForceGenerator {

    public double k1;
    public double k2;

    public ParticleDrag(double k1, double k2) {
        this.k1 = k1;
        this.k2 = k2;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        Vector3 force = new Vector3();
        force.add(physObject.velocity);

        double dragCoefficient = force.magnitude();
        dragCoefficient = k1 * dragCoefficient + k2 * dragCoefficient * dragCoefficient;
        force.normalise();
        force.mul(-dragCoefficient);
        physObject.addForce(force);
    }
}
