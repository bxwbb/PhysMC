package com.bxwbb.phy;

import com.bxwbb.math.Vector3;
import com.bxwbb.util.ObjectUtil;
import org.bukkit.entity.Display;

import java.util.List;

public abstract class PhysObject {

    public Vector3 velocity = new Vector3();
    public Vector3 acceleration = new Vector3();
    public Vector3 forceAccum = new Vector3();
    public double damping = 0.995;
    public double inverseMass = 1 / 5d;

    public static Vector3 gravity = new Vector3(0, 10, 0);

    public abstract List<Display> getAllDisplay();

    public void addWithName(String name) {
        ObjectUtil.addDisplay(name, getAllDisplay().toArray(new Display[0]));
    }

    public abstract Vector3 getPosition();

    public abstract void setPosition(Vector3 location);

    public void integrate(double deltaTime) {
        assert (deltaTime > 0.0);
        setPosition(getPosition().addNew(velocity.mulNew(deltaTime)));
        Vector3 resultingAcc = new Vector3();
        resultingAcc.add(acceleration);
        resultingAcc.addScaledVector(forceAccum, inverseMass);
        velocity.addScaledVector(resultingAcc, deltaTime);
        velocity.mul(Math.pow(damping, deltaTime));
        clearAccumulators();
    }

    public void addForce(Vector3 force) {
        forceAccum.add(force);
    }

    public void clearAccumulators() {
        acceleration.clear();
    }

    public boolean hasFiniteMass() {
        return inverseMass > 0.0;
    }

    public double getMass() {
        return 1.0 / inverseMass;
    }

    public double getInverseMass() {
        return inverseMass;
    }

    public void tick() {}
}
