package com.bxwbb.util.debug;

import com.bxwbb.phy.PhysObject;
import com.bxwbb.util.SpawnUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.BoundingBox;
import org.joml.Vector3d;
import org.joml.Matrix3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LineDisplay extends PhysObject {

    public BlockDisplay blockDisplay;
    public Location start;
    public Location end;
    public float width = 0.04f;
    public Material material;
    private TextDebugDisplay label;
    private String vectorName;
    private String lastLabelText;

    public LineDisplay(Location start, Location end, Material material) {
        this.start = start;
        this.end = end;
        this.material = material;
        update();
    }

    public static List<LineDisplay> vectorArrow(Location origin, Vector3d vector, Material material) {
        return vectorArrow(origin, vector, 0.25d, material);
    }

    public static List<LineDisplay> vectorArrow(Location origin, Vector3d vector, double headLength, Material material) {
        return vectorArrow(origin, vector, headLength, material, null);
    }

    public static List<LineDisplay> vectorArrow(Location origin, Vector3d vector, Material material, String name) {
        return vectorArrow(origin, vector, 0.25d, material, name);
    }

    public static List<LineDisplay> vectorArrow(Location origin, Vector3d vector, double headLength, Material material, String name) {
        if (vector.lengthSquared() < 1.0e-8d) {
            throw new IllegalArgumentException("向量长度不能为 0");
        }
        Vector3d direction = new Vector3d(vector).normalize();
        Location end = add(origin, vector);
        List<LineDisplay> lines = new ArrayList<>();
        lines.add(new LineDisplay(origin.clone(), end.clone(), material));

        Vector3d firstSide = perpendicular(direction);
        Vector3d secondSide = new Vector3d(direction).cross(firstSide, new Vector3d()).normalize();
        Location baseCenter = add(end, new Vector3d(direction).mul(-headLength));
        double headRadius = headLength * 0.45d;
        lines.add(new LineDisplay(add(baseCenter, new Vector3d(firstSide).mul(headRadius)), end.clone(), material));
        lines.add(new LineDisplay(add(baseCenter, new Vector3d(firstSide).mul(-headRadius)), end.clone(), material));
        lines.add(new LineDisplay(add(baseCenter, new Vector3d(secondSide).mul(headRadius)), end.clone(), material));
        lines.add(new LineDisplay(add(baseCenter, new Vector3d(secondSide).mul(-headRadius)), end.clone(), material));
        if (name != null && !name.isEmpty()) {
            lines.get(0).vectorName = name;
            lines.get(0).updateVectorLabel();
        }
        return lines;
    }

    public static LineDisplay vectorLine(Location start, Location end, Material material, String name) {
        LineDisplay line = new LineDisplay(start.clone(), end.clone(), material);
        line.vectorName = name;
        line.updateVectorLabel();
        return line;
    }

    public static List<LineDisplay> aabbBox(Location min, Location max, Material material) {
        if (!min.getWorld().equals(max.getWorld())) {
            throw new IllegalArgumentException("AABB 两个角点必须在同一世界");
        }
        return wireBox(corners(min, max), material);
    }

    public static List<LineDisplay> aabbBox(BoundingBox box, org.bukkit.World world, Material material) {
        return aabbBox(
                new Location(world, box.getMinX(), box.getMinY(), box.getMinZ()),
                new Location(world, box.getMaxX(), box.getMaxY(), box.getMaxZ()),
                material
        );
    }

    public static List<LineDisplay> obbBox(Location center, Vector3d halfSize, Matrix3d orientation, Material material) {
        if (halfSize.x < 0.0d || halfSize.y < 0.0d || halfSize.z < 0.0d) {
            throw new IllegalArgumentException("OBB 半尺寸不能小于 0");
        }
        Vector3d[] corners = new Vector3d[8];
        int index = 0;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vector3d local = new Vector3d(halfSize.x * x, halfSize.y * y, halfSize.z * z);
                    orientation.transform(local);
                    corners[index++] = local.add(center.getX(), center.getY(), center.getZ());
                }
            }
        }
        return wireBox(toLocations(center, corners), material);
    }

    public void update() {
        if (blockDisplay != null && !blockDisplay.isDead()) {
            SpawnUtil.removeBlockDisplay(blockDisplay);
        }
        blockDisplay = SpawnUtil.spawnSegment(start, end, width);
        if (blockDisplay == null) {
            throw new IllegalArgumentException("线段起点和终点必须在同一世界，且距离不能为 0");
        }
        blockDisplay.setBlock(material.createBlockData());
    }

    public void setSegment(Location start, Location end) {
        this.start = start;
        this.end = end;
        if (blockDisplay != null && !blockDisplay.isDead() && SpawnUtil.updateSegment(blockDisplay, start, end, width)) {
            blockDisplay.setBlock(material.createBlockData());
            updateVectorLabel();
            return;
        }
        update();
        updateVectorLabel();
    }

    @Override
    public List<Display> getAllDisplay() {
        List<Display> displays = new ArrayList<>();
        displays.add(blockDisplay);
        if (label != null) {
            displays.addAll(label.getAllDisplay());
        }
        return displays;
    }

    @Override
    public Vector3d getPosition() {
        Location location = blockDisplay.getLocation();
        return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void setPosition(Vector3d location) {
        blockDisplay.teleport(new Location(blockDisplay.getWorld(), location.x, location.y, location.z));
    }

    private static List<LineDisplay> wireBox(Location[] corners, Material material) {
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
        };
        List<LineDisplay> lines = new ArrayList<>();
        for (int[] edge : edges) {
            lines.add(new LineDisplay(corners[edge[0]].clone(), corners[edge[1]].clone(), material));
        }
        return lines;
    }

    private static Location[] corners(Location min, Location max) {
        org.bukkit.World world = min.getWorld();
        return new Location[]{
                new Location(world, min.getX(), min.getY(), min.getZ()),
                new Location(world, max.getX(), min.getY(), min.getZ()),
                new Location(world, min.getX(), max.getY(), min.getZ()),
                new Location(world, max.getX(), max.getY(), min.getZ()),
                new Location(world, min.getX(), min.getY(), max.getZ()),
                new Location(world, max.getX(), min.getY(), max.getZ()),
                new Location(world, min.getX(), max.getY(), max.getZ()),
                new Location(world, max.getX(), max.getY(), max.getZ())
        };
    }

    private static Location[] toLocations(Location center, Vector3d[] points) {
        Location[] locations = new Location[points.length];
        for (int i = 0; i < points.length; i++) {
            locations[i] = new Location(center.getWorld(), points[i].x, points[i].y, points[i].z);
        }
        return locations;
    }

    private static Location add(Location location, Vector3d offset) {
        return location.clone().add(offset.x, offset.y, offset.z);
    }

    private void updateVectorLabel() {
        if (vectorName == null || vectorName.isEmpty()) return;
        Vector3d vector = new Vector3d(end.getX() - start.getX(), end.getY() - start.getY(), end.getZ() - start.getZ());
        Location labelLocation = add(start, new Vector3d(vector).mul(0.5d));
        String text = vectorName + "\n" + String.format(Locale.ROOT, "%.3f", vector.length());
        if (label == null) {
            label = TextDebugDisplay.text(labelLocation, text);
            lastLabelText = text;
            return;
        }
        label.setPosition(new Vector3d(labelLocation.getX(), labelLocation.getY(), labelLocation.getZ()));
        if (!text.equals(lastLabelText)) {
            label.update(Component.text(text).color(TextColor.color(0xFFFFFF)));
            lastLabelText = text;
        }
    }

    private static Vector3d perpendicular(Vector3d direction) {
        Vector3d reference = Math.abs(direction.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        return direction.cross(reference, new Vector3d()).normalize();
    }
}
