package com.bxwbb.force.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.particle.ParticleContact;
import com.bxwbb.phy.PhysObject;

public abstract class ParticleLink {

    public PhysObject[] particle = new PhysObject[2];

    protected double currentLength() {
        Vector3 relativePos = particle[0].getPosition().subNew(particle[1].getPosition());
        return relativePos.magnitude();
    }

    public abstract int fillContact(ParticleContact contact, int limit);

}
