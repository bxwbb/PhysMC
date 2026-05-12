package com.bxwbb.force.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleSpring implements ParticleForceGenerator {

    public Vector3 anchor;
    public double springConstant;
    public double restLength;

    public ParticleSpring(Vector3 anchor, double springConstant, double restLength) {
        this.anchor = anchor;
        this.springConstant = springConstant;
        this.restLength = restLength;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        Vector3 force = new Vector3();
        force.add(physObject.getPosition());
        force.sub(anchor);
        double magnitude = force.magnitude();
        magnitude = Math.abs(magnitude - restLength);
        force.mul(springConstant);
        force.normalise();
        force.mul(-magnitude);
        physObject.addForce(force);
    }
}
