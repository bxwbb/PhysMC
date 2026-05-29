package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;
import org.joml.Vector3d;

public class Buoyancy implements ForceGenerator {

    public double maxDepth;
    public double volume;
    public double waterHeight;
    public double liquidDensity = 1000d;
    public Vector3d centerOfBuoyancy;

    public Buoyancy(Vector3d centerOfBuoyancy, double maxDepth, double volume, double waterHeight) {
        this.maxDepth = maxDepth;
        this.volume = volume;
        this.waterHeight = waterHeight;
        this.centerOfBuoyancy = centerOfBuoyancy;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        Vector3d pointInWorld = body.getPointInWorldSpace(centerOfBuoyancy);
        double depth = pointInWorld.y;
        if (depth >= waterHeight + maxDepth) return;

        Vector3d force = new Vector3d();
        if (depth <= waterHeight - maxDepth) {
            force.y = liquidDensity * volume;
            body.addForceAtBodyPoint(force, centerOfBuoyancy);
            return;
        }

        force.y = liquidDensity * volume * (depth - maxDepth - waterHeight) / 2 * maxDepth;
        body.addForceAtBodyPoint(force, centerOfBuoyancy);
    }
}
