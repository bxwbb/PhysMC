package com.bxwbb.phy.particle;

import com.bxwbb.force.particle.ParticleForceRegistry;
import com.bxwbb.phy.PhysObject;

import java.util.LinkedList;
import java.util.List;

public class ParticleWorld {

    public List<PhysObject> physObjects = new LinkedList<>();
    public List<ParticleContactGenerator> particleContactGenerators = new LinkedList<>();
    public List<ParticleContact> contacts = new LinkedList<>();
    public ParticleForceRegistry registry = new ParticleForceRegistry();
    public ParticleContactResolver resolver;
    public int maxContacts;
    public int iterations = 0;

    public ParticleWorld(int maxContacts) {
        this.maxContacts = maxContacts;
        resolver = new ParticleContactResolver(iterations);
    }

    public ParticleWorld(int maxContacts, int iterations) {
        this.maxContacts = maxContacts;
        this.iterations = iterations;
        resolver = new ParticleContactResolver(iterations);
    }

    public void startFrame() {
        for (PhysObject physObject : physObjects) {
            physObject.clearAccumulators();
        }
    }

    public int generateContacts() {
        int limit = maxContacts;
        int contactIndex = 0;
        for (ParticleContactGenerator reg : particleContactGenerators) {
            if (limit <= 0) break;
            int used = reg.addContact(contacts, contactIndex, limit);
            limit -= used;
            contactIndex += used;
        }
        return maxContacts - limit;
    }

    public void integrate(double duration) {
        for (PhysObject physObject : physObjects) {
            physObject.integrate(duration);
        }
    }

    public void runPhysics(double duration) {
        registry.updateForces(duration);
        integrate(duration);
        int usedContacts = generateContacts();
        if (iterations == 0) resolver.setIterations(usedContacts * 2);
        resolver.resolveContacts(contacts, usedContacts, duration);
    }
}
