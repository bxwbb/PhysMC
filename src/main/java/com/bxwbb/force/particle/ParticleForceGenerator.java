package com.bxwbb.force.particle;

import com.bxwbb.phy.PhysObject;

public interface ParticleForceGenerator {

    void updateForce(PhysObject physObject, double duration);

}
