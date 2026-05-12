package com.bxwbb.force;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class Spring implements ForceGenerator {

    public Vector3 connectionPoint;
    public Vector3 otherConnectionPoint;
    public RigidBody other;
    public double springConstant;
    public double restLength;

    public Spring(Vector3 connectionPoint, Vector3 otherConnectionPoint, RigidBody other, double springConstant, double restLength) {
        this.connectionPoint = connectionPoint;
        this.otherConnectionPoint = otherConnectionPoint;
        this.other = other;
        this.springConstant = springConstant;
        this.restLength = restLength;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        Vector3 lws = body.getPointInWorldSpace(connectionPoint);
        Vector3 ows = other.getPointInWorldSpace(otherConnectionPoint);
        Vector3 force = new Vector3();
        force.add(lws);
        force.sub(ows);
        double magnitude = force.magnitude();
        magnitude = Math.abs(magnitude- restLength);
        magnitude *= springConstant;
        force.normalise();
        force.mul(-magnitude);
        body.addForceAtPoint(force, lws);
    }
}
