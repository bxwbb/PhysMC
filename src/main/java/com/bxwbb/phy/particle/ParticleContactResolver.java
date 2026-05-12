package com.bxwbb.phy.particle;

import java.util.List;

public class ParticleContactResolver {

    public int iterations;
    public int iterationsUsed;

    public ParticleContactResolver(int iterations) {
        this.iterations = iterations;
    }

    protected void setIterations(int iterations) {
        this.iterations = iterations;
    }

    protected void resolveContacts(List<ParticleContact> contactArray, int numContacts, double duration) {
        iterationsUsed = 0;
        while (iterationsUsed < iterations) {
            double max = 0;
            int maxIndex = numContacts;
            for (int i = 0; i < numContacts; i++) {
                double sepVel = contactArray.get(i).calculateSeparatingVelocity();
                if (sepVel < max) {
                    max = sepVel;
                    maxIndex = i;
                }
            }
            contactArray.get(maxIndex).resolve(duration);
            iterationsUsed++;
        }
    }

}
