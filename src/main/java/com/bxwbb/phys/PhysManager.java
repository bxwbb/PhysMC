package com.bxwbb.phys;

import com.bxwbb.phy.World;

public class PhysManager {

    private static final double TICK_DURATION_SECONDS = 0.05d;

    public final World world = World.getInstance();

    private long lastUpdateTime = 0;
    private boolean paused = false;

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
        if (paused) {
            lastUpdateTime = currentTime;
            return;
        }

        long deltaTime = currentTime - lastUpdateTime;
        world.startFrame();
        world.runPhysics(Math.min(deltaTime / 1000d, TICK_DURATION_SECONDS));
        lastUpdateTime = currentTime;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        lastUpdateTime = System.currentTimeMillis();
    }

    public boolean isPaused() {
        return paused;
    }

    public int stepTicks(int ticks) {
        int count = Math.max(0, ticks);
        for (int i = 0; i < count; i++) {
            step(TICK_DURATION_SECONDS);
        }
        lastUpdateTime = System.currentTimeMillis();
        return count;
    }

    public int runFast(double seconds) {
        double duration = Math.max(0.0d, seconds);
        int fullTicks = (int) Math.floor(duration / TICK_DURATION_SECONDS);
        double remainder = duration - fullTicks * TICK_DURATION_SECONDS;
        for (int i = 0; i < fullTicks; i++) {
            step(TICK_DURATION_SECONDS);
        }
        if (remainder > 0.000001d) {
            step(remainder);
            fullTicks++;
        }
        lastUpdateTime = System.currentTimeMillis();
        return fullTicks;
    }

    private void step(double duration) {
        world.startFrame();
        world.runPhysics(duration);
    }

}
