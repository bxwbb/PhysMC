package com.bxwbb.obj;

import com.bxwbb.phy.RigidBody;
import com.bxwbb.math.Quaternion;
import com.bxwbb.math.Vector3;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class PhysBlockDisplay extends RigidBody {

    private final BlockDisplay blockDisplay;

    public PhysBlockDisplay(Location location) {
        this.blockDisplay = SpawnUtil.spawnBlockDisplay(location);
        blockDisplay.setBlock(Bukkit.createBlockData(Material.COMMAND_BLOCK));
        blockDisplay.setShadowRadius(0);
        blockDisplay.setShadowStrength(0);
        blockDisplay.setPersistent(false);

        position.x = blockDisplay.getX();
        position.y = blockDisplay.getY();
        position.z = blockDisplay.getZ();

        calculateDerivedData();
    }

    @Override
    public List<Display> getAllDisplay() {
        return List.of(blockDisplay);
    }

    @Override
    public Vector3 getPosition() {
        return position;
    }

    @Override
    public void setPosition(Vector3 location) {
        position.clear();
        position.add(location);
    }

    @Override
    public void tick() {
        blockDisplay.teleport(new Location(blockDisplay.getWorld(), position.x, position.y, position.z, 0, 0));

        Transformation old = blockDisplay.getTransformation();
        blockDisplay.setTransformation(new Transformation(
                new Vector3f(0,0,0),
                toJomlQuat(orientation),
                old.getScale(),
                new Quaternionf(0,0,0,1)
        ));
    }

    private Quaternionf toJomlQuat(Quaternion q) {
        return new Quaternionf((float) q.i, (float) q.j, (float) q.k, (float) q.r);
    }

    public BlockDisplay getBlockDisplay() {
        return blockDisplay;
    }
}