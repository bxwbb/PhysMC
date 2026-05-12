package com.bxwbb.phy.particle;

import com.bxwbb.force.particle.ParticleForceGenerator;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleBuoyancy implements ParticleForceGenerator {

    public double maxDepth;
    public double volume;
    public double waterHeight;
    public double liquidDensity;

    public ParticleBuoyancy(double maxDepth, double volume, double waterHeight) {
        this(maxDepth, volume, waterHeight, 1000.0);
    }

    public ParticleBuoyancy(double maxDepth, double volume, double waterHeight, double liquidDensity) {
        this.maxDepth = maxDepth;
        this.volume = volume;
        this.waterHeight = waterHeight;
        this.liquidDensity = liquidDensity;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        double depth = physObject.getPosition().y;
        if (depth >= waterHeight + maxDepth) return;
        Vector3 force = new Vector3();
        if (depth <= waterHeight - maxDepth) {
            force.y = liquidDensity * volume;
            physObject.addForce(force);
            return;
        }
        force.y = liquidDensity * volume * (depth - maxDepth - waterHeight) / 2 * maxDepth;
        physObject.addForce(force);
    }
}
