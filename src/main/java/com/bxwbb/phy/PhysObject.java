package com.bxwbb.phy;

import com.bxwbb.util.ObjectUtil;
import org.bukkit.entity.Display;
import org.joml.Vector3d;

import java.util.List;

public abstract class PhysObject {

    public Vector3d velocity = new Vector3d();
    public Vector3d acceleration = new Vector3d();
    public Vector3d forceAccum = new Vector3d();
    public double damping = 0.995;
    public double inverseMass = 1 / 5d;

    public static Vector3d gravity = new Vector3d(0, 10, 0);

    public abstract List<Display> getAllDisplay();

    public void addWithName(String name) {
        ObjectUtil.addDisplay(name, getAllDisplay().toArray(new Display[0]));
    }

    public void addWithName(String group, String name) {
        ObjectUtil.addDisplay(group, name, getAllDisplay().toArray(new Display[0]));
    }

    public abstract Vector3d getPosition();

    public abstract void setPosition(Vector3d location);

    public void integrate(double deltaTime) {
        assert (deltaTime > 0.0);
        setPosition(new Vector3d(getPosition()).fma(deltaTime, velocity));
        Vector3d resultingAcc = new Vector3d(acceleration).fma(inverseMass, forceAccum);
        velocity.fma(deltaTime, resultingAcc);
        velocity.mul(Math.pow(damping, deltaTime));
        clearAccumulators();
    }

    public void addForce(Vector3d force) {
        forceAccum.add(force);
    }

    public void clearAccumulators() {
        acceleration.zero();
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
