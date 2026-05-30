package com.bxwbb.util.debug;

import com.bxwbb.obj.Box;
import com.bxwbb.phy.World;
import com.bxwbb.phys.BulletPhysicsEngine.BulletDebugSnapshot;
import com.bxwbb.util.SpawnUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DebugOverlayManager {

    private static final TextColor NAME_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor NORMAL_COLOR = TextColor.color(0x55FF55);
    private static final TextColor WARNING_COLOR = TextColor.color(0xFFFF55);
    private static final TextColor ERROR_COLOR = TextColor.color(0xFF5555);
    private static final TextColor WHITE_COLOR = TextColor.color(0xFFFFFF);

    private static final Map<String, DebugOverlay> OVERLAYS = new HashMap<>();
    private static final List<String> ALL_TYPES = List.of(
            "质量", "位置", "速度", "加速度", "角速度", "角加速度", "尺寸",
            "合力", "重力", "浮力估计", "流体阻力估计", "力矩",
            "惯性张量", "世界惯性张量", "逆质量", "睡眠",
            "AABB", "OBB",
            "Bullet位置", "Bullet质心", "Bullet插值位置", "Bullet旋转",
            "Bullet线速度", "Bullet角速度", "Bullet插值线速度", "Bullet插值角速度",
            "Bullet重力", "Bullet阻尼", "Bullet睡眠阈值", "Bullet摩擦", "Bullet反弹",
            "Bullet激活", "Bullet碰撞", "BulletCCD", "Bullet形状", "Bullet缩放",
            "Bullet包围球", "BulletAABB", "Bullet惯性", "Bullet约束数"
    );

    private DebugOverlayManager() {}

    public static int show(String group, String name, String type, List<Display> displays) {
        List<String> types = expandTypes(type);
        int changed = 0;
        for (Display display : displays) {
            Box box = boxFor(display);
            if (box == null) continue;
            Display anchor = anchorDisplay(box);
            if (anchor == null) continue;

            String key = key(group, name, anchor);
            DebugOverlay overlay = OVERLAYS.computeIfAbsent(key, ignored -> new DebugOverlay(group, name, anchor, box));
            overlay.box = box;
            overlay.anchor = anchor;
            for (String expanded : types) {
                if (overlay.types.add(expanded)) changed++;
            }
        }
        return changed;
    }

    public static int hide(String group, String name, String type) {
        List<String> types = expandTypes(type);
        int changed = 0;
        Iterator<Map.Entry<String, DebugOverlay>> iterator = OVERLAYS.entrySet().iterator();
        while (iterator.hasNext()) {
            DebugOverlay overlay = iterator.next().getValue();
            if (!matches(group, name, overlay)) continue;

            if (type.equals("*")) {
                changed += overlay.types.size();
                overlay.remove();
                iterator.remove();
                continue;
            }

            for (String expanded : types) {
                if (overlay.types.remove(expanded)) {
                    changed++;
                    overlay.clearVisual(expanded);
                }
            }
            if (overlay.types.isEmpty()) {
                overlay.remove();
                iterator.remove();
            }
        }
        return changed;
    }

    public static void tickAll() {
        Iterator<Map.Entry<String, DebugOverlay>> iterator = OVERLAYS.entrySet().iterator();
        while (iterator.hasNext()) {
            DebugOverlay overlay = iterator.next().getValue();
            if (!overlay.isValid()) {
                overlay.remove();
                iterator.remove();
                continue;
            }
            overlay.tick();
        }
    }

    public static List<String> types() {
        return ALL_TYPES;
    }

    private static List<String> expandTypes(String type) {
        if (type.equals("*")) return ALL_TYPES;
        String normalized = normalizeType(type);
        return ALL_TYPES.contains(normalized) ? List.of(normalized) : List.of(type);
    }

    private static String normalizeType(String type) {
        for (String candidate : ALL_TYPES) {
            if (candidate.equalsIgnoreCase(type)) return candidate;
        }
        return type;
    }

    private static boolean matches(String group, String name, DebugOverlay overlay) {
        return (group.equals("*") || group.equals(overlay.group)) && (name.equals("*") || name.equals(overlay.name));
    }

    private static String key(String group, String name, Display anchor) {
        return group + "|" + name + "|" + anchor.getUniqueId();
    }

    private static Box boxFor(Display display) {
        for (Box box : World.getInstance().boxes) {
            if (box.body.getAllDisplay().contains(display)) return box;
        }
        return null;
    }

    private static Display anchorDisplay(Box box) {
        for (Display display : box.body.getAllDisplay()) {
            if (display != null && display.isValid() && !display.isDead()) return display;
        }
        return null;
    }

    private static Component propertyLine(String name, String value, TextColor valueColor) {
        return Component.text()
                .append(Component.text(name + ": ").color(NAME_COLOR))
                .append(Component.text(value).color(valueColor))
                .build();
    }

    private static Component coloredPropertyLine(String name, String value, TextColor color) {
        return Component.text()
                .append(Component.text(name + ": ").color(color))
                .append(Component.text(value).color(color))
                .build();
    }

    private static Component joinLines(List<Component> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(lines.get(i));
        }
        return result;
    }

    private static TextColor colorFor(double value) {
        if (!Double.isFinite(value)) return ERROR_COLOR;
        return Math.abs(value) > 1.0e-6d ? NORMAL_COLOR : WARNING_COLOR;
    }

    private static TextColor colorFor(Material material) {
        switch (material) {
            case LIME_CONCRETE:
                return TextColor.color(0x55FF55);
            case GREEN_CONCRETE:
                return TextColor.color(0x00AA00);
            case YELLOW_CONCRETE:
                return TextColor.color(0xFFFF55);
            case GOLD_BLOCK:
            case ORANGE_CONCRETE:
                return TextColor.color(0xFFAA00);
            case RED_CONCRETE:
                return TextColor.color(0xFF5555);
            case BLUE_CONCRETE:
                return TextColor.color(0x5555FF);
            case CYAN_CONCRETE:
            case LIGHT_BLUE_CONCRETE:
                return TextColor.color(0x55FFFF);
            case PURPLE_CONCRETE:
            case MAGENTA_CONCRETE:
                return TextColor.color(0xAA00AA);
            case WHITE_CONCRETE:
                return WHITE_COLOR;
            default:
                return WHITE_COLOR;
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String vectorText(Vector3d vector) {
        return "(" + format(vector.x) + ", " + format(vector.y) + ", " + format(vector.z) + ")";
    }

    private static String vectorTextWithLength(Vector3d vector) {
        return vectorText(vector) + " | " + format(vector.length());
    }

    private static String quatText(Quaterniond quaternion) {
        return "(" + format(quaternion.x) + ", " + format(quaternion.y) + ", " + format(quaternion.z) + ", " + format(quaternion.w) + ")";
    }

    private static String matrixText(Matrix3d matrix) {
        return "[" + format(matrix.m00()) + ", " + format(matrix.m01()) + ", " + format(matrix.m02()) + "]\n"
                + "[" + format(matrix.m10()) + ", " + format(matrix.m11()) + ", " + format(matrix.m12()) + "]\n"
                + "[" + format(matrix.m20()) + ", " + format(matrix.m21()) + ", " + format(matrix.m22()) + "]";
    }

    private static Location location(Display display, Vector3d position) {
        return new Location(display.getWorld(), position.x, position.y, position.z);
    }

    private static Location add(Location location, Vector3d offset) {
        return location.clone().add(offset.x, offset.y, offset.z);
    }

    private static Vector3d perpendicular(Vector3d direction) {
        Vector3d reference = Math.abs(direction.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        return direction.cross(reference, new Vector3d()).normalize();
    }

    private static class DebugOverlay {
        private final String group;
        private final String name;
        private final Set<String> types = new LinkedHashSet<>();
        private final Map<String, List<LineDisplay>> linesByType = new HashMap<>();
        private Display anchor;
        private Box box;
        private TextDebugDisplay text;

        private DebugOverlay(String group, String name, Display anchor, Box box) {
            this.group = group;
            this.name = name;
            this.anchor = anchor;
            this.box = box;
        }

        private boolean isValid() {
            return anchor != null && anchor.isValid() && !anchor.isDead();
        }

        private void tick() {
            Vector3d textOffset = new Vector3d(0.0d, box.halfSize.y + 0.7d, 0.0d);
            if (text == null) {
                text = TextDebugDisplay.text(anchor, textOffset, "");
            } else {
                text.attachTo(anchor, textOffset);
            }

            List<Component> lines = new ArrayList<>();
            BulletDebugSnapshot bullet = World.getInstance().debugSnapshot(anchor);
            for (String type : types) {
                appendType(type, bullet, lines);
            }
            text.update(lines.isEmpty() ? Component.empty() : joinLines(lines));
        }

        private void appendType(String type, BulletDebugSnapshot bullet, List<Component> textLines) {
            switch (type) {
                case "质量":
                    textLines.add(propertyLine(type, box.body.hasFiniteMass() ? format(box.body.getMass()) : "无限", box.body.hasFiniteMass() ? NORMAL_COLOR : WARNING_COLOR));
                    return;
                case "位置":
                    appendVector(type, box.body.position, Material.LIME_CONCRETE, textLines, true);
                    return;
                case "速度":
                    appendVector(type, box.body.velocity, Material.CYAN_CONCRETE, textLines, true);
                    return;
                case "加速度":
                    appendVector(type, box.body.lastFrameAcceleration, Material.LIGHT_BLUE_CONCRETE, textLines, true);
                    return;
                case "角速度":
                    appendVector(type, box.body.rotation, Material.PURPLE_CONCRETE, textLines, false);
                    return;
                case "角加速度":
                    appendVector(type, box.body.inverseInertiaTensorWorld.transform(new Vector3d(box.body.torqueAccum)), Material.MAGENTA_CONCRETE, textLines, false);
                    return;
                case "尺寸":
                    textLines.add(propertyLine(type, vectorText(new Vector3d(box.halfSize).mul(2.0d)), NORMAL_COLOR));
                    return;
                case "合力":
                    appendVector(type, box.body.forceAccum, Material.RED_CONCRETE, textLines, true);
                    return;
                case "重力":
                    appendVector(type, new Vector3d(0.0d, -9.8d * Math.max(0.0d, box.body.getMass()), 0.0d), Material.GREEN_CONCRETE, textLines, true);
                    return;
                case "浮力估计":
                    appendVector(type, new Vector3d(0.0d, Math.max(0.0d, box.halfSize.x * box.halfSize.y * box.halfSize.z * 8.0d), 0.0d), Material.BLUE_CONCRETE, textLines, true);
                    return;
                case "流体阻力估计":
                    appendVector(type, new Vector3d(box.body.velocity).negate(), Material.ORANGE_CONCRETE, textLines, true);
                    return;
                case "力矩":
                    appendVector(type, box.body.torqueAccum, Material.YELLOW_CONCRETE, textLines, true);
                    return;
                case "惯性张量":
                    textLines.add(propertyLine(type, "\n" + matrixText(box.body.inverseInertiaTensor), NORMAL_COLOR));
                    return;
                case "世界惯性张量":
                    textLines.add(propertyLine(type, "\n" + matrixText(box.body.inverseInertiaTensorWorld), NORMAL_COLOR));
                    return;
                case "逆质量":
                    textLines.add(propertyLine(type, format(box.body.getInverseMass()), colorFor(box.body.getInverseMass())));
                    return;
                case "睡眠":
                    textLines.add(propertyLine(type, box.body.isAwake ? "唤醒" : "睡眠", box.body.isAwake ? NORMAL_COLOR : WARNING_COLOR));
                    return;
                case "AABB":
                    updateAabb(type, new Vector3d(box.body.position).sub(box.halfSize), new Vector3d(box.body.position).add(box.halfSize), Material.WHITE_CONCRETE);
                    textLines.add(propertyLine(type, vectorText(box.halfSize), WHITE_COLOR));
                    return;
                case "OBB":
                    updateObb(type, box.body.position, box.halfSize, box.body.orientation, Material.YELLOW_CONCRETE);
                    textLines.add(propertyLine(type, vectorText(box.halfSize), colorFor(Material.YELLOW_CONCRETE)));
                    return;
                default:
                    appendBulletType(type, bullet, textLines);
            }
        }

        private void appendBulletType(String type, BulletDebugSnapshot bullet, List<Component> textLines) {
            if (bullet == null) {
                textLines.add(propertyLine(type, "无 Bullet 数据", ERROR_COLOR));
                return;
            }
            switch (type) {
                case "Bullet位置":
                    appendVector(type, bullet.worldPosition, Material.LIME_CONCRETE, textLines, true);
                    return;
                case "Bullet质心":
                    appendVector(type, bullet.centerOfMassPosition, Material.GREEN_CONCRETE, textLines, true);
                    return;
                case "Bullet插值位置":
                    appendVector(type, bullet.interpolationPosition, Material.LIGHT_BLUE_CONCRETE, textLines, true);
                    return;
                case "Bullet旋转":
                    textLines.add(propertyLine(type, quatText(bullet.orientation), NORMAL_COLOR));
                    return;
                case "Bullet线速度":
                    appendVector(type, bullet.linearVelocity, Material.CYAN_CONCRETE, textLines, true);
                    return;
                case "Bullet角速度":
                    appendVector(type, bullet.angularVelocity, Material.PURPLE_CONCRETE, textLines, false);
                    return;
                case "Bullet插值线速度":
                    appendVector(type, bullet.interpolationLinearVelocity, Material.LIGHT_BLUE_CONCRETE, textLines, true);
                    return;
                case "Bullet插值角速度":
                    appendVector(type, bullet.interpolationAngularVelocity, Material.MAGENTA_CONCRETE, textLines, false);
                    return;
                case "Bullet重力":
                    appendVector(type, bullet.gravity, Material.GREEN_CONCRETE, textLines, true);
                    return;
                case "Bullet阻尼":
                    textLines.add(propertyLine(type, "线性 " + format(bullet.linearDamping) + " / 角 " + format(bullet.angularDamping), NORMAL_COLOR));
                    return;
                case "Bullet睡眠阈值":
                    textLines.add(propertyLine(type, "线性 " + format(bullet.linearSleepingThreshold) + " / 角 " + format(bullet.angularSleepingThreshold), NORMAL_COLOR));
                    return;
                case "Bullet摩擦":
                    textLines.add(propertyLine(type, format(bullet.friction), colorFor(bullet.friction)));
                    return;
                case "Bullet反弹":
                    textLines.add(propertyLine(type, format(bullet.restitution), colorFor(bullet.restitution)));
                    return;
                case "Bullet激活":
                    textLines.add(propertyLine(type, "状态 " + bullet.activationState + " / active=" + bullet.active + " / sleep=" + bullet.wantsSleeping, bullet.active ? NORMAL_COLOR : WARNING_COLOR));
                    return;
                case "Bullet碰撞":
                    textLines.add(propertyLine(type, "inWorld=" + bullet.inWorld + " flags=" + bullet.collisionFlags + " island=" + bullet.islandTag, bullet.inWorld ? NORMAL_COLOR : ERROR_COLOR));
                    return;
                case "BulletCCD":
                    textLines.add(propertyLine(type, "半径 " + format(bullet.ccdSweptSphereRadius) + " / 阈值 " + format(bullet.ccdMotionThreshold), NORMAL_COLOR));
                    return;
                case "Bullet形状":
                    textLines.add(propertyLine(type, bullet.shapeName + " / " + bullet.shapeType + " / margin " + format(bullet.shapeMargin), NORMAL_COLOR));
                    return;
                case "Bullet缩放":
                    appendVector(type, bullet.localScaling, Material.WHITE_CONCRETE, textLines, false);
                    return;
                case "Bullet包围球":
                    appendVector(type, bullet.boundingSphereCenter, Material.ORANGE_CONCRETE, textLines, false);
                    textLines.add(propertyLine(type + "半径", format(bullet.boundingSphereRadius), NORMAL_COLOR));
                    return;
                case "BulletAABB":
                    updateAabb(type, bullet.aabbMin, bullet.aabbMax, Material.LIGHT_BLUE_CONCRETE);
                    textLines.add(propertyLine(type, vectorText(bullet.aabbMin) + " -> " + vectorText(bullet.aabbMax), colorFor(Material.LIGHT_BLUE_CONCRETE)));
                    return;
                case "Bullet惯性":
                    textLines.add(propertyLine(type, "\n" + matrixText(bullet.inverseInertiaTensorWorld) + "\n局部 " + vectorText(bullet.inverseInertiaDiagLocal), NORMAL_COLOR));
                    return;
                case "Bullet约束数":
                    textLines.add(propertyLine(type, String.valueOf(bullet.constraintRefs), colorFor(bullet.constraintRefs)));
                    return;
                default:
                    textLines.add(propertyLine(type, "未知类型", ERROR_COLOR));
            }
        }

        private void appendVector(String type, Vector3d vector, Material material, List<Component> textLines, boolean draw) {
            TextColor color = colorFor(material);
            textLines.add(coloredPropertyLine(type, vectorTextWithLength(vector), color));
            if (!draw) {
                clearVisual(type);
                return;
            }
            updateVector(type, vector, material);
        }

        private void updateVector(String type, Vector3d vector, Material material) {
            if (vector.lengthSquared() < 1.0e-8d) {
                clearVisual(type);
                return;
            }
            Vector3d visual = new Vector3d(vector);
            double length = visual.length();
            double maxLength = Math.max(0.5d, box.halfSize.length() * 2.5d);
            if (length > maxLength) visual.mul(maxLength / length);

            Location origin = location(anchor, box.body.position);
            Location end = add(origin, visual);
            Vector3d direction = new Vector3d(visual).normalize();
            double headLength = Math.min(0.35d, Math.max(0.12d, visual.length() * 0.25d));
            Vector3d firstSide = perpendicular(direction);
            Vector3d secondSide = new Vector3d(direction).cross(firstSide, new Vector3d()).normalize();
            Location baseCenter = add(end, new Vector3d(direction).mul(-headLength));
            double headRadius = headLength * 0.45d;

            List<Segment> segments = List.of(
                    new Segment(origin, end, type),
                    new Segment(add(baseCenter, new Vector3d(firstSide).mul(headRadius)), end, null),
                    new Segment(add(baseCenter, new Vector3d(firstSide).mul(-headRadius)), end, null),
                    new Segment(add(baseCenter, new Vector3d(secondSide).mul(headRadius)), end, null),
                    new Segment(add(baseCenter, new Vector3d(secondSide).mul(-headRadius)), end, null)
            );
            updateLines(type, segments, material);
        }

        private void updateAabb(String type, Vector3d min, Vector3d max, Material material) {
            Location minLocation = location(anchor, min);
            Location maxLocation = location(anchor, max);
            updateLines(type, boxSegments(corners(minLocation, maxLocation)), material);
        }

        private void updateObb(String type, Vector3d center, Vector3d halfSize, Quaterniond orientation, Material material) {
            Vector3d[] points = new Vector3d[8];
            int index = 0;
            for (int x = -1; x <= 1; x += 2) {
                for (int y = -1; y <= 1; y += 2) {
                    for (int z = -1; z <= 1; z += 2) {
                        Vector3d local = new Vector3d(halfSize.x * x, halfSize.y * y, halfSize.z * z);
                        orientation.transform(local);
                        points[index++] = local.add(center);
                    }
                }
            }
            Location[] corners = new Location[points.length];
            for (int i = 0; i < points.length; i++) corners[i] = location(anchor, points[i]);
            updateLines(type, boxSegments(corners), material);
        }

        private Location[] corners(Location min, Location max) {
            return new Location[]{
                    new Location(anchor.getWorld(), min.getX(), min.getY(), min.getZ()),
                    new Location(anchor.getWorld(), max.getX(), min.getY(), min.getZ()),
                    new Location(anchor.getWorld(), min.getX(), max.getY(), min.getZ()),
                    new Location(anchor.getWorld(), max.getX(), max.getY(), min.getZ()),
                    new Location(anchor.getWorld(), min.getX(), min.getY(), max.getZ()),
                    new Location(anchor.getWorld(), max.getX(), min.getY(), max.getZ()),
                    new Location(anchor.getWorld(), min.getX(), max.getY(), max.getZ()),
                    new Location(anchor.getWorld(), max.getX(), max.getY(), max.getZ())
            };
        }

        private List<Segment> boxSegments(Location[] corners) {
            int[][] edges = {
                    {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                    {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
            };
            List<Segment> segments = new ArrayList<>();
            for (int[] edge : edges) {
                segments.add(new Segment(corners[edge[0]], corners[edge[1]]));
            }
            return segments;
        }

        private void updateLines(String type, List<Segment> segments, Material material) {
            List<LineDisplay> lines = linesByType.computeIfAbsent(type, ignored -> new ArrayList<>());
            while (lines.size() > segments.size()) {
                removeLine(lines.remove(lines.size() - 1));
            }
            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                if (i >= lines.size()) {
                    lines.add(segment.label == null
                            ? new LineDisplay(segment.start, segment.end, material)
                            : LineDisplay.vectorLine(segment.start, segment.end, material, segment.label));
                    continue;
                }
                LineDisplay line = lines.get(i);
                line.material = material;
                line.setSegment(segment.start, segment.end);
            }
        }

        private void clearVisual(String type) {
            List<LineDisplay> lines = linesByType.remove(type);
            if (lines == null) return;
            for (LineDisplay line : lines) removeLine(line);
        }

        private void removeLine(LineDisplay line) {
            for (Display display : line.getAllDisplay()) {
                if (display != null && !display.isDead()) SpawnUtil.removeDisplay(display);
            }
        }

        private void remove() {
            if (text != null) text.remove();
            for (String type : new ArrayList<>(linesByType.keySet())) clearVisual(type);
        }
    }

    private static class Segment {
        private final Location start;
        private final Location end;
        private final String label;

        private Segment(Location start, Location end) {
            this(start, end, null);
        }

        private Segment(Location start, Location end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }
}
