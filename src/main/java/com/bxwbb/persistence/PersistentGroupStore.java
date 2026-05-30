package com.bxwbb.persistence;

import com.bxwbb.PhysMC;
import com.bxwbb.PhysConfig;
import com.bxwbb.obj.Box;
import com.bxwbb.obj.CompoundPhysBlockDisplay;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.obj.PhysSphereDisplay;
import com.bxwbb.phy.World;
import com.bxwbb.phys.BulletPhysicsEngine.ConstraintDetail;
import com.bxwbb.util.ObjectUtil;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class PersistentGroupStore {

    private final PhysMC plugin;
    private final File file;
    private final File saveDirectory;
    private FileConfiguration config;

    public PersistentGroupStore(PhysMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "persistent-groups.yml");
        this.saveDirectory = new File(plugin.getDataFolder(), "saves");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public int saveGroup(String id) {
        config.set("groups." + id, null);
        ConfigurationSection group = config.createSection("groups." + id);
        int saved = saveGroupInto(group, id);
        if (saved == 0) {
            config.set("groups." + id, null);
            return 0;
        }
        save();
        return saved;
    }

    public int saveGroupToFile(String id, String fileName) {
        YamlConfiguration output = new YamlConfiguration();
        output.set("format", 1);
        output.set("source-group", id);
        int saved = saveGroupInto(output.createSection("group"), id);
        if (saved == 0) return 0;

        File target = saveFile(fileName);
        ensureSaveDirectory();
        try {
            output.save(target);
        } catch (IOException e) {
            plugin.getLogger().warning("保存物理存档失败: " + e.getMessage());
            return 0;
        }
        return saved;
    }

    public LoadResult loadFileToGroup(String fileName, String targetGroup) {
        File source = saveFile(fileName);
        if (!source.isFile()) {
            return new LoadResult(0, List.of());
        }

        YamlConfiguration input = YamlConfiguration.loadConfiguration(source);
        List<Display> loaded = loadGroup(targetGroup, input.getConfigurationSection("group"));
        return new LoadResult(loaded.size(), loaded);
    }

    public boolean removeSaveFile(String fileName) {
        File source = saveFile(fileName);
        return source.isFile() && source.delete();
    }

    public int countFileBodies(String fileName) {
        File source = saveFile(fileName);
        if (!source.isFile()) return 0;

        YamlConfiguration input = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection bodies = input.getConfigurationSection("group.bodies");
        return bodies == null ? 0 : bodies.getKeys(false).size();
    }

    public List<String> listSaveFiles() {
        if (!saveDirectory.isDirectory()) return List.of();
        File[] files = saveDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        List<String> names = new ArrayList<>();
        for (File savedFile : files) {
            String name = savedFile.getName();
            names.add(name.substring(0, name.length() - 4));
        }
        Collections.sort(names);
        return names;
    }

    private int saveGroupInto(ConfigurationSection group, String id) {
        List<Display> displays = ObjectUtil.displays.get(id);
        if (displays == null || displays.isEmpty()) return 0;

        Map<Display, Integer> displayIndexes = new HashMap<>();
        int index = 0;
        for (Box box : World.getInstance().boxes) {
            if (!box.getGroupId().filter(id::equals).isPresent()) continue;
            ConfigurationSection section = group.createSection("bodies." + index);
            Display primary = primaryDisplay(box);
            section.set("name", primary == null ? String.valueOf(index) : nameForDisplay(id, primary));
            saveBox(section, box);
            for (Display display : box.body.getAllDisplay()) {
                displayIndexes.put(display, index);
            }
            index++;
        }
        int lineCount = saveLines(group, displays);
        int constraintCount = saveConstraints(group, displayIndexes);
        if (index == 0 && lineCount == 0) {
            return 0;
        }
        return index + lineCount + constraintCount;
    }

    public boolean removeGroup(String id) {
        if (!config.contains("groups." + id)) return false;
        config.set("groups." + id, null);
        save();
        return true;
    }

    public void loadAll() {
        ConfigurationSection groups = config.getConfigurationSection("groups");
        if (groups == null) return;
        for (String id : groups.getKeys(false)) {
            loadGroup(id, groups.getConfigurationSection(id));
        }
    }

    private List<Display> loadGroup(String id, ConfigurationSection group) {
        List<Display> loadedDisplays = new ArrayList<>();
        if (group == null) return loadedDisplays;
        ConfigurationSection bodies = group.getConfigurationSection("bodies");
        List<Display> loadedBodyDisplays = new ArrayList<>();
        if (bodies != null) {
            for (String key : bodies.getKeys(false)) {
                ConfigurationSection section = bodies.getConfigurationSection(key);
                if (section == null) continue;
                Box box = loadBox(section);
                if (box == null) continue;
                box.body.addWithName(id, section.getString("name", String.valueOf(key)));
                World.getInstance().boxes.add(box);
                Display primary = primaryDisplay(box);
                if (primary != null) {
                    loadedBodyDisplays.add(primary);
                    loadedDisplays.add(primary);
                }
            }
        }
        loadLines(id, group.getConfigurationSection("lines"));
        loadConstraints(group.getConfigurationSection("constraints"), loadedBodyDisplays);
        return loadedDisplays;
    }

    private void saveBox(ConfigurationSection section, Box box) {
        if (box.body instanceof PhysSphereDisplay) {
            saveSphereBox(section, box, (PhysSphereDisplay) box.body);
            return;
        }
        if (box.body instanceof CompoundPhysBlockDisplay) {
            saveCompoundBox(section, box, (CompoundPhysBlockDisplay) box.body);
            return;
        }
        if (box.body instanceof PhysBlockDisplay) {
            saveSimpleBox(section, box, (PhysBlockDisplay) box.body);
        }
    }

    private void saveSimpleBox(ConfigurationSection section, Box box, PhysBlockDisplay body) {
        BlockDisplay display = body.getBlockDisplay();
        section.set("type", "block");
        section.set("world", display.getWorld().getName());
        section.set("block-data", display.getBlock().getAsString());
        setVector(section.createSection("position"), body.position);
        setQuaternion(section.createSection("orientation"), body.orientation);
        setVector(section.createSection("velocity"), body.velocity);
        setVector(section.createSection("angular-velocity"), body.rotation);
        setVector(section.createSection("scale"), new Vector3d(display.getTransformation().getScale()));
        setVector(section.createSection("half-size"), box.halfSize);
        section.set("mass", body.hasFiniteMass() ? body.getMass() : 0.0d);
    }

    private void saveSphereBox(ConfigurationSection section, Box box, PhysSphereDisplay body) {
        Display primary = primaryDisplay(box);
        if (!(primary instanceof BlockDisplay)) return;

        section.set("type", "sphere");
        section.set("world", primary.getWorld().getName());
        section.set("block-data", ((BlockDisplay) primary).getBlock().getAsString());
        setVector(section.createSection("position"), body.position);
        setQuaternion(section.createSection("orientation"), body.orientation);
        setVector(section.createSection("velocity"), body.velocity);
        setVector(section.createSection("angular-velocity"), body.rotation);
        section.set("radius", body.radius());
        section.set("detail", body.detail());
        setVector(section.createSection("half-size"), box.halfSize);
        section.set("mass", body.hasFiniteMass() ? body.getMass() : 0.0d);
    }

    private void saveCompoundBox(ConfigurationSection section, Box box, CompoundPhysBlockDisplay body) {
        Display primary = primaryDisplay(box);
        if (primary == null) return;

        section.set("type", "compound");
        section.set("world", primary.getWorld().getName());
        setVector(section.createSection("position"), body.position);
        setQuaternion(section.createSection("orientation"), body.orientation);
        setVector(section.createSection("velocity"), body.velocity);
        setVector(section.createSection("angular-velocity"), body.rotation);
        setVector(section.createSection("half-size"), box.halfSize);
        section.set("mass", body.hasFiniteMass() ? body.getMass() : 0.0d);

        int index = 0;
        for (CompoundPhysBlockDisplay.Part part : body.parts()) {
            if (part.display == null || !part.display.isValid()) continue;
            ConfigurationSection partSection = section.createSection("parts." + index++);
            partSection.set("block-data", part.display.getBlock().getAsString());
            setVector(partSection.createSection("local-offset"), part.localOffset);
            setVector(partSection.createSection("scale"), new Vector3d(part.scale));
            int collisionIndex = 0;
            for (CompoundPhysBlockDisplay.CollisionBox collisionBox : part.collisionBoxes) {
                ConfigurationSection collision = partSection.createSection("collision-boxes." + collisionIndex++);
                setVector(collision.createSection("local-center"), collisionBox.localCenter);
                setVector(collision.createSection("half-size"), collisionBox.halfSize);
            }
            partSection.set("has-collision-boxes", part.hasCollisionBoxes);
        }
    }

    private int saveLines(ConfigurationSection group, List<Display> displays) {
        int index = 0;
        for (Display display : displays) {
            if (!(display instanceof BlockDisplay)) continue;
            if (isPhysicsBodyDisplay(display)) continue;
            BlockDisplay line = (BlockDisplay) display;
            ConfigurationSection section = group.createSection("lines." + index);
            section.set("name", nameForDisplay(group.getName(), line));
            section.set("world", line.getWorld().getName());
            section.set("material", line.getBlock().getMaterial().name());
            setVector(section.createSection("position"), new Vector3d(line.getLocation().getX(), line.getLocation().getY(), line.getLocation().getZ()));
            Transformation transformation = line.getTransformation();
            setVector(section.createSection("translation"), new Vector3d(transformation.getTranslation()));
            setVector(section.createSection("scale"), new Vector3d(transformation.getScale()));
            setQuaternion(section.createSection("left-rotation"), new Quaterniond(
                    transformation.getLeftRotation().x,
                    transformation.getLeftRotation().y,
                    transformation.getLeftRotation().z,
                    transformation.getLeftRotation().w
            ));
            setQuaternion(section.createSection("right-rotation"), new Quaterniond(
                    transformation.getRightRotation().x,
                    transformation.getRightRotation().y,
                    transformation.getRightRotation().z,
                    transformation.getRightRotation().w
            ));
            index++;
        }
        return index;
    }

    private int saveConstraints(ConfigurationSection group, Map<Display, Integer> displayIndexes) {
        int index = 0;
        for (ConstraintDetail detail : World.getInstance().constraintDetails()) {
            Integer first = displayIndexes.get(detail.firstDisplay);
            Integer second = displayIndexes.get(detail.secondDisplay);
            if (first == null || second == null) continue;
            ConfigurationSection section = group.createSection("constraints." + index);
            section.set("type", detail.type);
            section.set("first", first);
            section.set("second", second);
            setVector(section.createSection("first-point"), new Vector3d(detail.first.getX(), detail.first.getY(), detail.first.getZ()));
            setVector(section.createSection("second-point"), new Vector3d(detail.second.getX(), detail.second.getY(), detail.second.getZ()));
            section.set("rest-length", detail.restLength);
            section.set("stiffness", detail.stiffness);
            section.set("damping", detail.damping);
            section.set("lower-linear", detail.lowerLinear);
            section.set("upper-linear", detail.upperLinear);
            section.set("lower-angular", detail.lowerAngular);
            section.set("upper-angular", detail.upperAngular);
            section.set("break-impulse", detail.breakImpulse);
            index++;
        }
        return index;
    }

    private Box loadBox(ConfigurationSection section) {
        String type = section.getString("type", "block");
        if (type.equalsIgnoreCase("compound")) {
            return loadCompoundBox(section);
        }
        if (type.equalsIgnoreCase("sphere")) {
            return loadSphereBox(section);
        }
        PhysBlockDisplay body = loadBody(section);
        if (body == null) return null;
        Vector3d halfSize = vector(section.getConfigurationSection("half-size"), new Vector3d(0.5d));
        return new Box(body, halfSize);
    }

    private Box loadSphereBox(ConfigurationSection section) {
        org.bukkit.World world = world(section.getString("world", ""));
        if (world == null) return null;

        Vector3d position = vector(section.getConfigurationSection("position"), new Vector3d());
        double radius = section.getDouble("radius", 0.5d);
        int detail = section.getInt("detail", plugin.getConfig().getInt(PhysConfig.DISPLAY_SPHERE_DETAIL, 3));
        BlockData blockData = blockData(section, "SLIME_BLOCK");
        PhysSphereDisplay body = new PhysSphereDisplay(new Location(world, position.x, position.y, position.z), radius, blockData.getMaterial(), detail);
        body.position.set(position);
        body.orientation.set(quaternion(section.getConfigurationSection("orientation")));
        body.velocity.set(vector(section.getConfigurationSection("velocity"), new Vector3d()));
        body.rotation.set(vector(section.getConfigurationSection("angular-velocity"), new Vector3d()));
        double mass = section.getDouble("mass", 1.0d);
        body.inverseMass = mass <= 0.0d ? 0.0d : 1.0d / mass;
        body.setAwake(true);
        body.tick();
        Vector3d halfSize = vector(section.getConfigurationSection("half-size"), new Vector3d(radius));
        return new Box(body, halfSize);
    }

    private PhysBlockDisplay loadBody(ConfigurationSection section) {
        org.bukkit.World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(section.getString("world", "")));
        }
        if (world == null) return null;

        Vector3d position = vector(section.getConfigurationSection("position"), new Vector3d());
        PhysBlockDisplay body = new PhysBlockDisplay(new Location(world, position.x, position.y, position.z));
        body.getBlockDisplay().setBlock(blockData(section, "COMMAND_BLOCK"));

        Transformation transformation = body.getBlockDisplay().getTransformation();
        Vector3d scale = vector(section.getConfigurationSection("scale"), new Vector3d(1.0d));
        transformation.getScale().set((float) scale.x, (float) scale.y, (float) scale.z);
        body.getBlockDisplay().setTransformation(transformation);

        body.position.set(position);
        body.orientation.set(quaternion(section.getConfigurationSection("orientation")));
        body.velocity.set(vector(section.getConfigurationSection("velocity"), new Vector3d()));
        body.rotation.set(vector(section.getConfigurationSection("angular-velocity"), new Vector3d()));
        double mass = section.getDouble("mass", 1.0d);
        body.inverseMass = mass <= 0.0d ? 0.0d : 1.0d / mass;
        body.setAwake(true);
        body.tick();
        return body;
    }

    private Box loadCompoundBox(ConfigurationSection section) {
        org.bukkit.World world = world(section.getString("world", ""));
        if (world == null) return null;

        Vector3d position = vector(section.getConfigurationSection("position"), new Vector3d());
        CompoundPhysBlockDisplay body = new CompoundPhysBlockDisplay(new Location(world, position.x, position.y, position.z));
        ConfigurationSection parts = section.getConfigurationSection("parts");
        if (parts == null) return null;

        for (String key : parts.getKeys(false)) {
            ConfigurationSection partSection = parts.getConfigurationSection(key);
            if (partSection == null) continue;
            Vector3d localOffset = vector(partSection.getConfigurationSection("local-offset"), new Vector3d());
            BlockDisplay display = SpawnUtil.spawnBlockDisplay(new Location(world, position.x + localOffset.x, position.y + localOffset.y, position.z + localOffset.z));
            display.setBlock(blockData(partSection, "COMMAND_BLOCK"));
            Vector3d scale = vector(partSection.getConfigurationSection("scale"), new Vector3d(1.0d));
            display.setTransformation(new Transformation(
                    new org.joml.Vector3f((float) -scale.x * 0.5f, (float) -scale.y * 0.5f, (float) -scale.z * 0.5f),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f((float) scale.x, (float) scale.y, (float) scale.z),
                    new org.joml.Quaternionf(0, 0, 0, 1)
            ));
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setPersistent(false);

            if (partSection.getBoolean("has-collision-boxes", false)) {
                body.addPart(display, localOffset, collisionBoxes(partSection.getConfigurationSection("collision-boxes")));
            } else {
                body.addPart(display, localOffset);
            }
        }

        body.position.set(position);
        body.orientation.set(quaternion(section.getConfigurationSection("orientation")));
        body.velocity.set(vector(section.getConfigurationSection("velocity"), new Vector3d()));
        body.rotation.set(vector(section.getConfigurationSection("angular-velocity"), new Vector3d()));
        double mass = section.getDouble("mass", 1.0d);
        body.inverseMass = mass <= 0.0d ? 0.0d : 1.0d / mass;
        body.setAwake(true);
        body.tick();
        Vector3d halfSize = vector(section.getConfigurationSection("half-size"), new Vector3d(0.5d));
        return new Box(body, halfSize);
    }

    private void loadLines(String id, ConfigurationSection lines) {
        if (lines == null) return;
        for (String key : lines.getKeys(false)) {
            ConfigurationSection section = lines.getConfigurationSection(key);
            if (section == null) continue;
            BlockDisplay line = loadLine(section);
            if (line == null) continue;
            ObjectUtil.addDisplay(id, section.getString("name", String.valueOf(key)), line);
        }
    }

    private BlockDisplay loadLine(ConfigurationSection section) {
        org.bukkit.World world = world(section.getString("world", ""));
        if (world == null) return null;
        Vector3d position = vector(section.getConfigurationSection("position"), new Vector3d());
        BlockDisplay display = SpawnUtil.spawnBlockDisplay(new Location(world, position.x, position.y, position.z));
        Material material = Material.matchMaterial(section.getString("material", "BLUE_CONCRETE"));
        if (material == null) material = Material.BLUE_CONCRETE;
        display.setBlock(Bukkit.createBlockData(material));
        Vector3d translation = vector(section.getConfigurationSection("translation"), new Vector3d());
        Vector3d scale = vector(section.getConfigurationSection("scale"), new Vector3d(1.0d));
        Quaterniond left = quaternion(section.getConfigurationSection("left-rotation"));
        Quaterniond right = quaternion(section.getConfigurationSection("right-rotation"));
        display.setTransformation(new Transformation(
                new org.joml.Vector3f((float) translation.x, (float) translation.y, (float) translation.z),
                new org.joml.Quaternionf((float) left.x, (float) left.y, (float) left.z, (float) left.w),
                new org.joml.Vector3f((float) scale.x, (float) scale.y, (float) scale.z),
                new org.joml.Quaternionf((float) right.x, (float) right.y, (float) right.z, (float) right.w)
        ));
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.setPersistent(false);
        return display;
    }

    private void loadConstraints(ConfigurationSection constraints, List<Display> bodies) {
        if (constraints == null) return;
        for (String key : constraints.getKeys(false)) {
            ConfigurationSection section = constraints.getConfigurationSection(key);
            if (section == null) continue;
            int firstIndex = section.getInt("first", -1);
            int secondIndex = section.getInt("second", -1);
            if (firstIndex < 0 || secondIndex < 0 || firstIndex >= bodies.size() || secondIndex >= bodies.size()) continue;
            Display first = bodies.get(firstIndex);
            Display second = bodies.get(secondIndex);
            Location firstPoint = location(first.getWorld(), section.getConfigurationSection("first-point"));
            Location secondPoint = location(second.getWorld(), section.getConfigurationSection("second-point"));
            String type = section.getString("type", "point").toLowerCase();
            if (type.equals("distance")) {
                World.getInstance().addDistanceConstraint(first, null, firstPoint, second, null, secondPoint, section.getDouble("rest-length", firstPoint.distance(secondPoint)));
            } else if (type.equals("spring")) {
                World.getInstance().addSpring(first, second, section.getDouble("rest-length", firstPoint.distance(secondPoint)), section.getDouble("stiffness", 25.0d), section.getDouble("damping", 2.0d));
            } else if (isTypedConstraint(type)) {
                World.getInstance().addTypedConstraint(
                        type,
                        first,
                        null,
                        firstPoint,
                        second,
                        null,
                        secondPoint,
                        section.getDouble("lower-linear", 0.0d),
                        section.getDouble("upper-linear", 0.0d),
                        section.getDouble("lower-angular", 0.0d),
                        section.getDouble("upper-angular", 0.0d),
                        section.getDouble("break-impulse", 0.0d)
                );
            } else {
                World.getInstance().connect(first, null, firstPoint, second, null, secondPoint);
            }
        }
    }

    private boolean isPhysicsBodyDisplay(Display display) {
        for (Box box : World.getInstance().boxes) {
            if (box.body.getAllDisplay().contains(display)) return true;
        }
        return false;
    }

    private String nameForDisplay(String group, Display display) {
        Map<String, List<Display>> names = ObjectUtil.groups.get(group);
        if (names == null) return group;
        for (Map.Entry<String, List<Display>> entry : names.entrySet()) {
            if (entry.getValue().contains(display)) return entry.getKey();
        }
        return group;
    }

    private boolean isTypedConstraint(String type) {
        return type.equals("hinge") || type.equals("slider") || type.equals("fixed") || type.equals("6dof") || type.equals("cone");
    }

    private Display primaryDisplay(Box box) {
        for (Display display : box.body.getAllDisplay()) {
            if (display != null && display.isValid() && !display.isDead()) {
                return display;
            }
        }
        return null;
    }

    private BlockData blockData(ConfigurationSection section, String fallback) {
        String serialized = section.getString("block-data");
        if (serialized != null && !serialized.isEmpty()) {
            try {
                return Bukkit.createBlockData(serialized);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Material material = Material.matchMaterial(section.getString("material", fallback));
        if (material == null) material = Material.matchMaterial(fallback);
        return Bukkit.createBlockData(material == null ? Material.COMMAND_BLOCK : material);
    }

    private List<CompoundPhysBlockDisplay.CollisionBox> collisionBoxes(ConfigurationSection section) {
        List<CompoundPhysBlockDisplay.CollisionBox> boxes = new ArrayList<>();
        if (section == null) return boxes;
        for (String key : section.getKeys(false)) {
            ConfigurationSection box = section.getConfigurationSection(key);
            if (box == null) continue;
            boxes.add(new CompoundPhysBlockDisplay.CollisionBox(
                    vector(box.getConfigurationSection("local-center"), new Vector3d()),
                    vector(box.getConfigurationSection("half-size"), new Vector3d(0.5d))
            ));
        }
        return boxes;
    }

    private File saveFile(String fileName) {
        String safeName = fileName == null ? "" : fileName.replace('\\', '/');
        int slash = safeName.lastIndexOf('/');
        if (slash >= 0) safeName = safeName.substring(slash + 1);
        safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isEmpty()) safeName = "save";
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".yml")) safeName += ".yml";
        return new File(saveDirectory, safeName);
    }

    private void ensureSaveDirectory() {
        if (!saveDirectory.isDirectory() && !saveDirectory.mkdirs()) {
            plugin.getLogger().warning("创建物理存档目录失败: " + saveDirectory.getAbsolutePath());
        }
    }

    private Location location(org.bukkit.World world, ConfigurationSection section) {
        Vector3d value = vector(section, new Vector3d());
        return new Location(world, value.x, value.y, value.z);
    }

    private org.bukkit.World world(String name) {
        org.bukkit.World world = Bukkit.getWorld(name);
        if (world == null && name != null && !name.isEmpty()) {
            world = Bukkit.createWorld(new WorldCreator(name));
        }
        return world;
    }

    private void setVector(ConfigurationSection section, Vector3d value) {
        section.set("x", value.x);
        section.set("y", value.y);
        section.set("z", value.z);
    }

    private Vector3d vector(ConfigurationSection section, Vector3d fallback) {
        if (section == null) return new Vector3d(fallback);
        return new Vector3d(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    private void setQuaternion(ConfigurationSection section, Quaterniond value) {
        section.set("x", value.x);
        section.set("y", value.y);
        section.set("z", value.z);
        section.set("w", value.w);
    }

    private Quaterniond quaternion(ConfigurationSection section) {
        if (section == null) return new Quaterniond();
        return new Quaterniond(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), section.getDouble("w", 1.0d));
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存持久化组失败: " + e.getMessage());
        }
    }

    public static class LoadResult {
        private final int count;
        private final List<Display> displays;

        private LoadResult(int count, List<Display> displays) {
            this.count = count;
            this.displays = List.copyOf(displays);
        }

        public int count() {
            return count;
        }

        public List<Display> displays() {
            return displays;
        }
    }
}
