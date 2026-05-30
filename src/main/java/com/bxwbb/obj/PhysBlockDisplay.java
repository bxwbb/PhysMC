package com.bxwbb.obj;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.phy.RigidBody;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

public class PhysBlockDisplay extends RigidBody {

    private static final double MAX_SAFE_COORDINATE = 2.9e7d;

    private final BlockDisplay blockDisplay;

    public PhysBlockDisplay(Location location) {
        this.blockDisplay = SpawnUtil.spawnBlockDisplay(location);
        blockDisplay.setBlock(Bukkit.createBlockData(Material.COMMAND_BLOCK));
        blockDisplay.setShadowRadius(0);
        blockDisplay.setShadowStrength(0);
        configureInterpolation();
        blockDisplay.setPersistent(false);

        position.set(blockDisplay.getX(), blockDisplay.getY(), blockDisplay.getZ());

        calculateDerivedData();
    }

    @Override
    public List<Display> getAllDisplay() {
        return List.of(blockDisplay);
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
            blockDisplay.remove();
            return;
        }
        configureInterpolation();
        blockDisplay.teleport(new Location(blockDisplay.getWorld(), position.x, position.y, position.z, 0, 0));

        Transformation old = blockDisplay.getTransformation();
        Vector3f scale = new Vector3f(old.getScale());
        Quaternionf rotation = toJomlQuat(orientation);
        Vector3f translation = new Vector3f(-scale.x * 0.5f, -scale.y * 0.5f, -scale.z * 0.5f);
        rotation.transform(translation);
        blockDisplay.setTransformation(new Transformation(
                translation,
                rotation,
                scale,
                new Quaternionf(0,0,0,1)
        ));
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

    private void configureInterpolation() {
        blockDisplay.setInterpolationDelay(getInt(PhysConfig.DISPLAY_INTERPOLATION_DELAY));
        blockDisplay.setInterpolationDuration(getInt(PhysConfig.DISPLAY_INTERPOLATION_DURATION));
        blockDisplay.setTeleportDuration(getInt(PhysConfig.DISPLAY_TELEPORT_DURATION));
    }

    private int getInt(String path) {
        return PhysMC.getPlugin(PhysMC.class).getConfig().getInt(path);
    }

    private Quaternionf toJomlQuat(Quaterniond q) {
        return new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w);
    }

    public BlockDisplay getBlockDisplay() {
        return blockDisplay;
    }
}
