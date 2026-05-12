package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ForceRegistry {

    public static class ForceRegistration {
        public RigidBody body;
        public ForceGenerator forceGenerator;

        public ForceRegistration(RigidBody body, ForceGenerator forceGenerator) {
            this.body = body;
            this.forceGenerator = forceGenerator;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ForceRegistration that = (ForceRegistration) o;
            return Objects.equals(body, that.body) && Objects.equals(forceGenerator, that.forceGenerator);
        }

        @Override
        public int hashCode() {
            return Objects.hash(body, forceGenerator);
        }
    }

    public final List<ForceRegistration> registrations = new ArrayList<>();

    public void add(RigidBody body, ForceGenerator fg) {
        registrations.add(new ForceRegistration(body, fg));
    }

    public void remove(RigidBody body, ForceGenerator fg) {
        registrations.remove(new ForceRegistration(body, fg));
    }

    public void clear() {
        registrations.clear();
    }

    public void updateForces(double duration) {
        for (ForceRegistration registration : registrations) {
            registration.forceGenerator.updateForce(registration.body, duration);
        }
    }
}
