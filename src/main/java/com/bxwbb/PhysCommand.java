package com.bxwbb;

import com.bxwbb.obj.Box;
import com.bxwbb.obj.CompoundPhysBlockDisplay;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.obj.PhysSphereDisplay;
import com.bxwbb.constraint.ConstraintSelectionStore;
import com.bxwbb.event.DisplayHitListener;
import com.bxwbb.persistence.PersistentGroupStore;
import com.bxwbb.phy.World;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.ObjectUtil;
import com.bxwbb.util.SpawnUtil;
import com.bxwbb.util.BlockDisplayScale;
import com.bxwbb.util.debug.LineDisplay;
import com.bxwbb.util.debug.DebugOverlayManager;
import com.bxwbb.util.debug.SpringDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class PhysCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_COMMAND = "physmc.command";
    private static final double ROD_MASS = 1.0d;
    private static final float ROD_WIDTH = 0.08f;
    private static final double ROD_SPAWN_CLEARANCE = 1.0d;
    private static final double ROPE_SEGMENT_MASS = 0.15d;
    private static final float ROPE_WIDTH = 0.05f;
    private static final float ROPE_VISUAL_OVERLAP = 0.08f;
    private static final int DEFAULT_ROPE_SEGMENTS = 8;
    private static final int MAX_ROPE_SEGMENTS = 32;
    private static final double SPRING_ENDPOINT_MASS = 1.0d;
    private static final float SPRING_ENDPOINT_SIZE = 0.18f;
    private static final double DEFAULT_SPRING_STIFFNESS = 25.0d;
    private static final double DEFAULT_SPRING_DAMPING = 2.0d;
    private static final int DEFAULT_SPRING_COILS = 6;
    private static final int MAX_SELECTION_BUILD_VOLUME = 32768;
    private static final int FILE_CONFIRM_BODY_THRESHOLD = 128;
    private static final Map<String, String> PRESET_KEY_ALIASES = presetKeyAliases();

    private final PhysMC plugin;
    private final List<String> commands = new ArrayList<>();
    private final Map<UUID, SelectionBuildTask> activeSelectionBuilds = new HashMap<>();
    private final Map<String, PendingFileOperation> pendingFileOperations = new HashMap<>();
    private final Map<String, PendingMaterialOperation> pendingMaterialOperations = new HashMap<>();
    private int commandIndex = 0;

    public PhysCommand(PhysMC plugin) {
        this.plugin = plugin;
        PluginCommand command = plugin.getCommand("physmc");
        if (command == null) {
            throw new IllegalStateException("plugin.yml 未注册 physmc 命令");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION_COMMAND)) {
            sendError(sender, "你没有权限使用 PhysMC 命令。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        try {
            commandIndex = 0;
            commands.clear();
            commands.addAll(Arrays.asList(args));

            String sub = consume().toLowerCase();
            switch (sub) {
                case "help":
                    sendUsage(sender);
                    return true;
                case "get":
                    return handleGet(sender);
                case "set":
                    return handleSet(sender);
                case "preset":
                case "预设":
                    return handlePreset(sender);
                case "spawn":
                    return handleSpawn(sender);
                case "remove":
                    return handleRemove(sender);
                case "pause":
                case "暂停":
                    return handlePause(sender);
                case "resume":
                case "continue":
                case "继续":
                    return handleResume(sender);
                case "step":
                case "步进":
                    return handleStep(sender);
                case "fast":
                case "快速":
                    return handleFast(sender);
                case "query":
                case "list":
                case "查询":
                    return handleQuery(sender);
                case "constraint":
                case "约束":
                    return handleConstraint(sender);
                case "persist":
                case "persistent":
                case "持久化":
                    return handlePersist(sender);
                case "file":
                case "savefile":
                case "存档":
                    return handleFileCommand(sender);
                case "debug":
                case "调试":
                    return handleDebug(sender);
                case "prop":
                case "property":
                case "物理量":
                    return handleProperty(sender);
                case "material":
                case "blocktype":
                case "材质":
                    return handleMaterial(sender);
                case "selection":
                case "select":
                case "选区":
                    return handleSelection(sender);
                case "selectiontool":
                case "selecttool":
                case "选区工具":
                    return handleSelectionTool(sender);
                case "clearselection":
                case "selectionclear":
                case "清空选区":
                    return handleClearSelection(sender);
                default:
                    sendError(sender, "未知子命令: " + sub + "。输入 /physmc help 查看帮助。");
                    return true;
            }
        } catch (NumberFormatException e) {
            sendError(sender, "数字参数格式错误，请检查输入。");
        } catch (IndexOutOfBoundsException e) {
            sendError(sender, "命令参数不足。输入 /physmc help 查看帮助。");
        } catch (IllegalArgumentException e) {
            sendError(sender, "命令参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            sendError(sender, "命令执行失败: " + e.getClass().getSimpleName());
            plugin.getLogger().warning("physmc command failed: " + e.getMessage());
        }
        return true;
    }

    private boolean handleGet(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "请输入配置键名。用法: /physmc get <键名>");
            return true;
        }

        String key = consume();
        if (key.startsWith(PhysConfig.ENTITY_PLAYER_MASS_PREFIX)) key = resolveMassKey(sender, key);
        if (!isConfigKey(key)) {
            sendError(sender, "未知配置键: " + key);
            return true;
        }

        sendConfigEntry(sender, key);
        return true;
    }

    private boolean handleSet(CommandSender sender) {
        if (remaining() < 2) {
            sendError(sender, "用法: /physmc set <键名> <值>");
            return true;
        }

        String key = consume();
        String value = consume();
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc set <键名> <值>");
            return true;
        }

        if (key.startsWith(PhysConfig.ENTITY_PLAYER_MASS_PREFIX)) key = resolveMassKey(sender, key);
        if (!isConfigKey(key)) {
            sendError(sender, "未知配置键: " + key);
            return true;
        }

        setConfigValue(key, value);
        plugin.saveConfig();
        sendConfigEntry(sender, key);
        return true;
    }

    private boolean handlePreset(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc preset <default|文件名>");
            return true;
        }
        String preset = consume();
        requireNoExtraArgs("/physmc preset <default|文件名>");

        if (preset.equalsIgnoreCase("default") || preset.equals("默认")) {
            int changed = resetPresetDefaults();
            plugin.saveConfig();
            sendSuccess(sender, "已重置物理预设参数，配置项数量: " + changed);
            return true;
        }

        File file = presetFile(preset);
        if (!file.isFile()) {
            sendError(sender, "没有找到预设文件: " + file.getName());
            return true;
        }
        int changed = applyPreset(file);
        plugin.saveConfig();
        sendSuccess(sender, "已应用预设 " + stripYamlSuffix(file.getName()) + "，配置项数量: " + changed);
        return true;
    }

    private boolean handleSelectionTool(CommandSender sender) {
        if (!hasNext()) {
            sendSuccess(sender, "下界之星选区工具当前状态: " + (DisplayHitListener.isSelectionToolEnabled() ? "启用" : "禁用"));
            return true;
        }
        String value = consume().toLowerCase();
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc selectiontool <on|off>");
            return true;
        }
        if (!value.equals("on") && !value.equals("off") && !value.equals("true") && !value.equals("false")
                && !value.equals("启用") && !value.equals("禁用")) {
            sendError(sender, "用法: /physmc selectiontool <on|off>");
            return true;
        }
        boolean enabled = value.equals("on") || value.equals("true") || value.equals("启用");
        DisplayHitListener.setSelectionToolEnabled(enabled);
        sendSuccess(sender, "下界之星选区工具已" + (enabled ? "启用。" : "禁用，并已清理所有选区显示。"));
        return true;
    }

    private boolean handleSelection(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc selection <tool|clear|build>");
            return true;
        }

        String action = consume().toLowerCase();
        switch (action) {
            case "tool":
            case "工具":
                return handleSelectionTool(sender);
            case "clear":
            case "清空":
                return handleClearSelection(sender);
            case "build":
            case "构建":
                return handleSelectionBuild(sender);
            default:
                sendError(sender, "未知选区子命令: " + action + "。用法: /physmc selection <tool|clear|build>");
                return true;
        }
    }

    private boolean handleProperty(CommandSender sender) {
        if (remaining() < 4) {
            sendError(sender, "用法: /physmc prop <组id> <名称> <get|set|add> <物理量> [坐标...] [值...]");
            return true;
        }
        String group = consume();
        String name = consume();
        String action = consume().toLowerCase();
        String property = normalizeProperty(consume());

        Box box = findBoxByGroupName(group, name);
        if (box == null) {
            sendError(sender, "找不到物理对象: " + group + " / " + name);
            return true;
        }

        if (action.equals("get") || action.equals("获取")) {
            requireNoExtraArgs("/physmc prop <组id> <名称> get <物理量>");
            sendProperty(sender, box, property);
            return true;
        }
        if (!action.equals("set") && !action.equals("add") && !action.equals("设置") && !action.equals("增加")) {
            sendError(sender, "操作必须是 get、set 或 add。");
            return true;
        }
        boolean add = action.equals("add") || action.equals("增加");
        mutateProperty(box, property, add);
        box.body.calculateDerivedData();
        box.body.tick();
        World.getInstance().syncBody(box);
        sendProperty(sender, box, property);
        return true;
    }

    private boolean handleSelectionBuild(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        if (!hasNext()) {
            sendError(sender, "用法: /physmc selection build <组id>");
            return true;
        }
        String group = consume();
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc selection build <组id>");
            return true;
        }

        Player player = (Player) sender;
        DisplayHitListener.SelectionBounds selection = DisplayHitListener.selectionBounds(player);
        if (selection == null) {
            sendError(sender, "请先用下界之星设置完整选区。");
            return true;
        }
        if (selection.volume() > MAX_SELECTION_BUILD_VOLUME) {
            sendError(sender, "选区体积过大: " + selection.volume() + "，当前上限为 " + MAX_SELECTION_BUILD_VOLUME + "。");
            return true;
        }

        UUID playerId = player.getUniqueId();
        SelectionBuildTask existing = activeSelectionBuilds.remove(playerId);
        if (existing != null) {
            existing.cancel();
            sendSuccess(sender, "已取消你之前未完成的选区构建任务。");
        }
        SelectionBuildTask task = new SelectionBuildTask(playerId, group, selection);
        activeSelectionBuilds.put(playerId, task);
        task.runTaskTimer(plugin, 1L, 1L);
        sendSuccess(sender, "已启动选区构建任务：体积 " + selection.volume() + "，组: " + group);
        return true;
    }

    private PhysBlockDisplay spawnSelectionBlock(Location center, BlockData blockData, double mass) {
        PhysBlockDisplay body = new PhysBlockDisplay(center);
        body.getBlockDisplay().setBlock(blockData);
        Transformation transformation = body.getBlockDisplay().getTransformation();
        transformation.getScale().set(1.0f, 1.0f, 1.0f);
        body.getBlockDisplay().setTransformation(transformation);
        body.position.set(center.getX(), center.getY(), center.getZ());
        body.velocity.zero();
        body.rotation.zero();
        body.inverseMass = mass <= 0.0d ? 0.0d : 1.0d / mass;
        body.setAwake(true);
        body.tick();
        return body;
    }

    private BlockDisplay spawnSelectionPart(Location center, BlockData blockData) {
        BlockDisplay display = SpawnUtil.spawnBlockDisplay(center);
        display.setBlock(blockData);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setPersistent(false);
        display.setTransformation(new Transformation(
                new Vector3f(-0.5f, -0.5f, -0.5f),
                new org.joml.Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new org.joml.Quaternionf(0, 0, 0, 1)
        ));
        return display;
    }

    private ItemDisplay spawnBannerPart(Location center, ItemStack item) {
        if (item == null) return null;

        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class);
        display.setItemStack(item);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setPersistent(false);
        display.setTransformation(new Transformation(
                new Vector3f(-0.5f, -0.5f, -0.5f),
                new org.joml.Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new org.joml.Quaternionf(0, 0, 0, 1)
        ));
        SpawnUtil.displays.add(display);
        return display;
    }

    private String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private void sendProperty(CommandSender sender, Box box, String property) {
        switch (property) {
            case "mass":
                sender.sendMessage(Component.text("质量: " + (box.body.hasFiniteMass() ? format(box.body.getMass()) : "0")).color(TextColor.color(0xCCCCCC)));
                return;
            case "invmass":
                sender.sendMessage(Component.text("逆质量: " + format(box.body.getInverseMass())).color(TextColor.color(0xCCCCCC)));
                return;
            case "position":
                sender.sendMessage(Component.text("位置: " + formatVector(box.body.position)).color(TextColor.color(0xCCCCCC)));
                return;
            case "velocity":
                sender.sendMessage(Component.text("速度: " + formatVector(box.body.velocity)).color(TextColor.color(0xCCCCCC)));
                return;
            case "rotation":
                sender.sendMessage(Component.text("角速度: " + formatVector(box.body.rotation)).color(TextColor.color(0xCCCCCC)));
                return;
            case "force":
                sender.sendMessage(Component.text("力: " + formatVector(box.body.forceAccum)).color(TextColor.color(0xCCCCCC)));
                return;
            case "torque":
                sender.sendMessage(Component.text("力矩: " + formatVector(box.body.torqueAccum)).color(TextColor.color(0xCCCCCC)));
                return;
            case "acceleration":
                sender.sendMessage(Component.text("加速度: " + formatVector(box.body.acceleration)).color(TextColor.color(0xCCCCCC)));
                return;
            case "orientation":
                sender.sendMessage(Component.text("旋转四元数: " + formatQuaternion(box.body.orientation)).color(TextColor.color(0xCCCCCC)));
                return;
            case "inertia":
                sender.sendMessage(Component.text("逆惯性张量: " + formatMatrix(box.body.inverseInertiaTensor)).color(TextColor.color(0xCCCCCC)));
                return;
            case "size":
                sender.sendMessage(Component.text("尺寸: " + formatVector(new Vector3d(box.halfSize).mul(2.0d))).color(TextColor.color(0xCCCCCC)));
                return;
            default:
                throw new IllegalArgumentException("未知物理量: " + property);
        }
    }

    private void mutateProperty(Box box, String property, boolean add) {
        switch (property) {
            case "mass":
                double mass = readSingleValue();
                if (mass < 0.0d) throw new IllegalArgumentException("质量不能小于 0");
                box.body.inverseMass = mass == 0.0d ? 0.0d : 1.0d / mass;
                return;
            case "invmass":
                double inverseMass = readSingleValue();
                if (inverseMass < 0.0d) throw new IllegalArgumentException("逆质量不能小于 0");
                box.body.inverseMass = add ? box.body.inverseMass + inverseMass : inverseMass;
                return;
            case "position":
                mutateVector(box.body.position, add);
                return;
            case "velocity":
                mutateVector(box.body.velocity, add);
                return;
            case "rotation":
                mutateVector(box.body.rotation, add);
                return;
            case "force":
                mutateVector(box.body.forceAccum, add);
                return;
            case "torque":
                mutateVector(box.body.torqueAccum, add);
                return;
            case "acceleration":
                mutateVector(box.body.acceleration, add);
                return;
            case "orientation":
                mutateQuaternion(box.body.orientation, add);
                box.body.orientation.normalize();
                return;
            case "inertia":
                mutateMatrix(box.body.inverseInertiaTensor, add);
                return;
            case "size":
                mutateSize(box, add);
                return;
            default:
                throw new IllegalArgumentException("未知物理量: " + property);
        }
    }

    private double readSingleValue() {
        if (remaining() != 1) throw new IllegalArgumentException("标量物理量需要 1 个值");
        return Double.parseDouble(consume());
    }

    private void mutateVector(Vector3d vector, boolean add) {
        List<Integer> indices = readCoordinateIndices(3);
        double[] values = readValues(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            double value = add ? vector.get(index) + values[i] : values[i];
            vector.setComponent(index, value);
        }
    }

    private void mutateQuaternion(Quaterniond quaternion, boolean add) {
        List<Integer> indices = readCoordinateIndices(4);
        double[] values = readValues(indices.size());
        double[] current = {quaternion.x, quaternion.y, quaternion.z, quaternion.w};
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            current[index] = add ? current[index] + values[i] : values[i];
        }
        quaternion.set(current[0], current[1], current[2], current[3]);
    }

    private void mutateMatrix(Matrix3d matrix, boolean add) {
        List<Integer> indices = readMatrixIndices();
        double[] values = readValues(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            int packed = indices.get(i);
            int row = packed / 3;
            int column = packed % 3;
            double value = add ? matrix.get(row, column) + values[i] : values[i];
            matrix.set(row, column, value);
        }
    }

    private void mutateSize(Box box, boolean add) {
        Vector3d size = new Vector3d(box.halfSize).mul(2.0d);
        List<Integer> indices = readCoordinateIndices(3);
        double[] values = readValues(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            double value = add ? size.get(index) + values[i] : values[i];
            if (value <= 0.0d) throw new IllegalArgumentException("尺寸必须大于 0");
            size.setComponent(index, value);
        }
        box.halfSize.set(size.mul(0.5d));
    }

    private List<Integer> readCoordinateIndices(int dimensions) {
        if (remaining() < 2) throw new IllegalArgumentException("缺少坐标和值");
        int coordinateCount = remaining() / 2;
        if (coordinateCount * 2 != remaining()) throw new IllegalArgumentException("坐标数量和值数量必须一致");
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < coordinateCount; i++) indices.add(parseCoordinateIndex(consume(), dimensions));
        return indices;
    }

    private List<Integer> readMatrixIndices() {
        if (remaining() < 2) throw new IllegalArgumentException("缺少矩阵坐标和值");
        int coordinateCount = remaining() / 2;
        if (coordinateCount * 2 != remaining()) throw new IllegalArgumentException("矩阵坐标数量和值数量必须一致");
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < coordinateCount; i++) indices.add(parseMatrixIndex(consume()));
        return indices;
    }

    private double[] readValues(int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) values[i] = Double.parseDouble(consume());
        return values;
    }

    private int parseCoordinateIndex(String token, int dimensions) {
        String value = token.toLowerCase();
        int index;
        switch (value) {
            case "x": index = 0; break;
            case "y": index = 1; break;
            case "z": index = 2; break;
            case "w": index = 3; break;
            default: index = Integer.parseInt(value) - 1; break;
        }
        if (index < 0 || index >= dimensions) throw new IllegalArgumentException("坐标超出范围: " + token);
        return index;
    }

    private int parseMatrixIndex(String token) {
        String[] parts = token.split("\\|");
        if (parts.length != 2) throw new IllegalArgumentException("矩阵坐标必须是 行|列，例如 1|2");
        int row = parseCoordinateIndex(parts[0], 3);
        int column = parseCoordinateIndex(parts[1], 3);
        return row * 3 + column;
    }

    private String normalizeProperty(String value) {
        String property = value.toLowerCase();
        if (property.equals("质量")) return "mass";
        if (property.equals("逆质量")) return "invmass";
        if (property.equals("位置") || property.equals("pos")) return "position";
        if (property.equals("速度") || property.equals("vel")) return "velocity";
        if (property.equals("角速度") || property.equals("angvel")) return "rotation";
        if (property.equals("力")) return "force";
        if (property.equals("力矩")) return "torque";
        if (property.equals("加速度")) return "acceleration";
        if (property.equals("旋转") || property.equals("四元数") || property.equals("orient")) return "orientation";
        if (property.equals("惯性") || property.equals("惯性张量")) return "inertia";
        if (property.equals("尺寸") || property.equals("大小")) return "size";
        return property;
    }

    private int getConfigInt(String path) {
        if (plugin.getConfig().contains(path)) return plugin.getConfig().getInt(path);
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && plugin.getConfig().contains(legacyPath)) return plugin.getConfig().getInt(legacyPath);
        return ((Number) PhysConfig.get(path).defaultValue).intValue();
    }

    private double getConfigDouble(String path) {
        if (plugin.getConfig().contains(path)) return plugin.getConfig().getDouble(path);
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && plugin.getConfig().contains(legacyPath)) return plugin.getConfig().getDouble(legacyPath);
        return ((Number) PhysConfig.get(path).defaultValue).doubleValue();
    }

    private boolean getConfigBoolean(String path) {
        if (plugin.getConfig().contains(path)) return plugin.getConfig().getBoolean(path);
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && plugin.getConfig().contains(legacyPath)) return plugin.getConfig().getBoolean(legacyPath);
        return (Boolean) PhysConfig.get(path).defaultValue;
    }

    private class SelectionBuildTask extends BukkitRunnable {
        private final UUID playerId;
        private final String group;
        private final DisplayHitListener.SelectionBounds selection;
        private final int blocksPerTick;
        private final boolean cullInternal;
        private final Set<String> solidBlocks = new HashSet<>();
        private final Map<String, BlockData> blockData = new HashMap<>();
        private final Map<String, List<CompoundPhysBlockDisplay.CollisionBox>> collisionBoxes = new HashMap<>();
        private final Map<String, ItemStack> bannerItems = new HashMap<>();
        private final List<CompoundPartBuild> parts = new ArrayList<>();
        private int x;
        private int y;
        private int z;
        private int scanned;
        private int removed;
        private int created;
        private int progressTicker;
        private Phase phase = Phase.SCAN;

        private SelectionBuildTask(UUID playerId, String group, DisplayHitListener.SelectionBounds selection) {
            this.playerId = playerId;
            this.group = group;
            this.selection = selection;
            this.blocksPerTick = Math.max(1, getConfigInt(PhysConfig.PERFORMANCE_SELECTION_BUILD_BLOCKS_PER_TICK));
            this.cullInternal = getConfigBoolean(PhysConfig.PERFORMANCE_SELECTION_BUILD_CULL_INTERNAL);
            this.x = selection.minX;
            this.y = selection.minY;
            this.z = selection.minZ;
        }

        @Override
        public void run() {
            int budget = blocksPerTick;
            while (budget-- > 0 && phase != Phase.DONE) {
                if (phase == Phase.SCAN) scanOne();
                else buildOne();
            }
            progressTicker++;
            if (progressTicker >= 20 && phase != Phase.DONE) {
                progressTicker = 0;
                notifyPlayer("选区构建中: " + phase.label + " " + scanned + "/" + selection.volume() + "，已生成 " + created + "。");
            }
            if (phase == Phase.DONE) finish();
        }

        private void scanOne() {
            Block block = selection.world.getBlockAt(x, y, z);
            if (!block.isEmpty() && block.getType() != Material.AIR) {
                String key = blockKey(x, y, z);
                solidBlocks.add(key);
                blockData.put(key, block.getBlockData().clone());
                collisionBoxes.put(key, collisionBoxes(block));
                ItemStack bannerItem = bannerItem(block);
                if (bannerItem != null) bannerItems.put(key, bannerItem);
            }
            scanned++;
            advanceOrSwitch();
        }

        private void buildOne() {
            String key = blockKey(x, y, z);
            if (solidBlocks.contains(key)) {
                Block block = selection.world.getBlockAt(x, y, z);
                block.setType(Material.AIR, false);
                removed++;

                if (!cullInternal || isSurfaceBlock(x, y, z)) {
                    Location center = block.getLocation().add(0.5d, 0.5d, 0.5d);
                    BlockDisplay display = spawnSelectionPart(center, blockData.get(key));
                    ItemDisplay bannerDisplay = spawnBannerPart(center, bannerItems.get(key));
                    if (bannerDisplay != null) display.setInvisible(true);
                    parts.add(new CompoundPartBuild(display, new Vector3d(center.getX(), center.getY(), center.getZ()), collisionBoxes.getOrDefault(key, List.of()), bannerDisplay == null ? List.of() : List.of(bannerDisplay)));
                    created++;
                }
            }
            advanceOrFinish();
        }

        private boolean isSurfaceBlock(int x, int y, int z) {
            int[][] offsets = {
                    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
            };
            for (int[] offset : offsets) {
                if (!isFullyOccludingBlock(blockKey(x + offset[0], y + offset[1], z + offset[2]))) {
                    return true;
                }
            }
            return false;
        }

        private boolean isFullyOccludingBlock(String key) {
            BlockData data = blockData.get(key);
            if (data == null || !data.getMaterial().isOccluding()) {
                return false;
            }

            List<CompoundPhysBlockDisplay.CollisionBox> boxes = collisionBoxes.get(key);
            if (boxes == null) {
                return false;
            }
            for (CompoundPhysBlockDisplay.CollisionBox box : boxes) {
                if (isFullBlockCollisionBox(box)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isFullBlockCollisionBox(CompoundPhysBlockDisplay.CollisionBox box) {
            return nearly(box.localCenter.x, 0.0d)
                    && nearly(box.localCenter.y, 0.0d)
                    && nearly(box.localCenter.z, 0.0d)
                    && nearly(box.halfSize.x, 0.5d)
                    && nearly(box.halfSize.y, 0.5d)
                    && nearly(box.halfSize.z, 0.5d);
        }

        private boolean nearly(double left, double right) {
            return Math.abs(left - right) < 1.0e-6d;
        }

        private void advanceOrSwitch() {
            if (advance()) return;
            phase = Phase.BUILD;
            x = selection.minX;
            y = selection.minY;
            z = selection.minZ;
        }

        private void advanceOrFinish() {
            if (advance()) return;
            phase = Phase.DONE;
        }

        private boolean advance() {
            z++;
            if (z <= selection.maxZ) return true;
            z = selection.minZ;
            y++;
            if (y <= selection.maxY) return true;
            y = selection.minY;
            x++;
            return x <= selection.maxX;
        }

        private void finish() {
            cancel();
            activeSelectionBuilds.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (created > 0) {
                createCompoundBody();
            }
            if (created > 0 && player != null) {
                DisplayHitListener.clearSelection(player);
            }
            notifyPlayer(created == 0
                    ? "选区构建结束：没有生成物理方块。"
                    : "选区构建完成：扫描 " + scanned + "，删除 " + removed + "，生成 " + created + " 个可见方块，合成为 1 个复合刚体。组: " + group);
        }

        private void createCompoundBody() {
            Vector3d center = new Vector3d();
            for (CompoundPartBuild part : parts) {
                center.add(part.worldCenter);
            }
            center.div(parts.size());

            Location bodyLocation = new Location(selection.world, center.x, center.y, center.z);
            CompoundPhysBlockDisplay compound = new CompoundPhysBlockDisplay(bodyLocation);
            for (CompoundPartBuild part : parts) {
                compound.addPart(part.display, new Vector3d(part.worldCenter).sub(center), part.collisionBoxes, part.extraDisplays);
            }
            compound.inverseMass = 1.0d / Math.max(1.0d, solidBlocks.size());
            compound.setAwake(true);
            compound.tick();
            compound.addWithName(group, "compound");

            Vector3d halfSize = boundsHalfSize();
            PhysManager.getInstance().world.boxes.add(new Box(compound, halfSize));
        }

        private Vector3d boundsHalfSize() {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (CompoundPartBuild part : parts) {
                minX = Math.min(minX, part.worldCenter.x - 0.5d);
                minY = Math.min(minY, part.worldCenter.y - 0.5d);
                minZ = Math.min(minZ, part.worldCenter.z - 0.5d);
                maxX = Math.max(maxX, part.worldCenter.x + 0.5d);
                maxY = Math.max(maxY, part.worldCenter.y + 0.5d);
                maxZ = Math.max(maxZ, part.worldCenter.z + 0.5d);
            }
            return new Vector3d(
                    Math.max(0.5d, (maxX - minX) * 0.5d),
                    Math.max(0.5d, (maxY - minY) * 0.5d),
                    Math.max(0.5d, (maxZ - minZ) * 0.5d)
            );
        }

        private List<CompoundPhysBlockDisplay.CollisionBox> collisionBoxes(Block block) {
            List<CompoundPhysBlockDisplay.CollisionBox> boxes = new ArrayList<>();
            for (BoundingBox boundingBox : block.getCollisionShape().getBoundingBoxes()) {
                double width = boundingBox.getWidthX();
                double height = boundingBox.getHeight();
                double depth = boundingBox.getWidthZ();
                if (width <= 0.0d || height <= 0.0d || depth <= 0.0d) continue;

                boxes.add(new CompoundPhysBlockDisplay.CollisionBox(
                        new Vector3d(
                                boundingBox.getCenterX() - 0.5d,
                                boundingBox.getCenterY() - 0.5d,
                                boundingBox.getCenterZ() - 0.5d
                        ),
                        new Vector3d(width * 0.5d, height * 0.5d, depth * 0.5d)
                ));
            }
            return boxes;
        }

        private ItemStack bannerItem(Block block) {
            BlockState state = block.getState(false);
            if (!(state instanceof Banner)) return null;

            Banner banner = (Banner) state;
            Material itemMaterial = bannerItemMaterial(block.getType());
            if (itemMaterial == null) return null;

            ItemStack item = new ItemStack(itemMaterial);
            if (item.getItemMeta() instanceof BannerMeta) {
                BannerMeta meta = (BannerMeta) item.getItemMeta();
                meta.setPatterns(new ArrayList<Pattern>(banner.getPatterns()));
                item.setItemMeta(meta);
            }
            return item;
        }

        private Material bannerItemMaterial(Material blockMaterial) {
            if (blockMaterial == null) return null;
            if (blockMaterial.name().endsWith("_WALL_BANNER")) {
                return Material.matchMaterial(blockMaterial.name().replace("_WALL_BANNER", "_BANNER"));
            }
            return blockMaterial.name().endsWith("_BANNER") ? blockMaterial : null;
        }

        private void notifyPlayer(String message) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                sendSuccess(player, message);
            }
        }
    }

    private enum Phase {
        SCAN("扫描"),
        BUILD("构建"),
        DONE("完成");

        private final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    private static class CompoundPartBuild {
        private final BlockDisplay display;
        private final Vector3d worldCenter;
        private final List<CompoundPhysBlockDisplay.CollisionBox> collisionBoxes;
        private final List<Display> extraDisplays;

        private CompoundPartBuild(BlockDisplay display, Vector3d worldCenter, List<CompoundPhysBlockDisplay.CollisionBox> collisionBoxes, List<Display> extraDisplays) {
            this.display = display;
            this.worldCenter = worldCenter;
            this.collisionBoxes = List.copyOf(collisionBoxes);
            this.extraDisplays = List.copyOf(extraDisplays);
        }
    }

    private enum FileOperationType {
        SAVE,
        LOAD
    }

    private static class PendingFileOperation {
        private static final long EXPIRES_AFTER_MILLIS = 60_000L;

        private final FileOperationType type;
        private final String group;
        private final String fileName;
        private final int bodyCount;
        private final boolean put;
        private final long createdAt;

        private PendingFileOperation(FileOperationType type, String group, String fileName, int bodyCount, boolean put) {
            this.type = type;
            this.group = group;
            this.fileName = fileName;
            this.bodyCount = bodyCount;
            this.put = put;
            this.createdAt = System.currentTimeMillis();
        }

        private static PendingFileOperation save(String group, String fileName, int bodyCount) {
            return new PendingFileOperation(FileOperationType.SAVE, group, fileName, bodyCount, false);
        }

        private static PendingFileOperation load(String group, String fileName, int bodyCount, boolean put) {
            return new PendingFileOperation(FileOperationType.LOAD, group, fileName, bodyCount, put);
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRES_AFTER_MILLIS;
        }
    }

    private static class PendingMaterialOperation {
        private static final long EXPIRES_AFTER_MILLIS = 60_000L;

        private final String group;
        private final String name;
        private final int displayCount;
        private final long createdAt;

        private PendingMaterialOperation(String group, String name, int displayCount) {
            this.group = group;
            this.name = name;
            this.displayCount = displayCount;
            this.createdAt = System.currentTimeMillis();
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRES_AFTER_MILLIS;
        }
    }

    private boolean handleClearSelection(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc clearselection");
            return true;
        }
        boolean cleared = DisplayHitListener.clearSelection((Player) sender);
        sendSuccess(sender, cleared ? "已清空你的选区。" : "你当前没有选区。");
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        if (!hasNext()) {
            sendError(sender, "请输入 spawn 类型。用法: /physmc spawn block|line|rod");
            return true;
        }

        Player player = (Player) sender;
        String type = consume().toLowerCase();
        switch (type) {
            case "help":
                sender.sendMessage("block - 刚体方块，用法: /physmc spawn block <组id> <名称> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
                sender.sendMessage("line - 调试线段，用法: /physmc spawn line <组id> <名称> <x1 y1 z1> <x2 y2 z2>");
                sender.sendMessage("rod - 轻杆线段，用法: /physmc spawn rod <组id> <名称> <长度>");
                return true;
            case "line":
                return spawnLine(sender, player);
            case "rod":
                return spawnRod(sender, player);
            case "rope":
                return spawnRope(sender, player);
            case "spring":
                return spawnSpring(sender, player);
            case "block":
                return spawnBlock(sender, player);
            case "sphere":
                return spawnSphere(sender, player);
            default:
                sendError(sender, "未知 spawn 类型: " + type);
                return true;
        }
    }

    private boolean spawnLine(CommandSender sender, Player player) {
        if (remaining() < 8) {
            sendError(sender, "用法: /physmc spawn line <组id> <名称> <x1 y1 z1> <x2 y2 z2>");
            return true;
        }

        String group = consume();
        String name = consume();
        Location start = parseMCLocation(consume(), consume(), consume(), player);
        Location end = parseMCLocation(consume(), consume(), consume(), player);
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn line <组id> <名称> <x1 y1 z1> <x2 y2 z2>");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        if (material.equals(Material.AIR)) material = Material.STONE;
        LineDisplay lineDisplay = new LineDisplay(start, end, material);
        lineDisplay.addWithName(group, name);
        sendSuccess(sender, "创建成功。");
        return true;
    }

    private boolean spawnRod(CommandSender sender, Player player) {
        if (remaining() < 3) {
            sendError(sender, "用法: /physmc spawn rod <组id> <名称> <长度>");
            return true;
        }

        String group = consume();
        String name = consume();
        double length = Double.parseDouble(consume());
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn rod <组id> <名称> <长度>");
            return true;
        }
        if (length <= 0) {
            sendError(sender, "长度必须大于 0。");
            return true;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location center = player.getEyeLocation().add(direction.clone().multiply(ROD_SPAWN_CLEARANCE + length * 0.5d));
        PhysBlockDisplay rod = new PhysBlockDisplay(center);
        rod.getBlockDisplay().setBlock(Bukkit.createBlockData(Material.GREEN_CONCRETE));
        Transformation transformation = rod.getBlockDisplay().getTransformation();
        transformation.getScale().set((float) length, ROD_WIDTH, ROD_WIDTH);
        rod.getBlockDisplay().setTransformation(transformation);
        rod.orientation.set(rotationFromX(direction));
        rod.position.set(center.getX(), center.getY(), center.getZ());
        rod.velocity.zero();
        rod.rotation.zero();
        rod.inverseMass = 1.0d / ROD_MASS;
        rod.setAwake(true);
        rod.tick();
        rod.addWithName(group, name);
        PhysManager.getInstance().world.boxes.add(new Box(rod));
        sendSuccess(sender, "创建成功。");
        return true;
    }

    private boolean spawnRope(CommandSender sender, Player player) {
        if (remaining() < 3) {
            sendError(sender, "用法: /physmc spawn rope <组id> <名称> <长度> [段数]");
            return true;
        }

        String group = consume();
        String name = consume();
        double length = Double.parseDouble(consume());
        int segments = hasNext() ? Integer.parseInt(consume()) : DEFAULT_ROPE_SEGMENTS;
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn rope <组id> <名称> <长度> [段数]");
            return true;
        }
        if (length <= 0.0d) {
            sendError(sender, "长度必须大于 0。");
            return true;
        }
        if (segments < 2 || segments > MAX_ROPE_SEGMENTS) {
            sendError(sender, "段数必须在 2 到 " + MAX_ROPE_SEGMENTS + " 之间。");
            return true;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location origin = player.getEyeLocation().add(direction.clone().multiply(ROD_SPAWN_CLEARANCE));
        double segmentLength = length / segments;
        List<PhysBlockDisplay> ropeSegments = new ArrayList<>();

        for (int i = 0; i < segments; i++) {
            Location center = origin.clone().add(direction.clone().multiply(segmentLength * (i + 0.5d)));
            PhysBlockDisplay segment = new PhysBlockDisplay(center);
            segment.getBlockDisplay().setBlock(Bukkit.createBlockData(Material.BROWN_WOOL));
            Transformation transformation = segment.getBlockDisplay().getTransformation();
            transformation.getScale().set((float) segmentLength + ROPE_VISUAL_OVERLAP, ROPE_WIDTH, ROPE_WIDTH);
            segment.getBlockDisplay().setTransformation(transformation);
            segment.orientation.set(rotationFromX(direction));
            segment.position.set(center.getX(), center.getY(), center.getZ());
            segment.velocity.zero();
            segment.rotation.zero();
            segment.inverseMass = 1.0d / ROPE_SEGMENT_MASS;
            segment.setAwake(true);
            segment.tick();
            segment.addWithName(group, name);
            PhysManager.getInstance().world.boxes.add(new Box(segment, new Vector3d(segmentLength * 0.5d, ROPE_WIDTH * 0.5d, ROPE_WIDTH * 0.5d)));
            ropeSegments.add(segment);
        }

        ObjectUtil.addGrabbableGroup(ropeSegments.stream()
                .map(PhysBlockDisplay::getBlockDisplay)
                .collect(Collectors.toList()));
        connectRopeSegments(origin, direction, segmentLength, ropeSegments);
        sendSuccess(sender, "轻绳创建成功。");
        return true;
    }

    private void connectRopeSegments(Location origin, Vector direction, double segmentLength, List<PhysBlockDisplay> ropeSegments) {
        for (int i = 0; i < ropeSegments.size() - 1; i++) {
            PhysBlockDisplay first = ropeSegments.get(i);
            PhysBlockDisplay second = ropeSegments.get(i + 1);
            if (first.getBlockDisplay().isDead() || second.getBlockDisplay().isDead()) continue;

            Location joint = origin.clone().add(direction.clone().multiply(segmentLength * (i + 1)));
            World.getInstance().connect(
                    first.getBlockDisplay(),
                    null,
                    joint,
                    second.getBlockDisplay(),
                    null,
                    joint,
                    true
            );
        }
    }

    private boolean spawnSpring(CommandSender sender, Player player) {
        if (remaining() < 3) {
            sendError(sender, "用法: /physmc spawn spring <组id> <名称> <长度> [刚度] [阻尼] [圈数]");
            return true;
        }

        String group = consume();
        String name = consume();
        double length = Double.parseDouble(consume());
        double stiffness = hasNext() ? Double.parseDouble(consume()) : DEFAULT_SPRING_STIFFNESS;
        double damping = hasNext() ? Double.parseDouble(consume()) : DEFAULT_SPRING_DAMPING;
        int coils = hasNext() ? Integer.parseInt(consume()) : DEFAULT_SPRING_COILS;
        double endpointMass = hasNext() ? Double.parseDouble(consume()) : SPRING_ENDPOINT_MASS;
        double endpointSize = hasNext() ? Double.parseDouble(consume()) : SPRING_ENDPOINT_SIZE;
        double radius = hasNext() ? Double.parseDouble(consume()) : endpointSize * 0.7d;
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn spring <组id> <名称> <长度> [刚度] [阻尼] [圈数]");
            return true;
        }
        if (length <= 0.0d || stiffness < 0.0d || damping < 0.0d || coils <= 0 || endpointMass <= 0.0d || endpointSize <= 0.0d || radius <= 0.0d) {
            sendError(sender, "长度、圈数、端点质量、端点大小和半径必须大于 0，刚度和阻尼不能小于 0。");
            return true;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation().add(direction.clone().multiply(ROD_SPAWN_CLEARANCE));
        Location end = start.clone().add(direction.clone().multiply(length));
        PhysBlockDisplay first = spawnSpringEndpoint(start, endpointMass, endpointSize);
        PhysBlockDisplay second = spawnSpringEndpoint(end, endpointMass, endpointSize);
        SpringDisplay springDisplay = new SpringDisplay(first.getBlockDisplay(), second.getBlockDisplay(), coils, radius);

        first.addWithName(group, name);
        second.addWithName(group, name);
        springDisplay.addWithName(group, name);
        PhysManager.getInstance().world.boxes.add(new Box(first));
        PhysManager.getInstance().world.boxes.add(new Box(second));
        World.getInstance().addSpring(first.getBlockDisplay(), second.getBlockDisplay(), length, stiffness, damping);
        sendSuccess(sender, "弹簧创建成功。");
        return true;
    }

    private PhysBlockDisplay spawnSpringEndpoint(Location location, double mass, double size) {
        PhysBlockDisplay endpoint = new PhysBlockDisplay(location);
        endpoint.getBlockDisplay().setBlock(Bukkit.createBlockData(Material.PURPLE_CONCRETE));
        Transformation transformation = endpoint.getBlockDisplay().getTransformation();
        transformation.getScale().set((float) size, (float) size, (float) size);
        endpoint.getBlockDisplay().setTransformation(transformation);
        endpoint.position.set(location.getX(), location.getY(), location.getZ());
        endpoint.velocity.zero();
        endpoint.rotation.zero();
        endpoint.inverseMass = 1.0d / mass;
        endpoint.setAwake(true);
        endpoint.tick();
        return endpoint;
    }

    private Quaterniond rotationFromX(Vector direction) {
        Vector from = new Vector(1, 0, 0);
        Vector to = direction.clone().normalize();
        double dot = Math.max(-1.0d, Math.min(1.0d, from.dot(to)));
        if (dot > 0.9999d) return new Quaterniond();
        if (dot < -0.9999d) return new Quaterniond().rotateY(Math.PI);

        Vector axis = from.getCrossProduct(to).normalize();
        return new Quaterniond().rotateAxis(Math.acos(dot), axis.getX(), axis.getY(), axis.getZ());
    }

    private boolean spawnBlock(CommandSender sender, Player player) {
        if (remaining() < 6) {
            sendError(sender, "用法: /physmc spawn block <组id> <名称> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
            return true;
        }

        String group = consume();
        String name = consume();
        double scaleX = Double.parseDouble(consume());
        double scaleY = Double.parseDouble(consume());
        double scaleZ = Double.parseDouble(consume());
        double mass = Double.parseDouble(consume());
        if (scaleX <= 0 || scaleY <= 0 || scaleZ <= 0) {
            sendError(sender, "大小 X/Y/Z 都必须大于 0。");
            return true;
        }
        if (mass < 0) {
            sendError(sender, "质量不能小于 0。");
            return true;
        }
        if (remaining() != 0 && remaining() != 3 && remaining() != 6) {
            sendError(sender, "速度参数必须是 3 个一组。用法: /physmc spawn block <组id> <名称> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
            return true;
        }

        PhysBlockDisplay physBlockDisplay = new PhysBlockDisplay(player.getLocation());
        Transformation transformation = physBlockDisplay.getBlockDisplay().getTransformation();
        transformation.getScale().set((float) scaleX, (float) scaleY, (float) scaleZ);
        physBlockDisplay.getBlockDisplay().setTransformation(transformation);
        physBlockDisplay.inverseMass = mass == 0.0d ? 0.0d : 1.0d / mass;

        if (remaining() >= 3) {
            physBlockDisplay.velocity.set(Double.parseDouble(consume()), Double.parseDouble(consume()), Double.parseDouble(consume()));
        }
        if (remaining() >= 3) {
            physBlockDisplay.rotation.set(Double.parseDouble(consume()), Double.parseDouble(consume()), Double.parseDouble(consume()));
        }

        physBlockDisplay.setAwake(true);
        physBlockDisplay.addWithName(group, name);
        PhysManager.getInstance().world.boxes.add(new Box(physBlockDisplay));
        sendSuccess(sender, "创建成功。");
        return true;
    }

    private boolean spawnSphere(CommandSender sender, Player player) {
        if (remaining() < 4) {
            sendError(sender, "用法: /physmc spawn sphere <组id> <名称> <半径> <质量> [材质] [精细度]");
            return true;
        }

        String group = consume();
        String name = consume();
        double radius = Double.parseDouble(consume());
        double mass = Double.parseDouble(consume());
        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        int detail = PhysMC.getPlugin(PhysMC.class).getConfig().getInt(PhysConfig.DISPLAY_SPHERE_DETAIL, 3);
        if (hasNext()) {
            String materialOrDetail = consume();
            Material parsedMaterial = Material.matchMaterial(materialOrDetail);
            if (parsedMaterial == null && isInteger(materialOrDetail)) {
                detail = Integer.parseInt(materialOrDetail);
            } else {
                material = parsedMaterial;
                if (hasNext()) {
                    detail = Integer.parseInt(consume());
                }
            }
        }
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn sphere <组id> <名称> <半径> <质量> [材质] [精细度]");
            return true;
        }
        if (radius <= 0.0d) {
            sendError(sender, "半径必须大于 0。");
            return true;
        }
        if (mass < 0.0d) {
            sendError(sender, "质量不能小于 0。");
            return true;
        }
        if (detail < 0) {
            sendError(sender, "精细度不能小于 0。");
            return true;
        }
        if (material == null || material == Material.AIR || !material.isBlock()) {
            material = Material.SLIME_BLOCK;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location center = player.getEyeLocation().add(direction.multiply(radius + 1.0d));
        PhysSphereDisplay sphere = new PhysSphereDisplay(center, radius, material, detail);
        sphere.inverseMass = mass == 0.0d ? 0.0d : 1.0d / mass;
        sphere.velocity.zero();
        sphere.rotation.zero();
        sphere.setAwake(true);
        sphere.tick();
        sphere.addWithName(group, name);
        PhysManager.getInstance().world.boxes.add(new Box(sphere, new Vector3d(radius)));
        sendSuccess(sender, "球体创建成功。");
        return true;
    }

    private boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "请输入要删除的组 ID。用法: /physmc remove <组id> [名称]");
            return true;
        }

        String group = consume();
        String name = hasNext() ? consume() : null;
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc remove <组id> [名称]");
            return true;
        }

        ObjectUtil.removeDisplay(group, name);
        World.getInstance().boxes.removeIf(rigidBody -> {
            for (Display display : rigidBody.body.getAllDisplay()) {
                if (!display.isDead()) return false;
            }
            return true;
        });
        sendSuccess(sender, name == null ? "已删除组。" : "已删除组内对象。");
        return true;
    }

    private boolean handlePersist(CommandSender sender) {
        if (remaining() < 2) {
            sendError(sender, "用法: /physmc persist <save|remove> <组id>");
            return true;
        }
        String action = consume().toLowerCase();
        String id = consume();
        requireNoExtraArgs("/physmc persist <save|remove> <组id>");

        if (action.equals("save") || action.equals("保存")) {
            int saved = plugin.persistentGroupStore().saveGroup(id);
            if (saved == 0) {
                sendError(sender, "没有找到可持久化的刚体组: " + id);
                return true;
            }
            sendSuccess(sender, "已持久化组 " + id + "，保存刚体 " + saved + " 个。");
            return true;
        }
        if (action.equals("remove") || action.equals("delete") || action.equals("删除") || action.equals("取消")) {
            if (!plugin.persistentGroupStore().removeGroup(id)) {
                sendError(sender, "该组没有持久化记录: " + id);
                return true;
            }
            sendSuccess(sender, "已取消组 " + id + " 的持久化记录，当前场景实体不会被删除。");
            return true;
        }
        sendError(sender, "未知持久化操作: " + action);
        return true;
    }

    private boolean handleFile(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc file <save|load|list> [...]");
            return true;
        }

        String action = consume().toLowerCase();
        if (action.equals("list") || action.equals("列表")) {
            requireNoExtraArgs("/physmc file list");
            List<String> files = plugin.persistentGroupStore().listSaveFiles();
            if (files.isEmpty()) {
                sendSuccess(sender, "当前没有物理存档文件。");
                return true;
            }
            sender.sendMessage(Component.text("物理存档文件: " + String.join(", ", files)).color(TextColor.color(0xFFAA00)));
            return true;
        }

        if (action.equals("save") || action.equals("保存")) {
            if (remaining() < 2) {
                sendError(sender, "用法: /physmc file save <组id> <文件名>");
                return true;
            }
            String group = consume();
            String fileName = consume();
            requireNoExtraArgs("/physmc file save <组id> <文件名>");
            int saved = plugin.persistentGroupStore().saveGroupToFile(group, fileName);
            if (saved == 0) {
                sendError(sender, "没有找到可保存的刚体组: " + group);
                return true;
            }
            sendSuccess(sender, "已保存组 " + group + " 到文件 " + fileName + "，对象数: " + saved + "。");
            return true;
        }

        if (action.equals("load") || action.equals("加载")) {
            if (remaining() < 2) {
                sendError(sender, "用法: /physmc file load <文件名> <目标组id>");
                return true;
            }
            String fileName = consume();
            String group = consume();
            requireNoExtraArgs("/physmc file load <文件名> <目标组id>");
            PersistentGroupStore.LoadResult result = plugin.persistentGroupStore().loadFileToGroup(fileName, group);
            if (result.count() == 0) {
                sendError(sender, "没有从文件加载到任何物理刚体: " + fileName);
                return true;
            }
            if (sender instanceof Player) {
                DisplayHitListener.holdDisplays((Player) sender, result.displays());
            }
            sendSuccess(sender, "已从文件 " + fileName + " 加载 " + result.count() + " 个刚体到组 " + group + "。");
            return true;
        }

        sendError(sender, "未知文件操作: " + action);
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (remaining() < 4) {
            sendError(sender, "用法: /physmc debug <组id|*|^> <名称|*> <show|hide> <类型|*>");
            return true;
        }
        String group = consume();
        String name = consume();
        String action = consume().toLowerCase();
        String type = consume();
        requireNoExtraArgs("/physmc debug <组id|*|^> <名称|*> <show|hide> <类型|*>");

        List<Display> targets = debugTargets(sender, group, name);
        if (targets.isEmpty()) {
            sendError(sender, "没有找到匹配的物理对象。");
            return true;
        }

        if (action.equals("show") || action.equals("展示") || action.equals("显示")) {
            int count = DebugOverlayManager.show(group, name, type, targets);
            sendSuccess(sender, "已显示 debug 项: " + count);
            return true;
        }
        if (action.equals("hide") || action.equals("隐藏")) {
            int count = DebugOverlayManager.hide(group, name, type);
            sendSuccess(sender, "已隐藏 debug 项: " + count);
            return true;
        }
        sendError(sender, "未知 debug 操作: " + action);
        return true;
    }

    private List<Display> debugTargets(CommandSender sender, String group, String name) {
        if (group.equals("^")) {
            if (!(sender instanceof Player)) return List.of();
            Box box = pointedBox((Player) sender);
            return box == null ? List.of() : box.body.getAllDisplay();
        }
        List<Display> result = new ArrayList<>();
        if (group.equals("*")) {
            for (Map.Entry<String, List<Display>> entry : ObjectUtil.displays.entrySet()) {
                if (name.equals("*")) result.addAll(entry.getValue());
                else {
                    List<Display> named = ObjectUtil.namedDisplays(entry.getKey(), name);
                    if (named != null) result.addAll(named);
                }
            }
            return result;
        }
        if (name.equals("*")) {
            List<Display> displays = ObjectUtil.displays.get(group);
            return displays == null ? List.of() : new ArrayList<>(displays);
        }
        List<Display> named = ObjectUtil.namedDisplays(group, name);
        return named == null ? List.of() : new ArrayList<>(named);
    }

    private boolean handleMaterial(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        Player player = (Player) sender;
        if (!hasNext()) {
            sendError(sender, "用法: /physmc material <组id> <名称> 或 /physmc material confirm");
            return true;
        }

        String group = consume();
        if (group.equalsIgnoreCase("confirm") || group.equals("确认")) {
            requireNoExtraArgs("/physmc material confirm");
            PendingMaterialOperation operation = pendingMaterialOperations.remove(confirmKey(sender));
            if (operation == null || operation.isExpired()) {
                sendError(sender, "没有等待确认的材质操作，或确认已过期。");
                return true;
            }
            int changed = setDisplayMaterial(operation.group, operation.name, Material.AIR);
            logConfirmedMaterialOperation(player, operation, changed);
            sendSuccess(sender, "已确认并将 " + operation.group + "/" + operation.name + " 的 " + changed + " 个方块展示实体设置为空气。");
            return true;
        }

        if (remaining() < 1) {
            sendError(sender, "用法: /physmc material <组id> <名称>");
            return true;
        }
        String name = consume();
        requireNoExtraArgs("/physmc material <组id> <名称>");

        List<BlockDisplay> displays = blockDisplays(group, name);
        if (displays.isEmpty()) {
            sendError(sender, "没有找到可修改的方块展示实体: " + group + "/" + name);
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        if (material == Material.AIR) {
            pendingMaterialOperations.put(confirmKey(sender), new PendingMaterialOperation(group, name, displays.size()));
            sendError(sender, "你手上拿的是空气，这会让目标方块展示实体不可见。请在 60 秒内执行 /physmc material confirm 二次确认。");
            return true;
        }
        if (!material.isBlock()) {
            sendError(sender, "手上物品不是方块，不能设置为方块展示实体材质。");
            return true;
        }

        int changed = isPlayerHead(material)
                ? setDisplayPlayerHead(group, name, item)
                : setDisplayMaterial(group, name, material);
        sendSuccess(sender, "已将 " + group + "/" + name + " 的 " + changed + " 个方块展示实体设置为 " + material.name() + "。");
        return true;
    }

    private List<BlockDisplay> blockDisplays(String group, String name) {
        List<Display> displays = ObjectUtil.namedDisplays(group, name);
        if (displays == null) return List.of();
        List<BlockDisplay> result = new ArrayList<>();
        for (Display display : displays) {
            if (display instanceof BlockDisplay && display.isValid() && !display.isDead()) {
                result.add((BlockDisplay) display);
            }
        }
        return result;
    }

    private int setDisplayMaterial(String group, String name, Material material) {
        BlockData blockData = material.createBlockData();
        int changed = 0;
        for (BlockDisplay display : blockDisplays(group, name)) {
            display.setBlock(blockData);
            BlockDisplayScale.setMultiplier(display, BlockDisplayScale.NORMAL);
            changed++;
        }
        return changed;
    }

    private int setDisplayPlayerHead(String group, String name, ItemStack item) {
        BlockData blockData = Material.PLAYER_HEAD.createBlockData();
        int changed = 0;
        for (BlockDisplay display : blockDisplays(group, name)) {
            display.setBlock(blockData);
            BlockDisplayScale.setMultiplier(display, BlockDisplayScale.PLAYER_HEAD);
            changed++;
        }
        return changed;
    }

    private boolean isPlayerHead(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    private void logConfirmedMaterialOperation(Player player, PendingMaterialOperation operation, int actualCount) {
        File logFile = new File(plugin.getDataFolder(), "material-confirmations.log");
        File parent = logFile.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("time=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.write(" type=material-air");
            writer.write(" sender=" + player.getName());
            writer.write(" uuid=" + player.getUniqueId());
            writer.write(" server=" + Bukkit.getServer().getName());
            writer.write(" world=" + player.getWorld().getName());
            writer.write(" location=" + formatLocation(player.getLocation()));
            writer.write(" group=" + operation.group);
            writer.write(" name=" + operation.name);
            writer.write(" requestedCount=" + operation.displayCount);
            writer.write(" actualCount=" + actualCount);
            writer.write(" permissions=" + permissionList(player));
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("写入材质确认日志失败: " + e.getMessage());
        }
    }

    private Box pointedBox(Player player) {
        org.bukkit.util.Vector start = player.getEyeLocation().toVector();
        org.bukkit.util.Vector direction = player.getEyeLocation().getDirection().normalize();
        double best = 6.0d;
        Box bestBox = null;
        for (Box box : World.getInstance().boxes) {
            for (Display display : box.body.getAllDisplay()) {
                if (display.isDead() || !display.getWorld().equals(player.getWorld())) continue;
                double projection = display.getLocation().toVector().subtract(start).dot(direction);
                if (projection < 0.0d || projection > best) continue;
                org.bukkit.util.Vector closest = start.clone().add(direction.clone().multiply(projection));
                if (closest.distance(display.getLocation().toVector()) <= Math.max(box.halfSize.x, Math.max(box.halfSize.y, box.halfSize.z)) + 0.4d) {
                    best = projection;
                    bestBox = box;
                }
            }
        }
        return bestBox;
    }

    private boolean handleFileCommand(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc file <save|load|put|remove|list|confirm> [...]");
            return true;
        }

        String action = consume().toLowerCase();
        if (action.equals("confirm") || action.equals("确认")) {
            requireNoExtraArgs("/physmc file confirm");
            PendingFileOperation operation = pendingFileOperations.remove(confirmKey(sender));
            if (operation == null || operation.isExpired()) {
                sendError(sender, "没有等待确认的文件操作，或确认已过期。");
                return true;
            }
            executeConfirmedFileOperation(sender, operation);
            return true;
        }
        if (action.equals("list") || action.equals("列表")) {
            requireNoExtraArgs("/physmc file list");
            List<String> files = plugin.persistentGroupStore().listSaveFiles();
            if (files.isEmpty()) sendSuccess(sender, "当前没有物理存档文件。");
            else sender.sendMessage(Component.text("物理存档文件: " + String.join(", ", files)).color(TextColor.color(0xFFAA00)));
            return true;
        }
        if (action.equals("save") || action.equals("保存")) {
            if (remaining() < 2) {
                sendError(sender, "用法: /physmc file save <组id> <文件名>");
                return true;
            }
            String group = consume();
            String fileName = consume();
            requireNoExtraArgs("/physmc file save <组id> <文件名>");
            int bodyCount = groupBodyCount(group);
            if (bodyCount >= FILE_CONFIRM_BODY_THRESHOLD) {
                requestFileConfirmation(sender, PendingFileOperation.save(group, fileName, bodyCount));
                return true;
            }
            int saved = plugin.persistentGroupStore().saveGroupToFile(group, fileName);
            if (saved == 0) {
                sendError(sender, "没有找到可保存的刚体组: " + group);
                return true;
            }
            sendSuccess(sender, "已保存组 " + group + " 到文件 " + fileName + "，对象数: " + saved + "。");
            return true;
        }
        if (action.equals("load") || action.equals("加载") || action.equals("put") || action.equals("放置")) {
            if (remaining() < 2) {
                sendError(sender, "用法: /physmc file " + action + " <文件名> <目标组id>");
                return true;
            }
            String fileName = consume();
            String group = consume();
            requireNoExtraArgs("/physmc file " + action + " <文件名> <目标组id>");
            boolean put = action.equals("put") || action.equals("放置");
            if (put && !(sender instanceof Player)) {
                sendError(sender, "put 只能由玩家执行，因为需要玩家当前位置。");
                return true;
            }
            int bodyCount = plugin.persistentGroupStore().countFileBodies(fileName);
            if (bodyCount >= FILE_CONFIRM_BODY_THRESHOLD) {
                requestFileConfirmation(sender, PendingFileOperation.load(group, fileName, bodyCount, put));
                return true;
            }
            loadFileNow(sender, group, fileName, put);
            return true;
        }
        if (action.equals("remove") || action.equals("delete") || action.equals("删除")) {
            if (remaining() < 1) {
                sendError(sender, "用法: /physmc file remove <文件名>");
                return true;
            }
            String fileName = consume();
            requireNoExtraArgs("/physmc file remove <文件名>");
            if (!plugin.persistentGroupStore().removeSaveFile(fileName)) {
                sendError(sender, "没有找到可删除的物理存档文件: " + fileName);
                return true;
            }
            sendSuccess(sender, "已删除物理存档文件: " + fileName);
            return true;
        }
        sendError(sender, "未知文件操作: " + action);
        return true;
    }

    private void loadFileNow(CommandSender sender, String group, String fileName, boolean put) {
        PersistentGroupStore.LoadResult result = plugin.persistentGroupStore().loadFileToGroup(fileName, group);
        if (result.count() == 0) {
            sendError(sender, "没有从文件加载到任何物理刚体: " + fileName);
            return;
        }
        if (put) translateLoadedDisplaysToPlayer((Player) sender, result.displays());
        if (put && sender instanceof Player) holdLoadedDisplaysNextTick((Player) sender, result.displays());
        sendSuccess(sender, "已从文件 " + fileName + " 加载 " + result.count() + " 个刚体到组 " + group + "。");
    }

    private void requestFileConfirmation(CommandSender sender, PendingFileOperation operation) {
        pendingFileOperations.put(confirmKey(sender), operation);
        sendError(sender, "该文件操作涉及 " + operation.bodyCount + " 个刚体，需要二次确认。请在 60 秒内执行 /physmc file confirm。");
    }

    private void executeConfirmedFileOperation(CommandSender sender, PendingFileOperation operation) {
        if (operation.type == FileOperationType.SAVE) {
            int saved = plugin.persistentGroupStore().saveGroupToFile(operation.group, operation.fileName);
            if (saved == 0) {
                sendError(sender, "没有找到可保存的刚体组: " + operation.group);
                return;
            }
            logConfirmedFileOperation(sender, operation, saved);
            sendSuccess(sender, "已确认并保存组 " + operation.group + " 到文件 " + operation.fileName + "，对象数: " + saved + "。");
            return;
        }
        if (operation.put && !(sender instanceof Player)) {
            sendError(sender, "put 只能由玩家确认执行。");
            return;
        }
        PersistentGroupStore.LoadResult result = plugin.persistentGroupStore().loadFileToGroup(operation.fileName, operation.group);
        if (result.count() == 0) {
            sendError(sender, "没有从文件加载到任何物理刚体: " + operation.fileName);
            return;
        }
        if (operation.put) translateLoadedDisplaysToPlayer((Player) sender, result.displays());
        if (operation.put && sender instanceof Player) holdLoadedDisplaysNextTick((Player) sender, result.displays());
        logConfirmedFileOperation(sender, operation, result.count());
        sendSuccess(sender, "已确认并从文件 " + operation.fileName + " 加载 " + result.count() + " 个刚体到组 " + operation.group + "。");
    }

    private void holdLoadedDisplaysNextTick(Player player, List<Display> displays) {
        List<Display> targets = new ArrayList<>(displays);
        Bukkit.getScheduler().runTask(plugin, () -> DisplayHitListener.holdDisplays(player, targets));
    }

    private void translateLoadedDisplaysToPlayer(Player player, List<Display> displays) {
        Vector3d center = new Vector3d();
        int count = 0;
        for (Box box : World.getInstance().boxes) {
            Display primary = firstDisplay(box);
            if (primary != null && displays.contains(primary)) {
                center.add(box.body.position);
                count++;
            }
        }
        if (count == 0) return;
        center.div(count);
        Vector3d delta = new Vector3d(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()).sub(center);
        for (Box box : World.getInstance().boxes) {
            Display primary = firstDisplay(box);
            if (primary != null && displays.contains(primary)) {
                box.body.position.add(delta);
                World.getInstance().syncBody(box);
                box.body.tick();
            }
        }
    }

    private Display firstDisplay(Box box) {
        for (Display display : box.body.getAllDisplay()) {
            if (display != null && display.isValid() && !display.isDead()) return display;
        }
        return null;
    }

    private int groupBodyCount(String group) {
        int count = 0;
        for (Box box : World.getInstance().boxes) {
            if (box.getGroupId().filter(group::equals).isPresent()) count++;
        }
        return count;
    }

    private String confirmKey(CommandSender sender) {
        return sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "console:" + sender.getName();
    }

    private void logConfirmedFileOperation(CommandSender sender, PendingFileOperation operation, int actualCount) {
        File logFile = new File(plugin.getDataFolder(), "file-confirmations.log");
        File parent = logFile.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("time=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.write(" type=" + operation.type.name().toLowerCase(java.util.Locale.ROOT));
            writer.write(" put=" + operation.put);
            writer.write(" sender=" + sender.getName());
            writer.write(" player=" + (sender instanceof Player));
            writer.write(" server=" + Bukkit.getServer().getName());
            writer.write(" group=" + operation.group);
            writer.write(" file=" + operation.fileName);
            writer.write(" requestedCount=" + operation.bodyCount);
            writer.write(" actualCount=" + actualCount);
            if (sender instanceof Player) {
                Player player = (Player) sender;
                writer.write(" uuid=" + player.getUniqueId());
                writer.write(" world=" + player.getWorld().getName());
                writer.write(" location=" + formatLocation(player.getLocation()));
            }
            writer.write(" permissions=" + permissionList(sender));
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("写入文件确认日志失败: " + e.getMessage());
        }
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + ":" + format(location.getX()) + "," + format(location.getY()) + "," + format(location.getZ());
    }

    private String permissionList(CommandSender sender) {
        if (!(sender instanceof Player)) return sender.isOp() ? "operator" : "console";
        List<String> permissions = ((Player) sender).getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .sorted()
                .collect(Collectors.toList());
        return permissions.isEmpty() ? "none" : String.join(",", permissions);
    }

    private boolean handlePause(CommandSender sender) {
        requireNoExtraArgs("/physmc pause");
        PhysManager.getInstance().pause();
        sendSuccess(sender, "物理模拟已暂停。");
        return true;
    }

    private boolean handleResume(CommandSender sender) {
        requireNoExtraArgs("/physmc resume");
        PhysManager.getInstance().resume();
        sendSuccess(sender, "物理模拟已继续。");
        return true;
    }

    private boolean handleStep(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc step <tick数>");
            return true;
        }
        int ticks = Integer.parseInt(consume());
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc step <tick数>");
            return true;
        }
        if (ticks <= 0) {
            sendError(sender, "tick 数必须大于 0。");
            return true;
        }
        int stepped = PhysManager.getInstance().stepTicks(ticks);
        sendSuccess(sender, "已步进 " + stepped + " tick。");
        return true;
    }

    private boolean handleFast(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "用法: /physmc fast <秒数>");
            return true;
        }
        double seconds = Double.parseDouble(consume());
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc fast <秒数>");
            return true;
        }
        if (seconds <= 0.0d) {
            sendError(sender, "秒数必须大于 0。");
            return true;
        }
        int steps = PhysManager.getInstance().runFast(seconds);
        sendSuccess(sender, "已快速模拟 " + format(seconds) + " 秒，执行 " + steps + " 个物理步。");
        return true;
    }

    private boolean handleQuery(CommandSender sender) {
        if (!hasNext()) {
            sendPhysicsSummary(sender);
            return true;
        }

        String id = consume();
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc query [id]");
            return true;
        }

        Box box = findBoxById(id);
        if (box == null) {
            sendError(sender, "未找到物理物体: " + id);
            return true;
        }

        sendBoxInfo(sender, id, box);
        return true;
    }

    private boolean handleConstraint(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        if (!hasNext()) {
            sendConstraintHelp(sender);
            return true;
        }
        String action = consume().toLowerCase();
        if (action.equals("help") || action.equals("帮助")) {
            sendConstraintHelp(sender);
            return true;
        }
        if (!action.equals("create") && !action.equals("创建")) {
            sendError(sender, "未知约束操作: " + action);
            return true;
        }
        if (!hasNext()) {
            sendConstraintHelp(sender);
            return true;
        }

        Player player = (Player) sender;
        ConstraintSelectionStore.Selection selection = ConstraintSelectionStore.get(player);
        if (selection == null) {
            sendError(sender, "请先用木棍左键选择两个约束点。");
            return true;
        }
        if (!selection.first.hasPhysicalTarget() && !selection.second.hasPhysicalTarget()) {
            sendError(sender, "两个点都不在物理物体上，无法创建约束。");
            return true;
        }

        String type = consume().toLowerCase();
        if (type.equals("help") || type.equals("帮助")) {
            sendConstraintHelp(sender);
            return true;
        }
        boolean created;
        switch (type) {
            case "point":
            case "点":
                created = World.getInstance().connect(
                        selection.first.display,
                        selection.first.entity,
                        selection.first.location,
                        selection.second.display,
                        selection.second.entity,
                        selection.second.location
                );
                break;
            case "distance":
            case "距离":
                double distance = hasNext() ? Double.parseDouble(consume()) : selection.distance();
                if (distance < 0.0d) {
                    sendError(sender, "距离不能小于 0。");
                    return true;
                }
                created = World.getInstance().addDistanceConstraint(
                        selection.first.display,
                        selection.first.entity,
                        selection.first.location,
                        selection.second.display,
                        selection.second.entity,
                        selection.second.location,
                        distance
                );
                break;
            case "spring":
            case "弹簧":
                double restLength = hasNext() ? Double.parseDouble(consume()) : selection.distance();
                double stiffness = hasNext() ? Double.parseDouble(consume()) : DEFAULT_SPRING_STIFFNESS;
                double damping = hasNext() ? Double.parseDouble(consume()) : DEFAULT_SPRING_DAMPING;
                if (restLength < 0.0d || stiffness < 0.0d || damping < 0.0d) {
                    sendError(sender, "弹簧原长、刚度和阻尼不能小于 0。");
                    return true;
                }
                created = createSelectedSpring(selection, restLength, stiffness, damping);
                break;
            case "hinge":
            case "铰链":
                created = createTypedConstraint("hinge", selection, 0.0d, 0.0d, 1.0d, -1.0d);
                break;
            case "fixed":
            case "固定":
                double breakImpulse = hasNext() ? Double.parseDouble(consume()) : getConfigDouble(PhysConfig.PHYSICS_FIXED_CONSTRAINT_BREAK_IMPULSE);
                created = createTypedConstraint("fixed", selection, 0.0d, 0.0d, 0.0d, 0.0d, breakImpulse);
                break;
            case "slider":
            case "滑轨":
                double lowerLinear = hasNext() ? Double.parseDouble(consume()) : -selection.distance();
                double upperLinear = hasNext() ? Double.parseDouble(consume()) : selection.distance();
                double lowerAngular = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                double upperAngular = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                created = createTypedConstraint("slider", selection, lowerLinear, upperLinear, lowerAngular, upperAngular);
                break;
            case "6dof":
                double linearLower = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                double linearUpper = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                double angularLower = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                double angularUpper = hasNext() ? Double.parseDouble(consume()) : 0.0d;
                created = createTypedConstraint("6dof", selection, linearLower, linearUpper, angularLower, angularUpper);
                break;
            case "cone":
            case "锥形":
                double swing = hasNext() ? Double.parseDouble(consume()) : 0.8d;
                double twist = hasNext() ? Double.parseDouble(consume()) : 0.4d;
                created = createTypedConstraint("cone", selection, 0.0d, 0.0d, swing, twist);
                break;
            default:
                sendError(sender, "未知约束类型: " + type);
                return true;
        }
        requireNoExtraArgs("/physmc constraint create <point|distance|spring|hinge|slider|fixed|6dof|cone> [...]");
        if (!created) {
            sendError(sender, "约束创建失败：至少一个点必须位于物理刚体上。");
            return true;
        }
        sendSuccess(sender, "约束创建成功。");
        return true;
    }

    private boolean createSelectedSpring(ConstraintSelectionStore.Selection selection, double restLength, double stiffness, double damping) {
        if (selection.first.display == null || selection.second.display == null) {
            return false;
        }
        World.getInstance().addSpring(selection.first.display, selection.second.display, restLength, stiffness, damping);
        return true;
    }

    private boolean createTypedConstraint(String type, ConstraintSelectionStore.Selection selection, double lowerLinear, double upperLinear, double lowerAngular, double upperAngular) {
        return createTypedConstraint(type, selection, lowerLinear, upperLinear, lowerAngular, upperAngular, 0.0d);
    }

    private boolean createTypedConstraint(String type, ConstraintSelectionStore.Selection selection, double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
        return World.getInstance().addTypedConstraint(
                type,
                selection.first.display,
                selection.first.entity,
                selection.first.location,
                selection.second.display,
                selection.second.entity,
                selection.second.location,
                lowerLinear,
                upperLinear,
                lowerAngular,
                upperAngular,
                breakImpulse
        );
    }

    private void sendConstraintHelp(CommandSender sender) {
        sender.sendMessage(Component.text("约束命令：先用木棍左键选择两个点，再执行 create。").color(TextColor.color(0xFFAA00)));
        sender.sendMessage(Component.text("point: 点对点约束，使两个选点重合。用法: /physmc constraint create point").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("distance [距离]: 距离约束，保持两个选点间距；不填则使用当前选点距离。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("spring [原长] [刚度] [阻尼]: 弹簧力；原长不填则使用当前距离，刚度越大越硬，阻尼越大越不抖。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("hinge: 铰链约束，像门轴；轴线由第一个选点指向第二个选点。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("slider [线性下限] [线性上限] [角度下限] [角度上限]: 滑轨约束，沿选点方向滑动。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("fixed [最大冲量]: 固定约束，锁定两个刚体的相对位置和相对旋转；超过最大冲量会断裂，0 表示不断裂。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("6dof [线性下限] [线性上限] [角度下限] [角度上限]: 六自由度约束，统一限制三轴平移和三轴旋转。").color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("cone [摆动角] [扭转角]: 锥形扭转约束，适合类似肩关节、吊挂关节；角度单位是弧度。").color(TextColor.color(0xCCCCCC)));
    }

    private void requireNoExtraArgs(String usage) {
        if (hasNext()) {
            throw new IllegalArgumentException("参数过多。用法: " + usage);
        }
    }

    private boolean hasNext() {
        return commandIndex < commands.size();
    }

    private int remaining() {
        return commands.size() - commandIndex;
    }

    private String consume() {
        return commands.get(commandIndex++);
    }

    private boolean isConfigKey(String key) {
        return PhysConfig.KEYS.contains(key)
                || PhysConfig.isEntityMassKey(key)
                || PhysConfig.isEntityTypeMassKey(key)
                || isMaterialPropertyKey(key);
    }

    private void sendConfigEntry(CommandSender sender, String key) {
        PhysConfig.Entry entry = PhysConfig.get(key);
        if (entry == null && PhysConfig.isEntityMassKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 单个玩家或实体的质量。质量为 0 时实体位置不受物理模拟改变。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 玩家 60-100，普通实体 10-80；只作为静态碰撞体则设 0。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: 根据 实体.默认质量_玩家 或 实体.默认质量_非玩家 决定").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isEntityTypeMassKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 EntityType 的默认质量。质量为 0 时该类型实体只作为静态碰撞体。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 小型实体 1-20，普通生物 20-80，载具 30-100。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: 启动时按实体类型自动生成").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isMaterialFrictionKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 Material 地形碰撞体的摩擦系数。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 冰 0.01-0.08，普通方块 0.5-1.0，蜂蜜块 1.0-2.0。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: " + PhysConfig.get(PhysConfig.PHYSICS_FRICTION).defaultValue).color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isMaterialStaticFrictionKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 Material 地形碰撞体的静摩擦系数，用于 Bullet 接触求解。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 冰 0.01-0.08，普通方块 0.6-1.0，蜂蜜块 1.2-2.0。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: " + PhysConfig.get(PhysConfig.PHYSICS_STATIC_FRICTION).defaultValue).color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isMaterialDynamicFrictionKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 Material 接触时的动摩擦近似，会衰减水平滑动速度。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 冰 0.002-0.01，普通方块 0.03-0.12，蜂蜜块 0.2-0.5。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: " + PhysConfig.get(PhysConfig.PHYSICS_DYNAMIC_FRICTION).defaultValue).color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isMaterialRollingFrictionKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 Material 接触时的滚动摩擦近似，会衰减角速度。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 冰 0.004-0.02，普通方块 0.02-0.08，蜂蜜块 0.2-0.6。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: " + PhysConfig.get(PhysConfig.PHYSICS_ROLLING_FRICTION).defaultValue).color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        if (entry == null && PhysConfig.isMaterialRestitutionKey(key)) {
            sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
            sender.sendMessage(Component.text("说明: 指定 Material 地形碰撞体的反弹系数。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("建议: 普通方块 0-0.2，黏液块 0.6-0.95。").color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("默认值: " + PhysConfig.get(PhysConfig.PHYSICS_STATIC_RESTITUTION).defaultValue).color(TextColor.color(0xCCCCCC)));
            sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
            return;
        }
        sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
        sender.sendMessage(Component.text("说明: " + entry.description).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("建议: " + entry.suggestion).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("默认值: " + entry.defaultValue).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
    }

    private void setConfigValue(String key, String value) {
        if ((PhysConfig.isMaterialFrictionKey(key) || PhysConfig.isMaterialStaticFrictionKey(key)
                || PhysConfig.isMaterialDynamicFrictionKey(key) || PhysConfig.isMaterialRollingFrictionKey(key)
                || PhysConfig.isMaterialRestitutionKey(key)) && !isMaterialPropertyKey(key)) {
            throw new IllegalArgumentException("材质属性后缀必须是有效方块 Material 名称");
        }
        Object defaultValue = (PhysConfig.isEntityMassKey(key)
                || PhysConfig.isEntityTypeMassKey(key)
                || PhysConfig.isMaterialFrictionKey(key)
                || PhysConfig.isMaterialStaticFrictionKey(key)
                || PhysConfig.isMaterialDynamicFrictionKey(key)
                || PhysConfig.isMaterialRollingFrictionKey(key)
                || PhysConfig.isMaterialRestitutionKey(key)) ? 0.0d : PhysConfig.get(key).defaultValue;
        if (defaultValue instanceof Boolean) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("布尔值必须是 true 或 false");
            }
            plugin.getConfig().set(key, Boolean.parseBoolean(value));
        } else if (defaultValue instanceof Integer) {
            plugin.getConfig().set(key, Integer.parseInt(value));
        } else {
            plugin.getConfig().set(key, Double.parseDouble(value));
        }
    }

    private int applyPreset(File file) {
        YamlConfiguration preset = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection values = preset.getConfigurationSection("values");
        if (values == null) values = preset;

        int changed = 0;
        for (String rawKey : values.getKeys(false)) {
            String key = normalizePresetKey(rawKey);
            if (key == null || !PhysConfig.KEYS.contains(key)) {
                throw new IllegalArgumentException("预设包含未知配置项: " + rawKey);
            }
            Object value = values.get(rawKey);
            if (value == null) continue;
            setConfigValue(key, String.valueOf(value));
            changed++;
        }
        return changed;
    }

    private int resetPresetDefaults() {
        int changed = 0;
        for (String key : presetManagedKeys()) {
            PhysConfig.Entry entry = PhysConfig.get(key);
            if (entry == null) continue;
            plugin.getConfig().set(key, entry.defaultValue);
            changed++;
        }
        return changed;
    }

    private File presetFile(String name) {
        String fileName = name.endsWith(".yml") || name.endsWith(".yaml") ? name : name + ".yml";
        return new File(new File(plugin.getDataFolder(), "presets"), fileName);
    }

    private List<String> presetSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("default");
        suggestions.add("默认");
        File directory = new File(plugin.getDataFolder(), "presets");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml")
                || name.toLowerCase(java.util.Locale.ROOT).endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                suggestions.add(stripYamlSuffix(file.getName()));
            }
        }
        return suggestions;
    }

    private String stripYamlSuffix(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".yaml")) return name.substring(0, name.length() - 5);
        if (lower.endsWith(".yml")) return name.substring(0, name.length() - 4);
        return name;
    }

    private String normalizePresetKey(String key) {
        if (PhysConfig.KEYS.contains(key)) return key;
        return PRESET_KEY_ALIASES.get(key.toLowerCase(java.util.Locale.ROOT));
    }

    private static List<String> presetManagedKeys() {
        return List.of(
                PhysConfig.PHYSICS_GRAVITY_X,
                PhysConfig.PHYSICS_GRAVITY_Y,
                PhysConfig.PHYSICS_GRAVITY_Z,
                PhysConfig.PHYSICS_FIXED_STEP,
                PhysConfig.PHYSICS_MAX_SUB_STEPS,
                PhysConfig.PHYSICS_DYNAMIC_MARGIN,
                PhysConfig.PHYSICS_STATIC_MARGIN,
                PhysConfig.PHYSICS_FRICTION,
                PhysConfig.PHYSICS_RESTITUTION,
                PhysConfig.PHYSICS_STATIC_RESTITUTION,
                PhysConfig.PHYSICS_STATIC_FRICTION,
                PhysConfig.PHYSICS_DYNAMIC_FRICTION,
                PhysConfig.PHYSICS_ROLLING_FRICTION,
                PhysConfig.PHYSICS_LINEAR_DAMPING,
                PhysConfig.PHYSICS_ANGULAR_DAMPING,
                PhysConfig.PHYSICS_LINEAR_SLEEPING_THRESHOLD,
                PhysConfig.PHYSICS_ANGULAR_SLEEPING_THRESHOLD,
                PhysConfig.GAME_FORCE_ATTACK_IMPULSE,
                PhysConfig.FLUID_BUOYANCY_ENABLED,
                PhysConfig.FLUID_DENSITY_WATER,
                PhysConfig.FLUID_DENSITY_LAVA,
                PhysConfig.FLUID_LINEAR_DRAG_WATER,
                PhysConfig.FLUID_LINEAR_DRAG_LAVA,
                PhysConfig.FLUID_ANGULAR_DRAG_WATER,
                PhysConfig.FLUID_ANGULAR_DRAG_LAVA,
                PhysConfig.FLUID_HORIZONTAL_FLOW_WATER,
                PhysConfig.FLUID_HORIZONTAL_FLOW_LAVA,
                PhysConfig.FLUID_FALLING_FLOW_WATER,
                PhysConfig.FLUID_FALLING_FLOW_LAVA
        );
    }

    private static Map<String, String> presetKeyAliases() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("gravity-x", PhysConfig.PHYSICS_GRAVITY_X);
        keys.put("gravity-y", PhysConfig.PHYSICS_GRAVITY_Y);
        keys.put("gravity-z", PhysConfig.PHYSICS_GRAVITY_Z);
        keys.put("fixed-step", PhysConfig.PHYSICS_FIXED_STEP);
        keys.put("max-sub-steps", PhysConfig.PHYSICS_MAX_SUB_STEPS);
        keys.put("dynamic-margin", PhysConfig.PHYSICS_DYNAMIC_MARGIN);
        keys.put("static-margin", PhysConfig.PHYSICS_STATIC_MARGIN);
        keys.put("friction", PhysConfig.PHYSICS_FRICTION);
        keys.put("restitution", PhysConfig.PHYSICS_RESTITUTION);
        keys.put("static-restitution", PhysConfig.PHYSICS_STATIC_RESTITUTION);
        keys.put("static-friction", PhysConfig.PHYSICS_STATIC_FRICTION);
        keys.put("dynamic-friction", PhysConfig.PHYSICS_DYNAMIC_FRICTION);
        keys.put("rolling-friction", PhysConfig.PHYSICS_ROLLING_FRICTION);
        keys.put("linear-damping", PhysConfig.PHYSICS_LINEAR_DAMPING);
        keys.put("angular-damping", PhysConfig.PHYSICS_ANGULAR_DAMPING);
        keys.put("linear-sleeping-threshold", PhysConfig.PHYSICS_LINEAR_SLEEPING_THRESHOLD);
        keys.put("angular-sleeping-threshold", PhysConfig.PHYSICS_ANGULAR_SLEEPING_THRESHOLD);
        keys.put("attack-impulse", PhysConfig.GAME_FORCE_ATTACK_IMPULSE);
        keys.put("buoyancy-enabled", PhysConfig.FLUID_BUOYANCY_ENABLED);
        keys.put("water-density", PhysConfig.FLUID_DENSITY_WATER);
        keys.put("lava-density", PhysConfig.FLUID_DENSITY_LAVA);
        keys.put("water-linear-drag", PhysConfig.FLUID_LINEAR_DRAG_WATER);
        keys.put("lava-linear-drag", PhysConfig.FLUID_LINEAR_DRAG_LAVA);
        keys.put("water-angular-drag", PhysConfig.FLUID_ANGULAR_DRAG_WATER);
        keys.put("lava-angular-drag", PhysConfig.FLUID_ANGULAR_DRAG_LAVA);
        keys.put("water-horizontal-flow", PhysConfig.FLUID_HORIZONTAL_FLOW_WATER);
        keys.put("lava-horizontal-flow", PhysConfig.FLUID_HORIZONTAL_FLOW_LAVA);
        keys.put("water-falling-flow", PhysConfig.FLUID_FALLING_FLOW_WATER);
        keys.put("lava-falling-flow", PhysConfig.FLUID_FALLING_FLOW_LAVA);
        return keys;
    }

    private Object getConfigValue(String key) {
        if (plugin.getConfig().contains(key)) return plugin.getConfig().get(key);
        String legacyKey = PhysConfig.LEGACY_KEYS.get(key);
        if (legacyKey != null && plugin.getConfig().contains(legacyKey)) return plugin.getConfig().get(legacyKey);
        if (PhysConfig.isEntityMassKey(key)) return 0.0d;
        if (PhysConfig.isEntityTypeMassKey(key)) return 0.0d;
        if (PhysConfig.isMaterialFrictionKey(key)) return PhysConfig.get(PhysConfig.PHYSICS_FRICTION).defaultValue;
        if (PhysConfig.isMaterialStaticFrictionKey(key)) return PhysConfig.get(PhysConfig.PHYSICS_STATIC_FRICTION).defaultValue;
        if (PhysConfig.isMaterialDynamicFrictionKey(key)) return PhysConfig.get(PhysConfig.PHYSICS_DYNAMIC_FRICTION).defaultValue;
        if (PhysConfig.isMaterialRollingFrictionKey(key)) return PhysConfig.get(PhysConfig.PHYSICS_ROLLING_FRICTION).defaultValue;
        if (PhysConfig.isMaterialRestitutionKey(key)) return PhysConfig.get(PhysConfig.PHYSICS_STATIC_RESTITUTION).defaultValue;
        return PhysConfig.get(key).defaultValue;
    }

    private String resolveMassKey(CommandSender sender, String key) {
        String target = key.substring(PhysConfig.ENTITY_PLAYER_MASS_PREFIX.length());
        if (target.startsWith("@")) {
            List<Entity> selected = Bukkit.selectEntities(sender, target);
            if (selected.isEmpty()) throw new IllegalArgumentException("选择器未选中任何实体");
            return massKey(selected.get(0));
        }
        return key;
    }

    private String massKey(Entity entity) {
        if (entity instanceof Player) return PhysConfig.ENTITY_PLAYER_MASS_PREFIX + entity.getName();
        return PhysConfig.ENTITY_UUID_MASS_PREFIX + entity.getUniqueId();
    }

    private void sendPhysicsSummary(CommandSender sender) {
        sender.sendMessage(Component.text("物理模拟状态: " + (PhysManager.getInstance().isPaused() ? "暂停" : "运行")).color(TextColor.color(0xFFAA00)));
        sender.sendMessage(Component.text("刚体数量: " + World.getInstance().boxes.size()).color(TextColor.color(0xCCCCCC)));
        if (ObjectUtil.displays.isEmpty()) {
            sender.sendMessage(Component.text("已登记 ID: 无").color(TextColor.color(0xCCCCCC)));
            return;
        }
        sender.sendMessage(Component.text("已登记 ID: " + String.join(", ", ObjectUtil.displays.keySet())).color(TextColor.color(0xCCCCCC)));
    }

    private Box findBoxById(String id) {
        List<Display> displays = ObjectUtil.displays.get(id);
        if (displays == null || displays.isEmpty()) return null;
        for (Box box : World.getInstance().boxes) {
            for (Display display : box.body.getAllDisplay()) {
                if (displays.contains(display)) {
                    return box;
                }
            }
        }
        return null;
    }

    private Box findBoxByGroupName(String group, String name) {
        List<Display> displays = ObjectUtil.namedDisplays(group, name);
        if (displays == null || displays.isEmpty()) return null;
        for (Box box : World.getInstance().boxes) {
            for (Display display : box.body.getAllDisplay()) {
                if (displays.contains(display)) return box;
            }
        }
        return null;
    }

    private void sendBoxInfo(CommandSender sender, String id, Box box) {
        sender.sendMessage(Component.text("物理物体: " + id).color(TextColor.color(0xFFAA00)));
        sender.sendMessage(Component.text("质量: " + (box.body.hasFiniteMass() ? format(box.body.getMass()) : "0")).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("位置: " + formatVector(box.body.position)).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("速度: " + formatVector(box.body.velocity)).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("角速度: " + formatVector(box.body.rotation)).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("尺寸: " + formatVector(new Vector3d(box.halfSize).mul(2.0d))).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("展示实体数: " + box.body.getAllDisplay().size()).color(TextColor.color(0xCCCCCC)));
    }

    private String formatVector(Vector3d value) {
        return "(" + format(value.x) + ", " + format(value.y) + ", " + format(value.z) + ")";
    }

    private String formatQuaternion(Quaterniond value) {
        return "(" + format(value.x) + ", " + format(value.y) + ", " + format(value.z) + ", " + format(value.w) + ")";
    }

    private String formatMatrix(Matrix3d value) {
        return "[" + format(value.m00()) + ", " + format(value.m01()) + ", " + format(value.m02()) + "; "
                + format(value.m10()) + ", " + format(value.m11()) + ", " + format(value.m12()) + "; "
                + format(value.m20()) + ", " + format(value.m21()) + ", " + format(value.m22()) + "]";
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("PhysMC 命令:").color(TextColor.color(0xFFAA00)));
        sender.sendMessage("/physmc spawn block <组id> <名称> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
        sender.sendMessage("/physmc spawn line <组id> <名称> <x1 y1 z1> <x2 y2 z2>");
        sender.sendMessage("/physmc spawn rod <组id> <名称> <长度>");
        sender.sendMessage("/physmc get <键名>");
        sender.sendMessage("/physmc set <键名> <值>");
        sender.sendMessage("/physmc preset <default|文件名>");
        sender.sendMessage("/physmc remove <id>");
        sender.sendMessage("/physmc pause | resume");
        sender.sendMessage("/physmc step <tick数>");
        sender.sendMessage("/physmc fast <秒数>");
        sender.sendMessage("/physmc query [id]");
        sender.sendMessage("/physmc constraint create <point|distance|spring|hinge|slider|fixed|6dof|cone> [...]");
        sender.sendMessage("/physmc persist <save|remove> <组id>");
        sender.sendMessage("/physmc debug <组id|*|^> <名称|*> <show|hide> <类型|*>");
        sender.sendMessage("/physmc prop <组id> <名称> <get|set|add> <物理量> [坐标...] [值...]");
        sender.sendMessage("/physmc material <组id> <名称>");
        sender.sendMessage("/physmc selection tool <on|off>");
        sender.sendMessage("/physmc selection clear");
        sender.sendMessage("/physmc selection build <组id>");
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(TextColor.color(0xFF3333)));
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(TextColor.color(0x00FF00)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && !args[0].isEmpty()
                && ("preset".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) || "\u9884\u8bbe".startsWith(args[0]))) {
            return filter(List.of("preset", "\u9884\u8bbe"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("preset") || args[0].equals("\u9884\u8bbe"))) {
            return filter(presetSuggestions(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("preset") || args[0].equals("预设"))) return filter(presetSuggestions(), args[1]);
        if (args.length == 1) {
            return filter(List.of("help", "spawn", "remove", "get", "set", "pause", "resume", "step", "fast", "query", "constraint", "persist", "file", "savefile", "debug", "prop", "material", "blocktype", "selection", "selectiontool", "clearselection", "材质"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("material") || args[0].equalsIgnoreCase("blocktype") || args[0].equals("材质"))) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("confirm");
            suggestions.add("确认");
            suggestions.addAll(ObjectUtil.displays.keySet());
            return filter(suggestions, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("material") || args[0].equalsIgnoreCase("blocktype") || args[0].equals("材质"))
                && !args[1].equalsIgnoreCase("confirm") && !args[1].equals("确认")) {
            return objectNameSuggestions(args[1], args[2]);
        }
        if (args.length == 1) return filter(List.of("help", "spawn", "remove", "get", "set", "pause", "resume", "step", "fast", "query", "constraint", "persist", "file", "savefile", "debug", "prop", "selection", "selectiontool", "clearselection", "物理量", "选区", "选区工具", "清空选区", "暂停", "继续", "步进", "快速", "查询", "约束", "持久化", "调试"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("prop") || args[0].equalsIgnoreCase("property") || args[0].equals("物理量"))) {
            return objectIdSuggestions(args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("prop") || args[0].equalsIgnoreCase("property") || args[0].equals("物理量"))) {
            return objectNameSuggestions(args[1], args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("prop") || args[0].equalsIgnoreCase("property") || args[0].equals("物理量"))) {
            return filter(List.of("get", "set", "add", "获取", "设置", "增加"), args[3]);
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("prop") || args[0].equalsIgnoreCase("property") || args[0].equals("物理量"))) {
            return filter(propertySuggestions(), args[4]);
        }
        if (args.length > 5 && (args[0].equalsIgnoreCase("prop") || args[0].equalsIgnoreCase("property") || args[0].equals("物理量"))) {
            return filter(propertyArgumentSuggestions(args), args[args.length - 1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("selection") || args[0].equalsIgnoreCase("select") || args[0].equals("选区"))) {
            return filter(List.of("tool", "clear", "build", "工具", "清空", "构建"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("selection") || args[0].equalsIgnoreCase("select") || args[0].equals("选区"))
                && (args[1].equalsIgnoreCase("tool") || args[1].equals("工具"))) {
            return filter(List.of("on", "off", "启用", "禁用"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("selection") || args[0].equalsIgnoreCase("select") || args[0].equals("选区"))
                && (args[1].equalsIgnoreCase("build") || args[1].equals("构建"))) {
            return objectIdSuggestions(args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("selectiontool") || args[0].equalsIgnoreCase("selecttool") || args[0].equals("选区工具"))) {
            return filter(List.of("on", "off", "启用", "禁用"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            List<String> keys = new ArrayList<>(PhysConfig.KEYS);
            keys.add(PhysConfig.ENTITY_PLAYER_MASS_PREFIX);
            keys.add(PhysConfig.ENTITY_TYPE_MASS_PREFIX);
            keys.add(PhysConfig.MATERIAL_FRICTION_PREFIX);
            keys.add(PhysConfig.MATERIAL_STATIC_FRICTION_PREFIX);
            keys.add(PhysConfig.MATERIAL_DYNAMIC_FRICTION_PREFIX);
            keys.add(PhysConfig.MATERIAL_ROLLING_FRICTION_PREFIX);
            keys.add(PhysConfig.MATERIAL_RESTITUTION_PREFIX);
            if (args[1].startsWith(PhysConfig.ENTITY_PLAYER_MASS_PREFIX)) keys.addAll(massKeySuggestions(args[1]));
            if (args[1].startsWith(PhysConfig.ENTITY_TYPE_MASS_PREFIX)) keys.addAll(entityTypeMassKeySuggestions(args[1]));
            if (args[1].startsWith(PhysConfig.MATERIAL_FRICTION_PREFIX)) keys.addAll(materialKeySuggestions(args[1], PhysConfig.MATERIAL_FRICTION_PREFIX));
            if (args[1].startsWith(PhysConfig.MATERIAL_STATIC_FRICTION_PREFIX)) keys.addAll(materialKeySuggestions(args[1], PhysConfig.MATERIAL_STATIC_FRICTION_PREFIX));
            if (args[1].startsWith(PhysConfig.MATERIAL_DYNAMIC_FRICTION_PREFIX)) keys.addAll(materialKeySuggestions(args[1], PhysConfig.MATERIAL_DYNAMIC_FRICTION_PREFIX));
            if (args[1].startsWith(PhysConfig.MATERIAL_ROLLING_FRICTION_PREFIX)) keys.addAll(materialKeySuggestions(args[1], PhysConfig.MATERIAL_ROLLING_FRICTION_PREFIX));
            if (args[1].startsWith(PhysConfig.MATERIAL_RESTITUTION_PREFIX)) keys.addAll(materialKeySuggestions(args[1], PhysConfig.MATERIAL_RESTITUTION_PREFIX));
            return filter(keys, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("true", "false", "<value>");
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return filter(List.of("help", "block", "sphere", "line", "rod", "rope", "spring"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return objectIdSuggestions(args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("remove")) return objectNameSuggestions(args[1], args[2]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("file") || args[0].equalsIgnoreCase("savefile"))) return filter(List.of("save", "load", "put", "remove", "list", "confirm"), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("file") || args[0].equalsIgnoreCase("savefile")) && args[1].equalsIgnoreCase("save")) return objectIdSuggestions(args[2]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("file") || args[0].equalsIgnoreCase("savefile")) && (args[1].equalsIgnoreCase("load") || args[1].equalsIgnoreCase("put") || args[1].equalsIgnoreCase("remove"))) return filter(plugin.persistentGroupStore().listSaveFiles(), args[2]);
        if (args.length == 4 && (args[0].equalsIgnoreCase("file") || args[0].equalsIgnoreCase("savefile")) && (args[1].equalsIgnoreCase("load") || args[1].equalsIgnoreCase("put"))) return objectIdSuggestions(args[3]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug") || args[0].equals("调试"))) return filter(debugGroupSuggestions(), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("debug") || args[0].equals("调试"))) return filter(debugNameSuggestions(args[1]), args[2]);
        if (args.length == 4 && (args[0].equalsIgnoreCase("debug") || args[0].equals("调试"))) return filter(List.of("show", "hide", "显示", "隐藏"), args[3]);
        if (args.length == 5 && (args[0].equalsIgnoreCase("debug") || args[0].equals("调试"))) {
            List<String> types = new ArrayList<>();
            types.add("*");
            types.addAll(DebugOverlayManager.types());
            types.addAll(List.of(
                    "mass", "position", "velocity", "acceleration", "angvel", "angacc", "size", "force", "gravity",
                    "buoyancy", "fluiddrag", "torque", "inertia", "worldinertia", "invmass", "sleep", "aabb", "obb",
                    "bulletpos", "bulletcom", "bulletinterp", "bulletrot", "bulletlinvel", "bulletangvel",
                    "bulletinterplinvel", "bulletinterpangvel", "bulletgravity", "bulletdamping",
                    "bulletsleepthreshold", "bulletfriction", "bulletrestitution", "bulletactivation",
                    "bulletcollision", "bulletccd", "bulletshape", "bulletscale", "bulletboundsphere",
                    "bulletaabb", "bulletinertia", "bulletconstraints"
            ));
            return filter(types, args[4]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("persist") || args[0].equalsIgnoreCase("persistent") || args[0].equals("持久化"))) {
            return filter(List.of("save", "remove", "保存", "取消"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("persist") || args[0].equalsIgnoreCase("persistent") || args[0].equals("持久化"))) {
            return objectIdSuggestions(args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("query") || args[0].equalsIgnoreCase("list") || args[0].equals("查询"))) return objectIdSuggestions(args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("constraint") || args[0].equals("约束"))) return filter(List.of("create", "创建"), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("constraint") || args[0].equals("约束")) && (args[1].equalsIgnoreCase("create") || args[1].equals("创建"))) {
            return filter(List.of("point", "distance", "spring", "hinge", "slider", "fixed", "6dof", "cone", "点", "距离", "弹簧", "铰链", "滑轨", "固定", "锥形"), args[2]);
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("constraint") || args[0].equals("约束")) && (args[1].equalsIgnoreCase("create") || args[1].equals("创建"))) {
            return filter(List.of(constraintArgumentHint(args[2], args.length)), args[args.length - 1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("step") || args[0].equals("步进"))) return filter(List.of("1", "5", "20", "100"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("fast") || args[0].equals("快速"))) return filter(List.of("1", "5", "10", "60"), args[1]);
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("block")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(blockArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("sphere")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            if (args.length == 7) return filter(blockMaterialSuggestions(), args[6]);
            return filter(List.of(sphereArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("line")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(lineArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("rod")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(rodArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("rope")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(ropeArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("spring")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(springArgumentHint(args.length)), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        return values.stream()
                .filter(value -> value != null && !value.isEmpty())
                .filter(value -> value.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> blockMaterialSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("<精细度>");
        for (Material material : Material.values()) {
            if (material.isBlock() && material != Material.AIR) {
                suggestions.add(material.name());
            }
        }
        return suggestions;
    }

    private String blockArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<大小X>";
            case 6: return "<大小Y>";
            case 7: return "<大小Z>";
            case 8: return "<质量>";
            case 9: return "<vx>";
            case 10: return "<vy>";
            case 11: return "<vz>";
            case 12: return "<wx>";
            case 13: return "<wy>";
            case 14: return "<wz>";
            default: return "";
        }
    }

    private String lineArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<x1>";
            case 6: return "<y1>";
            case 7: return "<z1>";
            case 8: return "<x2>";
            case 9: return "<y2>";
            case 10: return "<z2>";
            default: return "";
        }
    }

    private String sphereArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<半径>";
            case 6: return "<质量>";
            case 7: return "<材质>";
            case 8: return "<精细度>";
            default: return "";
        }
    }

    private String rodArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<长度>";
            default: return "";
        }
    }

    private String ropeArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<长度>";
            case 6: return "<段数>";
            default: return "";
        }
    }

    private String springArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<组id>";
            case 4: return "<名称>";
            case 5: return "<长度>";
            case 6: return "<刚度>";
            case 7: return "<阻尼>";
            case 8: return "<圈数>";
            case 9: return "<端点质量>";
            case 10: return "<端点大小>";
            case 11: return "<半径>";
            default: return "";
        }
    }

    private String constraintArgumentHint(String type, int argLength) {
        String normalized = type.toLowerCase();
        if (normalized.equals("distance") || normalized.equals("距离")) {
            return argLength == 4 ? "<距离>" : "";
        }
        if (normalized.equals("spring") || normalized.equals("弹簧")) {
            switch (argLength) {
                case 4: return "<原长>";
                case 5: return "<刚度>";
                case 6: return "<阻尼>";
                default: return "";
            }
        }
        if (normalized.equals("fixed") || normalized.equals("固定")) {
            return argLength == 4 ? "<最大冲量>" : "";
        }
        if (normalized.equals("slider") || normalized.equals("滑轨") || normalized.equals("6dof")) {
            switch (argLength) {
                case 4: return "<线性下限>";
                case 5: return "<线性上限>";
                case 6: return "<角度下限>";
                case 7: return "<角度上限>";
                default: return "";
            }
        }
        if (normalized.equals("cone") || normalized.equals("锥形")) {
            switch (argLength) {
                case 4: return "<摆动角>";
                case 5: return "<扭转角>";
                default: return "";
            }
        }
        return "";
    }

    private List<String> massKeySuggestions(String prefix) {
        String valuePrefix = prefix.substring(PhysConfig.ENTITY_PLAYER_MASS_PREFIX.length()).toLowerCase();
        List<String> suggestions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(valuePrefix)) {
                suggestions.add(PhysConfig.ENTITY_PLAYER_MASS_PREFIX + player.getName());
            }
        }
        suggestions.add(PhysConfig.ENTITY_PLAYER_MASS_PREFIX + "@p");
        suggestions.add(PhysConfig.ENTITY_PLAYER_MASS_PREFIX + "@e[type=!player,limit=1,sort=nearest]");
        return suggestions;
    }

    private List<String> entityTypeMassKeySuggestions(String prefix) {
        String valuePrefix = prefix.substring(PhysConfig.ENTITY_TYPE_MASS_PREFIX.length()).toUpperCase();
        List<String> suggestions = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type.name().startsWith(valuePrefix)) {
                suggestions.add(PhysConfig.ENTITY_TYPE_MASS_PREFIX + type.name());
            }
        }
        return suggestions;
    }

    private List<String> objectIdSuggestions(String prefix) {
        List<String> suggestions = new ArrayList<>(ObjectUtil.displays.keySet());
        if (suggestions.isEmpty()) {
            suggestions.add("<id>");
        }
        return filter(suggestions, prefix);
    }

    private List<String> objectNameSuggestions(String group, String prefix) {
        Map<String, List<Display>> names = ObjectUtil.groups.get(group);
        if (names == null || names.isEmpty()) return filter(List.of("<名称>"), prefix);
        return filter(new ArrayList<>(names.keySet()), prefix);
    }

    private List<String> debugGroupSuggestions() {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("*");
        suggestions.add("^");
        suggestions.addAll(ObjectUtil.displays.keySet());
        return suggestions;
    }

    private List<String> debugNameSuggestions(String group) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("*");
        if (!group.equals("*") && !group.equals("^")) {
            Map<String, List<Display>> names = ObjectUtil.groups.get(group);
            if (names != null) suggestions.addAll(names.keySet());
        }
        return suggestions;
    }

    private List<String> propertySuggestions() {
        return List.of(
                "mass", "invmass", "position", "velocity", "rotation", "force", "torque", "acceleration", "orientation", "inertia", "size",
                "质量", "逆质量", "位置", "速度", "角速度", "力", "力矩", "加速度", "旋转", "四元数", "惯性张量", "尺寸"
        );
    }

    private List<String> propertyArgumentSuggestions(String[] args) {
        String action = args.length > 3 ? args[3].toLowerCase() : "";
        if (action.equals("get") || action.equals("获取")) return List.of();

        String property = args.length > 4 ? normalizeProperty(args[4]) : "";
        if (isScalarProperty(property)) return args.length == 6 ? List.of("<值>") : List.of();
        if (isQuaternionProperty(property)) return List.of("x", "y", "z", "w", "1", "2", "3", "4", "<值>");
        if (isMatrixProperty(property)) {
            return List.of("1|1", "1|2", "1|3", "2|1", "2|2", "2|3", "3|1", "3|2", "3|3", "x|x", "y|y", "z|z", "<值>");
        }
        if (isVectorProperty(property)) return List.of("x", "y", "z", "1", "2", "3", "<值>");
        return List.of("<值>");
    }

    private boolean isScalarProperty(String property) {
        return property.equals("mass") || property.equals("invmass");
    }

    private boolean isVectorProperty(String property) {
        return property.equals("position")
                || property.equals("velocity")
                || property.equals("rotation")
                || property.equals("force")
                || property.equals("torque")
                || property.equals("acceleration")
                || property.equals("size");
    }

    private boolean isQuaternionProperty(String property) {
        return property.equals("orientation");
    }

    private boolean isMatrixProperty(String property) {
        return property.equals("inertia");
    }

    private boolean isMaterialPropertyKey(String key) {
        String materialName = null;
        if (PhysConfig.isMaterialFrictionKey(key)) {
            materialName = key.substring(PhysConfig.MATERIAL_FRICTION_PREFIX.length());
        } else if (PhysConfig.isMaterialStaticFrictionKey(key)) {
            materialName = key.substring(PhysConfig.MATERIAL_STATIC_FRICTION_PREFIX.length());
        } else if (PhysConfig.isMaterialDynamicFrictionKey(key)) {
            materialName = key.substring(PhysConfig.MATERIAL_DYNAMIC_FRICTION_PREFIX.length());
        } else if (PhysConfig.isMaterialRollingFrictionKey(key)) {
            materialName = key.substring(PhysConfig.MATERIAL_ROLLING_FRICTION_PREFIX.length());
        } else if (PhysConfig.isMaterialRestitutionKey(key)) {
            materialName = key.substring(PhysConfig.MATERIAL_RESTITUTION_PREFIX.length());
        }
        if (materialName == null) return false;
        Material material = Material.matchMaterial(materialName);
        return material != null && material.isBlock();
    }

    private List<String> materialKeySuggestions(String prefix, String keyPrefix) {
        String valuePrefix = prefix.substring(keyPrefix.length()).toUpperCase();
        List<String> suggestions = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isBlock() && material.name().startsWith(valuePrefix)) {
                suggestions.add(keyPrefix + material.name());
            }
        }
        return suggestions;
    }

    public static Location parseMCLocation(String xStr, String yStr, String zStr, Player player) {
        Location loc = player.getLocation().clone();
        double x = parseSingleCoord(xStr, loc.getX(), loc.getDirection(), true);
        double y = parseSingleCoord(yStr, loc.getY(), loc.getDirection(), false);
        double z = parseSingleCoord(zStr, loc.getZ(), loc.getDirection(), true);
        loc.setX(x);
        loc.setY(y);
        loc.setZ(z);
        return loc;
    }

    private static double parseSingleCoord(String input, double base, org.bukkit.util.Vector dir, boolean isHorizontal) {
        if (input.startsWith("^")) {
            double offset = input.length() == 1 ? 0 : Double.parseDouble(input.substring(1));
            if (isHorizontal) {
                org.bukkit.util.Vector horizontal = dir.clone().setY(0).normalize();
                return base + horizontal.getX() * offset;
            }
            return base + dir.getY() * offset;
        }
        if (input.startsWith("~")) {
            double offset = input.length() == 1 ? 0 : Double.parseDouble(input.substring(1));
            return base + offset;
        }
        return Double.parseDouble(input);
    }

    public static Color parseHexColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() != 6) return Color.fromRGB(255, 255, 255);
            return Color.fromRGB(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
