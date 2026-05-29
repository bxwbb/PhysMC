package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;
import org.joml.Vector3d;

public class Spring implements ForceGenerator {

    public Vector3d connectionPoint;
    public Vector3d otherConnectionPoint;
    public RigidBody other;
    public double springConstant;
    public double restLength;

    public Spring(Vector3d connectionPoint, Vector3d otherConnectionPoint, RigidBody other, double springConstant, double restLength) {
        this.connectionPoint = connectionPoint;
        this.otherConnectionPoint = otherConnectionPoint;
        this.other = other;
        this.springConstant = springConstant;
        this.restLength = restLength;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        Vector3d localWorld = body.getPointInWorldSpace(connectionPoint);
        Vector3d otherWorld = other.getPointInWorldSpace(otherConnectionPoint);
        Vector3d force = new Vector3d(localWorld).sub(otherWorld);
        double magnitude = Math.abs(force.length() - restLength) * springConstant;
        force.normalize().mul(-magnitude);
        body.addForceAtPoint(force, localWorld);
    }
}
