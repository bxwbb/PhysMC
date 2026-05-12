package com.bxwbb.util.debug;

import com.bxwbb.phy.PhysObject;
import com.bxwbb.math.Vector3;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;

import java.util.List;

public class LineDisplay extends PhysObject {

    public BlockDisplay blockDisplay;
    public Location start;
    public Location end;
    public float width = 0.04f;
    public Material material;

    public LineDisplay(Location start, Location end, Material material) {
        this.start = start;
        this.end = end;
        this.material = material;
        update();
    }

    public void update() {
        blockDisplay = SpawnUtil.spawnSegment(start, end, width);
        blockDisplay.setBlock(material.createBlockData());
    }

    @Override
    public List<Display> getAllDisplay() {
        return List.of(blockDisplay);
    }

    @Override
    public Vector3 getPosition() {
        Location location = blockDisplay.getLocation();
        return new Vector3(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void setPosition(Vector3 location) {
        blockDisplay.teleport(new Location(blockDisplay.getWorld(), location.x, location.y, location.z));
    }
}