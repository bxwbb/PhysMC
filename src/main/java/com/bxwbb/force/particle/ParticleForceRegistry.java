package com.bxwbb.force.particle;

import com.bxwbb.phy.PhysObject;

import java.util.ArrayList;
import java.util.List;

public class ParticleForceRegistry {

    protected record ParticleForceRegistration(PhysObject particle, ParticleForceGenerator fg) {

    }

    protected List<ParticleForceRegistration> registrations = new ArrayList<>();

    public void add(PhysObject particle, ParticleForceGenerator fg) {
        registrations.add(new ParticleForceRegistration(particle, fg));
    }

    public void remove(PhysObject particle, ParticleForceGenerator fg) {
        registrations.remove(new ParticleForceRegistration(particle, fg));
    }

    public void clear() {
        registrations.clear();
    }

    public void updateForces(double duration) {
        for (ParticleForceRegistration registration : registrations) {
            registration.fg.updateForce(registration.particle, duration);
        }
    }

}
