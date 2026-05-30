package com.bxwbb.obj;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.phy.RigidBody;
import com.bxwbb.util.BlockDisplayScale;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundPhysBlockDisplay extends RigidBody {

    private static final double MAX_SAFE_COORDINATE = 2.9e7d;

    private final List<Part> parts = new ArrayList<>();

    public CompoundPhysBlockDisplay(Location center) {
        position.set(center.getX(), center.getY(), center.getZ());
        calculateDerivedData();
    }

    public void addPart(BlockDisplay display, Vector3d localOffset) {
        addPart(display, localOffset, List.of(), List.of(), false);
    }

    public void addPart(BlockDisplay display, Vector3d localOffset, List<CollisionBox> collisionBoxes) {
        addPart(display, localOffset, collisionBoxes, List.of(), true);
    }

    public void addPart(BlockDisplay display, Vector3d localOffset, List<CollisionBox> collisionBoxes, List<Display> extraDisplays) {
        addPart(display, localOffset, collisionBoxes, extraDisplays, true);
    }

    private void addPart(BlockDisplay display, Vector3d localOffset, List<CollisionBox> collisionBoxes, List<Display> extraDisplays, boolean hasCollisionBoxes) {
        parts.add(new Part(display, new Vector3d(localOffset), new Vector3f(display.getTransformation().getScale()), collisionBoxes, extraDisplays, hasCollisionBoxes));
    }

    public List<Part> parts() {
        return Collections.unmodifiableList(parts);
    }

    @Override
    public List<Display> getAllDisplay() {
        List<Display> displays = new ArrayList<>();
        for (Part part : parts) {
            displays.add(part.display);
            displays.addAll(part.extraDisplays);
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
                for (Display extraDisplay : part.extraDisplays) {
                    if (extraDisplay != null && extraDisplay.isValid()) extraDisplay.remove();
                }
            }
            return;
        }

        Quaternionf rotation = toJomlQuat(orientation);
        for (Part part : parts) {
            if (part.display == null || part.display.isDead()) continue;
            configureInterpolation(part.display);

            Vector3f offset = new Vector3f((float) part.localOffset.x, (float) part.localOffset.y, (float) part.localOffset.z);
            rotation.transform(offset);
            Location location = new Location(part.display.getWorld(), position.x + offset.x, position.y + offset.y, position.z + offset.z, 0, 0);
            part.display.teleport(location);

            Vector3f visualScale = new Vector3f(part.scale).mul(BlockDisplayScale.multiplier(part.display));
            Vector3f translation = new Vector3f(-visualScale.x * 0.5f, -visualScale.y * 0.5f, -visualScale.z * 0.5f);
            rotation.transform(translation);
            part.display.setTransformation(new Transformation(
                    translation,
                    rotation,
                    visualScale,
                    new Quaternionf(0, 0, 0, 1)
            ));
            for (Display extraDisplay : part.extraDisplays) {
                if (extraDisplay == null || extraDisplay.isDead()) continue;
                configureInterpolation(extraDisplay);
                extraDisplay.teleport(location);
                extraDisplay.setTransformation(new Transformation(
                        new Vector3f(translation),
                        rotation,
                        new Vector3f(visualScale),
                        new Quaternionf(0, 0, 0, 1)
                ));
            }
        }
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

    private Quaternionf toJomlQuat(Quaterniond q) {
        return new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w);
    }

    public static class Part {
        public final BlockDisplay display;
        public final Vector3d localOffset;
        public final Vector3f scale;
        public final List<CollisionBox> collisionBoxes;
        public final List<Display> extraDisplays;
        public final boolean hasCollisionBoxes;

        private Part(BlockDisplay display, Vector3d localOffset, Vector3f scale, List<CollisionBox> collisionBoxes, List<Display> extraDisplays, boolean hasCollisionBoxes) {
            this.display = display;
            this.localOffset = localOffset;
            this.scale = scale;
            this.collisionBoxes = List.copyOf(collisionBoxes);
            this.extraDisplays = List.copyOf(extraDisplays);
            this.hasCollisionBoxes = hasCollisionBoxes;
        }
    }

    public static class CollisionBox {
        public final Vector3d localCenter;
        public final Vector3d halfSize;

        public CollisionBox(Vector3d localCenter, Vector3d halfSize) {
            this.localCenter = new Vector3d(localCenter);
            this.halfSize = new Vector3d(halfSize);
        }
    }
}
