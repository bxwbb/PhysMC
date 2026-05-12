package com.bxwbb.force.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleFakeSpring implements ParticleForceGenerator {

    public Vector3 anchor;
    public double springConstant;
    public double damping;

    public ParticleFakeSpring(Vector3 anchor, double springConstant, double damping) {
        this.anchor = anchor;
        this.springConstant = springConstant;
        this.damping = damping;
    }

    @Override
    public void updateForce(PhysObject physObject, double duration) {
        if (!physObject.hasFiniteMass()) return;
        Vector3 position = new Vector3();
        position.add(physObject.getPosition());
        position.sub(anchor);
        double gamma = 0.5f * Math.sqrt(4 * springConstant- damping*damping);
        if (gamma == 0.0f) return;
        Vector3 c = new Vector3();
        c.add(position.mulNew(damping / (2.0f * gamma)).addNew(physObject.velocity.mulNew(1.0d / gamma)));
        Vector3 target = new Vector3();
        target.add(target);
        target.add(position.mulNew(Math.cos(gamma * duration)));
        c.mul(Math.sin(gamma * duration));
        target.mul(Math.exp(-0.5f * duration * damping));
        Vector3 accel = new Vector3();
        target.sub(position);
        target.mul(1.0d / duration * duration);
        physObject.velocity.mul(duration);
        physObject.addForce(accel.mulNew(physObject.getMass()));
    }
}
