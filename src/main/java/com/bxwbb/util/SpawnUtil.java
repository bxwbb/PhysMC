package com.bxwbb.util;

import com.bxwbb.event.DisplayHitListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class SpawnUtil {

    public static final List<Display> displays = new ArrayList<>();

    private SpawnUtil() {}

    public static TextDisplay spawnTextDisplay(Location location) {
        TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);
        displays.add(textDisplay);
        return textDisplay;
    }

    public static void removeTextDisplay(TextDisplay textDisplay) {
        textDisplay.remove();
        displays.remove(textDisplay);
    }

    public static BlockDisplay spawnBlockDisplay(Location location) {
        BlockDisplay blockDisplay = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        displays.add(blockDisplay);
        DisplayHitListener.BLOCK_DISPLAYS.add(blockDisplay);
        return blockDisplay;
    }

    public static void removeBlockDisplay(BlockDisplay blockDisplay) {
        blockDisplay.remove();
        DisplayHitListener.BLOCK_DISPLAYS.remove(blockDisplay);
        displays.remove(blockDisplay);
    }

    // ====================== 线段生成：全部 -1.5 修正漂移 ======================
    public static BlockDisplay spawnSegment(Location start, Location end, float width) {
        if (start == null || end == null || !start.getWorld().equals(end.getWorld()))
            return null;

        Vector startVec = start.toVector();
        Vector endVec = end.toVector();
        Vector dir = endVec.subtract(startVec);
        double length = dir.length();
        if (length < 0.0001)
            return null;
        dir.normalize();

        // 中心点全部 -1.5 修正漂移
        Vector centerVec = startVec.clone().add(dir.clone().multiply(length * 0.5));
        Location centerLoc = new Location(
                start.getWorld(),
                centerVec.getX() + 0.5,
                centerVec.getY() + 0.5,
                centerVec.getZ() + 0.5
        );

        Quaternionf rotation = getRotation(new Vector(1, 0, 0), dir);
        Vector3f scale = new Vector3f((float) length, width, width);

        BlockDisplay segment = spawnOrientedBlock(centerLoc, rotation, scale);
        displays.add(segment);
        return segment;
    }

    private static Quaternionf getRotation(Vector from, Vector to) {
        from = from.clone().normalize();
        to = to.clone().normalize();

        double dot = from.dot(to);
        if (dot > 0.9999)
            return new Quaternionf();
        if (dot < -0.9999)
            return new Quaternionf(0, 1, 0, 0);

        Vector cross = from.getCrossProduct(to).normalize();
        double angle = Math.acos(dot);
        return new Quaternionf().rotateAxis((float) angle, (float) cross.getX(), (float) cross.getY(), (float) cross.getZ());
    }

    public static BlockDisplay spawnOrientedBlock(Location location, Quaternionf rotation, Vector3f scale) {
        BlockDisplay display = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        BlockData blockData = Bukkit.createBlockData(Material.BLUE_CONCRETE);
        display.setBlock(blockData);

        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                rotation,
                scale,
                new Quaternionf(0, 0, 0, 1)
        ));

        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setPersistent(false);
        return display;
    }

    public static void removeDisplay(Display display) {
        display.remove();
        displays.remove(display);
    }

    public static void removeAll() {
        for (Display display : displays)
            display.remove();
        displays.clear();
    }

}