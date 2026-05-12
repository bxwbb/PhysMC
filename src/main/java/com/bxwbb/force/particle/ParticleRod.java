package com.bxwbb.force.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.particle.ParticleContact;

public class ParticleRod extends ParticleLink {

    public double length;

    @Override
    public int fillContact(ParticleContact contact, int limit) {
        double currentLen = currentLength();
        if (currentLen == length) {
            return 0;
        }
        contact.particle[0] = particle[0];
        contact.particle[1] = particle[1];
        Vector3 normal = particle[1].getPosition().subNew(particle[0].getPosition());
        normal.normalise();
        if (currentLen > length) {
            contact.contactNormal = normal;
            contact.penetration = currentLen - length;
        } else {
            contact.contactNormal = normal.mulNew(-1);
            contact.penetration = length - currentLen;
        }
        contact.restitution = 0;
        return 1;
    }
}
