package com.bxwbb.phy.particle;

import com.bxwbb.force.particle.ParticleLink;
import com.bxwbb.math.Vector3;

public class ParticleCable extends ParticleLink {

    public double maxLength;
    public double restitution;

    @Override
    public int fillContact(ParticleContact contact, int limit) {
        double length = currentLength();
        if (length < maxLength)
        {
            return 0;
        }
        contact.particle[0] = particle[0];
        contact.particle[1] = particle[1];
        Vector3 normal = particle[1].getPosition().subNew(particle[0].getPosition());
        normal.normalise();
        contact.contactNormal = normal;
        contact.penetration = length-maxLength;
        contact.restitution = restitution;
        return 1;
    }
}
