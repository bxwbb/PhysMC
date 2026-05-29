package com.bxwbb.phy;

import com.bxwbb.force.ForceRegistry;
import com.bxwbb.obj.Box;
import com.bxwbb.phys.BulletPhysicsEngine;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;

public class World {

    public final List<Box> boxes = new LinkedList<>();
    public final ForceRegistry forceRegistry = new ForceRegistry();

    private final BulletPhysicsEngine physicsEngine = new BulletPhysicsEngine();
    private static final World instance = new World();

    public static World getInstance() {
        return instance;
    }

    public void startFrame() {
    }

    public void runPhysics(double duration) {
        physicsEngine.step(boxes, duration);
    }

    public boolean applyImpulse(Display display, Vector impulse, Location hitLocation) {
        return physicsEngine.applyImpulse(display, impulse, hitLocation);
    }
}
