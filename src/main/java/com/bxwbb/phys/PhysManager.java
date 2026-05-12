package com.bxwbb.phys;

import com.bxwbb.phy.World;

public class PhysManager {

    public final World world = new World();

    private long lastUpdateTime = 0;

    private static final PhysManager instance;

    private PhysManager() {}

    static {
        instance = new PhysManager();
    }

    public static PhysManager getInstance() {
        return instance;
    }

    public void tick() {
        long currentTime = System.currentTimeMillis();
        long deltaTime = currentTime - lastUpdateTime;
        world.startFrame();
        world.runPhysics(deltaTime / 1000d);
        lastUpdateTime = System.currentTimeMillis();
    }

}
