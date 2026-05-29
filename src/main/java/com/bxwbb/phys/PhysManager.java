package com.bxwbb.phys;

import com.bxwbb.phy.World;

public class PhysManager {

    public final World world = World.getInstance();

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
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }

        long deltaTime = currentTime - lastUpdateTime;
        world.startFrame();
        world.runPhysics(Math.min(deltaTime / 1000d, 0.05d));
        lastUpdateTime = currentTime;
    }

}
