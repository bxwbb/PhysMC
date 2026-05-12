package com.bxwbb.phy.particle;

import com.bxwbb.math.Vector3;
import com.bxwbb.phy.PhysObject;

public class ParticleContact {

    public PhysObject[] particle = new PhysObject[2];
    public double restitution;
    public Vector3 contactNormal;
    public double penetration;

    protected void resolve(double duration) {
        resolveVelocity(duration);
        resolveInterpenetration(duration);
    }

    protected double calculateSeparatingVelocity() {
        Vector3 relativeVelocity = particle[0].velocity;
        if (particle[1] != null) relativeVelocity.sub(particle[1].velocity);
        return relativeVelocity.dot(contactNormal);
    }

    private void resolveVelocity(double duration) {
        double separatingVelocity = calculateSeparatingVelocity();
        if (separatingVelocity > 0) {
            return;
        }
        double newSepVelocity = -separatingVelocity * restitution;
        Vector3 accCausedVelocity = new Vector3();
        accCausedVelocity.add(particle[0].acceleration);
        if (particle[1] != null) accCausedVelocity.sub(particle[1].acceleration);
        double accCausedSepVelocity = accCausedVelocity.dot(contactNormal.mulNew(duration));
        if (accCausedSepVelocity < 0) {
            newSepVelocity += restitution * accCausedSepVelocity;
            if (newSepVelocity < 0) newSepVelocity = 0;
        }
        double deltaVelocity = newSepVelocity - separatingVelocity;
        double totalInverseMass = particle[0].inverseMass;
        if (particle[1] != null) totalInverseMass += particle[1].inverseMass;
        if (totalInverseMass <= 0) return;
        double impulse = deltaVelocity / totalInverseMass;
        Vector3 impulsePerIMass = contactNormal.mulNew(impulse);
        particle[0].velocity.add(impulsePerIMass.mulNew(particle[0].inverseMass));
        if (particle[1] != null) {
            particle[1].velocity.add(impulsePerIMass.mulNew(-particle[1].inverseMass));
        }
    }

    private void resolveInterpenetration(double duration) {
        if (penetration <= 0) return;
        double totalInverseMass = particle[0].inverseMass;
        if (particle[1] != null) totalInverseMass += particle[1].inverseMass;
        if (totalInverseMass <= 0) return;
        Vector3 movePerIMass = contactNormal.mulNew(-penetration / totalInverseMass);
        particle[0].setPosition(particle[0].getPosition().addNew(movePerIMass.mulNew(particle[0].inverseMass)));
        if (particle[1] != null) {
            particle[1].setPosition(particle[1].getPosition().addNew(movePerIMass.mulNew(particle[1].inverseMass)));
        }
    }
}
