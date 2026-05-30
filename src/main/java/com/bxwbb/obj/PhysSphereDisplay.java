package com.bxwbb.obj;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.phy.RigidBody;
import com.bxwbb.util.BlockDisplayScale;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class PhysSphereDisplay extends RigidBody {

    private static final double MAX_SAFE_COORDINATE = 2.9e7d;
    private static final int MAX_DETAIL = 8;

    private final List<Part> parts = new ArrayList<>();
    private final double radius;
    private final int detail;

    public PhysSphereDisplay(Location center, double radius, Material material) {
        this(center, radius, material, configuredDetail());
    }

    public PhysSphereDisplay(Location center, double radius, Material material, int detail) {
        this.radius = radius;
        this.detail = Math.max(0, Math.min(MAX_DETAIL, detail));
        position.set(center.getX(), center.getY(), center.getZ());
        createParts(center, material == null ? Material.SLIME_BLOCK : material);
        calculateDerivedData();
    }

    public double radius() {
        return radius;
    }

    public int detail() {
        return detail;
    }

    @Override
    public List<Display> getAllDisplay() {
        List<Display> displays = new ArrayList<>();
        for (Part part : parts) {
            displays.add(part.display);
        }
        return displays;
    }

    @Override
    public Vector3d getPosition() {
        return position;
    }

    @Override
    public void setPosition(Vector3d location) {
        position.set(location);
    }

    @Override
    public void tick() {
        if (!isSafePosition()) {
            for (Part part : parts) {
                if (part.display != null && part.display.isValid()) part.display.remove();
            }
            return;
        }

        for (Part part : parts) {
            if (part.display == null || part.display.isDead()) continue;
            configureInterpolation(part.display);

            Quaternionf rotation = new Quaternionf((float) orientation.x, (float) orientation.y, (float) orientation.z, (float) orientation.w);
            Vector3f offset = new Vector3f((float) part.localOffset.x, (float) part.localOffset.y, (float) part.localOffset.z);
            rotation.transform(offset);
            Location location = new Location(
                    part.display.getWorld(),
                    position.x + offset.x,
                    position.y + offset.y,
                    position.z + offset.z,
                    0,
                    0
            );
            part.display.teleport(location);

            float visualSize = part.size * BlockDisplayScale.multiplier(part.display);
            Vector3f translation = new Vector3f(-visualSize * 0.5f, -visualSize * 0.5f, -visualSize * 0.5f);
            rotation.transform(translation);
            part.display.setTransformation(new Transformation(
                    translation,
                    rotation,
                    new Vector3f(visualSize, visualSize, visualSize),
                    new Quaternionf(0, 0, 0, 1)
            ));
        }
    }

    private void createParts(Location center, Material material) {
        if (detail == 0) {
            double side = radius * 2.0d;
            addPart(center, material, new Vector3d(), side);
            return;
        }

        double partSize = Math.max(0.12d, radius * 0.38d);
        addPart(center, material, new Vector3d(), Math.max(partSize, radius * 0.55d));

        int rings = detail;
        int segments = Math.max(8, detail * 2 + 2);
        for (int ring = -rings; ring <= rings; ring++) {
            double y = radius * ring / rings;
            double ringRadius = Math.sqrt(Math.max(0.0d, radius * radius - y * y));
            if (ringRadius < partSize * 0.35d) continue;
            for (int i = 0; i < segments; i++) {
                double angle = Math.PI * 2.0d * i / segments;
                addPart(center, material, new Vector3d(Math.cos(angle) * ringRadius, y, Math.sin(angle) * ringRadius), partSize);
            }
        }
        addPart(center, material, new Vector3d(0.0d, radius, 0.0d), partSize);
        addPart(center, material, new Vector3d(0.0d, -radius, 0.0d), partSize);
    }

    private void addPart(Location center, Material material, Vector3d offset, double size) {
        BlockDisplay display = SpawnUtil.spawnBlockDisplay(new Location(center.getWorld(), center.getX() + offset.x, center.getY() + offset.y, center.getZ() + offset.z));
        display.setBlock(material.createBlockData());
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setPersistent(false);
        display.setTransformation(new Transformation(
                new Vector3f((float) -size * 0.5f, (float) -size * 0.5f, (float) -size * 0.5f),
                new Quaternionf(),
                new Vector3f((float) size, (float) size, (float) size),
                new Quaternionf(0, 0, 0, 1)
        ));
        parts.add(new Part(display, offset, (float) size));
    }

    private boolean isSafePosition() {
        return Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z)
                && Math.abs(position.x) <= MAX_SAFE_COORDINATE
                && Math.abs(position.z) <= MAX_SAFE_COORDINATE
                && position.y > -4096.0d
                && position.y < 4096.0d;
    }

    private void configureInterpolation(Display display) {
        display.setInterpolationDelay(getInt(PhysConfig.DISPLAY_INTERPOLATION_DELAY));
        display.setInterpolationDuration(getInt(PhysConfig.DISPLAY_INTERPOLATION_DURATION));
        display.setTeleportDuration(getInt(PhysConfig.DISPLAY_TELEPORT_DURATION));
    }

    private int getInt(String path) {
        return PhysMC.getPlugin(PhysMC.class).getConfig().getInt(path);
    }

    private static int configuredDetail() {
        PhysMC plugin = PhysMC.getPlugin(PhysMC.class);
        return plugin.getConfig().getInt(PhysConfig.DISPLAY_SPHERE_DETAIL, 3);
    }

    private static class Part {
        private final BlockDisplay display;
        private final Vector3d localOffset;
        private final float size;

        private Part(BlockDisplay display, Vector3d localOffset, float size) {
            this.display = display;
            this.localOffset = new Vector3d(localOffset);
            this.size = size;
        }
    }
}
