package com.bxwbb.util.debug;

import com.bxwbb.phy.PhysObject;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class SpringDisplay extends PhysObject {

    private static final int SEGMENTS_PER_COIL = 4;
    private static final float WIDTH = 0.035f;
    private static final List<SpringDisplay> ACTIVE = new ArrayList<>();

    private final Display first;
    private final Display second;
    private final int coils;
    private final double radius;
    private final List<BlockDisplay> segments = new ArrayList<>();

    public SpringDisplay(Display first, Display second, int coils, double radius) {
        this.first = first;
        this.second = second;
        this.coils = Math.max(1, coils);
        this.radius = Math.max(0.03d, radius);
        ACTIVE.add(this);
        rebuild();
    }

    public static void tickAll() {
        ACTIVE.removeIf(display -> {
            display.tick();
            return display.segments.isEmpty();
        });
    }

    @Override
    public List<Display> getAllDisplay() {
        return new ArrayList<>(segments);
    }

    @Override
    public Vector3d getPosition() {
        Location location = first.getLocation();
        return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void setPosition(Vector3d location) {
    }

    @Override
    public void tick() {
        update();
    }

    private void rebuild() {
        int count = coils * SEGMENTS_PER_COIL;
        for (int i = 0; i < count; i++) {
            BlockDisplay segment = SpawnUtil.spawnSegment(first.getLocation(), second.getLocation(), WIDTH);
            if (segment != null) {
                segment.setBlock(Bukkit.createBlockData(Material.PURPLE_CONCRETE));
                segments.add(segment);
            }
        }
        update();
    }

    private void update() {
        if (first.isDead() || second.isDead() || !first.getWorld().equals(second.getWorld())) {
            remove();
            return;
        }

        List<Location> points = springPoints();
        int requiredSegments = points.size() - 1;
        while (segments.size() < requiredSegments) {
            BlockDisplay segment = SpawnUtil.spawnSegment(points.get(0), points.get(1), WIDTH);
            if (segment == null) break;
            segment.setBlock(Bukkit.createBlockData(Material.PURPLE_CONCRETE));
            segments.add(segment);
        }

        for (int i = 0; i < segments.size(); i++) {
            BlockDisplay segment = segments.get(i);
            if (i >= requiredSegments || !SpawnUtil.updateSegment(segment, points.get(i), points.get(i + 1), WIDTH)) {
                segment.remove();
            }
        }
        segments.removeIf(Display::isDead);
    }

    private List<Location> springPoints() {
        Location start = first.getLocation();
        Location end = second.getLocation();
        Vector axis = end.toVector().subtract(start.toVector());
        double length = axis.length();
        if (length < 0.0001d) {
            List<Location> points = new ArrayList<>();
            points.add(start);
            points.add(end);
            return points;
        }
        axis.normalize();

        Vector side = Math.abs(axis.getY()) < 0.9d ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector normal = axis.getCrossProduct(side).normalize();
        Vector binormal = axis.getCrossProduct(normal).normalize();

        int pointCount = coils * SEGMENTS_PER_COIL + 1;
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < pointCount; i++) {
            double t = i / (double) (pointCount - 1);
            double angle = Math.PI * 2.0d * coils * t;
            Vector radial = normal.clone().multiply(Math.cos(angle) * radius)
                    .add(binormal.clone().multiply(Math.sin(angle) * radius));
            if (i == 0 || i == pointCount - 1) {
                radial.zero();
            }
            points.add(start.clone().add(axis.clone().multiply(length * t)).add(radial));
        }
        return points;
    }

    public void remove() {
        for (BlockDisplay segment : segments) {
            if (!segment.isDead()) {
                segment.remove();
            }
        }
        segments.clear();
        ACTIVE.remove(this);
    }
}
