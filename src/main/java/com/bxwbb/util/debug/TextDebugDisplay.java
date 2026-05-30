package com.bxwbb.util.debug;

import com.bxwbb.phy.PhysObject;
import com.bxwbb.util.SpawnUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class TextDebugDisplay extends PhysObject {

    private static final TextColor NAME_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor NORMAL_COLOR = TextColor.color(0x55FF55);
    private static final TextColor WARNING_COLOR = TextColor.color(0xFFFF55);
    private static final TextColor ERROR_COLOR = TextColor.color(0xFF5555);
    private static final TextColor TEXT_COLOR = TextColor.color(0xFFFFFF);
    private static final List<TextDebugDisplay> ACTIVE = new ArrayList<>();

    private final TextDisplay display;
    private Display attachedTo;
    private Vector3d offset = new Vector3d();

    private TextDebugDisplay(Location location, Component text) {
        this.display = SpawnUtil.spawnTextDisplay(location);
        this.display.text(text);
        this.display.setBillboard(Display.Billboard.CENTER);
        this.display.setShadowed(true);
        this.display.setSeeThrough(true);
        this.display.setPersistent(false);
        ACTIVE.add(this);
    }

    public static TextDebugDisplay text(Location location, String text) {
        return new TextDebugDisplay(location, Component.text(text).color(TEXT_COLOR));
    }

    public static TextDebugDisplay text(Display target, Vector3d offset, String text) {
        return text(target.getLocation(), text).attachTo(target, offset);
    }

    public static TextDebugDisplay property(Location location, String name, Object value) {
        return property(location, name, value, DebugState.NORMAL);
    }

    public static TextDebugDisplay property(Location location, String name, Object value, DebugState state) {
        return new TextDebugDisplay(location, propertyLine(name, String.valueOf(value), state));
    }

    public static TextDebugDisplay property(Display target, Vector3d offset, String name, Object value, DebugState state) {
        return property(target.getLocation(), name, value, state).attachTo(target, offset);
    }

    public static TextDebugDisplay tensor(Location location, String name, Vector3d vector) {
        return tensor(location, name, vector, DebugState.NORMAL);
    }

    public static TextDebugDisplay tensor(Location location, String name, Vector3d vector, DebugState state) {
        return new TextDebugDisplay(location, component(name, vector, state));
    }

    public static TextDebugDisplay tensor(Display target, Vector3d offset, String name, Vector3d vector, DebugState state) {
        return tensor(target.getLocation(), name, vector, state).attachTo(target, offset);
    }

    public static TextDebugDisplay tensor(Location location, String name, Matrix3d matrix) {
        return tensor(location, name, matrix, DebugState.NORMAL);
    }

    public static TextDebugDisplay tensor(Location location, String name, Matrix3d matrix, DebugState state) {
        return new TextDebugDisplay(location, component(name, matrix, state));
    }

    public static Component component(String name, Vector3d vector, DebugState state) {
        return propertyLine(name, formatVector(vector), state);
    }

    public static Component component(String name, Matrix3d matrix, DebugState state) {
        return Component.text()
                .append(Component.text(name + ":").color(NAME_COLOR))
                .append(Component.newline())
                .append(Component.text(formatMatrix(matrix)).color(colorFor(state)))
                .build();
    }

    public static TextDebugDisplay tensor(Display target, Vector3d offset, String name, Matrix3d matrix, DebugState state) {
        return tensor(target.getLocation(), name, matrix, state).attachTo(target, offset);
    }

    public static TextDebugDisplay tensor(Location location, List<TensorEntry> entries) {
        List<Component> lines = new ArrayList<>();
        for (TensorEntry entry : entries) {
            lines.add(propertyLine(entry.name, entry.value, entry.state));
        }
        return new TextDebugDisplay(location, joinLines(lines));
    }

    public static TextDebugDisplay tensor(Display target, Vector3d offset, List<TensorEntry> entries) {
        return tensor(target.getLocation(), entries).attachTo(target, offset);
    }

    public static void tickAll() {
        Iterator<TextDebugDisplay> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            TextDebugDisplay debug = iterator.next();
            if (debug.display.isDead() || !debug.display.isValid()) {
                iterator.remove();
                continue;
            }
            if (debug.attachedTo == null) continue;
            if (debug.attachedTo.isDead() || !debug.attachedTo.isValid()) {
                debug.display.remove();
                iterator.remove();
                continue;
            }
            debug.syncAttachment();
        }
    }

    public TextDebugDisplay attachTo(Display target, Vector3d offset) {
        this.attachedTo = target;
        this.offset = new Vector3d(offset);
        syncAttachment();
        return this;
    }

    public TextDebugDisplay detach() {
        this.attachedTo = null;
        return this;
    }

    public void update(Component text) {
        display.text(text);
    }

    public void updateProperty(String name, Object value, DebugState state) {
        update(propertyLine(name, String.valueOf(value), state));
    }

    public void remove() {
        SpawnUtil.removeTextDisplay(display);
        ACTIVE.remove(this);
    }

    @Override
    public List<Display> getAllDisplay() {
        return List.of(display);
    }

    @Override
    public Vector3d getPosition() {
        Location location = display.getLocation();
        return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public void setPosition(Vector3d location) {
        SpawnUtil.configureInterpolation(display);
        display.teleport(new Location(display.getWorld(), location.x, location.y, location.z));
    }

    private void syncAttachment() {
        Location base = attachedTo.getLocation();
        SpawnUtil.configureInterpolation(display);
        display.teleport(new Location(
                base.getWorld(),
                base.getX() + offset.x,
                base.getY() + offset.y,
                base.getZ() + offset.z
        ));
    }

    private static Component propertyLine(String name, String value, DebugState state) {
        return Component.text()
                .append(Component.text(name + ": ").color(NAME_COLOR))
                .append(Component.text(value).color(colorFor(state)))
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

    private static String formatVector(Vector3d vector) {
        return "(" + format(vector.x) + ", " + format(vector.y) + ", " + format(vector.z) + ")";
    }

    private static String formatMatrix(Matrix3d matrix) {
        return "[" + format(matrix.m00()) + ", " + format(matrix.m01()) + ", " + format(matrix.m02()) + "]\n"
                + "[" + format(matrix.m10()) + ", " + format(matrix.m11()) + ", " + format(matrix.m12()) + "]\n"
                + "[" + format(matrix.m20()) + ", " + format(matrix.m21()) + ", " + format(matrix.m22()) + "]";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static TextColor colorFor(DebugState state) {
        switch (state) {
            case WARNING:
                return WARNING_COLOR;
            case ERROR:
                return ERROR_COLOR;
            case NORMAL:
            default:
                return NORMAL_COLOR;
        }
    }

    public enum DebugState {
        NORMAL,
        WARNING,
        ERROR
    }

    public static class TensorEntry {
        private final String name;
        private final String value;
        private final DebugState state;

        public TensorEntry(String name, Object value, DebugState state) {
            this.name = name;
            this.value = String.valueOf(value);
            this.state = state;
        }

        public static TensorEntry normal(String name, Object value) {
            return new TensorEntry(name, value, DebugState.NORMAL);
        }

        public static TensorEntry warning(String name, Object value) {
            return new TensorEntry(name, value, DebugState.WARNING);
        }

        public static TensorEntry error(String name, Object value) {
            return new TensorEntry(name, value, DebugState.ERROR);
        }
    }
}
