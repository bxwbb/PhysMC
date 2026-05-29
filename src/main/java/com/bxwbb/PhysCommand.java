package com.bxwbb;

import com.bxwbb.obj.Box;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.phy.World;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.ObjectUtil;
import com.bxwbb.util.debug.LineDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PhysCommand implements CommandExecutor, TabCompleter {

    private final PhysMC plugin;
    private final List<String> commands = new ArrayList<>();
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
                case "spawn":
                    return handleSpawn(sender);
                case "remove":
                    return handleRemove(sender);
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

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sendError(sender, "该命令只能由玩家执行。");
            return true;
        }
        if (!hasNext()) {
            sendError(sender, "请输入 spawn 类型。用法: /physmc spawn block|line");
            return true;
        }

        Player player = (Player) sender;
        String type = consume().toLowerCase();
        switch (type) {
            case "help":
                sender.sendMessage("block - 刚体方块，用法: /physmc spawn block <id> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
                sender.sendMessage("line - 调试线段，用法: /physmc spawn line <id> <x1 y1 z1> <x2 y2 z2>");
                return true;
            case "line":
                return spawnLine(sender, player);
            case "block":
                return spawnBlock(sender, player);
            default:
                sendError(sender, "未知 spawn 类型: " + type);
                return true;
        }
    }

    private boolean spawnLine(CommandSender sender, Player player) {
        if (remaining() < 7) {
            sendError(sender, "用法: /physmc spawn line <id> <x1 y1 z1> <x2 y2 z2>");
            return true;
        }

        String id = consume();
        Location start = parseMCLocation(consume(), consume(), consume(), player);
        Location end = parseMCLocation(consume(), consume(), consume(), player);
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc spawn line <id> <x1 y1 z1> <x2 y2 z2>");
            return true;
        }

        Material material = player.getInventory().getItemInMainHand().getType();
        if (material.equals(Material.AIR)) material = Material.STONE;
        LineDisplay lineDisplay = new LineDisplay(start, end, material);
        lineDisplay.addWithName(id);
        sendSuccess(sender, "创建成功。");
        return true;
    }

    private boolean spawnBlock(CommandSender sender, Player player) {
        if (remaining() < 5) {
            sendError(sender, "用法: /physmc spawn block <id> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
            return true;
        }

        String id = consume();
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
            sendError(sender, "速度参数必须是 3 个一组。用法: /physmc spawn block <id> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
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
        physBlockDisplay.addWithName(id);
        PhysManager.getInstance().world.boxes.add(new Box(physBlockDisplay));
        sendSuccess(sender, "创建成功。");
        return true;
    }

    private boolean handleRemove(CommandSender sender) {
        if (!hasNext()) {
            sendError(sender, "请输入要删除的 ID。");
            return true;
        }

        String id = consume();
        if (hasNext()) {
            sendError(sender, "参数过多。用法: /physmc remove <id>");
            return true;
        }

        ObjectUtil.removeDisplay(id);
        World.getInstance().boxes.removeIf(rigidBody -> {
            for (Display display : rigidBody.body.getAllDisplay()) {
                if (!display.isDead()) return false;
            }
            return true;
        });
        sendSuccess(sender, "已删除对象。");
        return true;
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
        return PhysConfig.KEYS.contains(key) || PhysConfig.isEntityMassKey(key) || PhysConfig.isEntityTypeMassKey(key);
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
        sender.sendMessage(Component.text("配置项: " + key).color(TextColor.color(0xFFAA00)));
        sender.sendMessage(Component.text("说明: " + entry.description).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("建议: " + entry.suggestion).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("默认值: " + entry.defaultValue).color(TextColor.color(0xCCCCCC)));
        sender.sendMessage(Component.text("当前值: " + getConfigValue(key)).color(TextColor.color(0x00FF00)));
    }

    private void setConfigValue(String key, String value) {
        Object defaultValue = (PhysConfig.isEntityMassKey(key) || PhysConfig.isEntityTypeMassKey(key)) ? 0.0d : PhysConfig.get(key).defaultValue;
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

    private Object getConfigValue(String key) {
        if (plugin.getConfig().contains(key)) return plugin.getConfig().get(key);
        String legacyKey = PhysConfig.LEGACY_KEYS.get(key);
        if (legacyKey != null && plugin.getConfig().contains(legacyKey)) return plugin.getConfig().get(legacyKey);
        if (PhysConfig.isEntityMassKey(key)) return 0.0d;
        if (PhysConfig.isEntityTypeMassKey(key)) return 0.0d;
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

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("PhysMC 命令:").color(TextColor.color(0xFFAA00)));
        sender.sendMessage("/physmc spawn block <id> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]");
        sender.sendMessage("/physmc spawn line <id> <x1 y1 z1> <x2 y2 z2>");
        sender.sendMessage("/physmc get <键名>");
        sender.sendMessage("/physmc set <键名> <值>");
        sender.sendMessage("/physmc remove <id>");
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(TextColor.color(0xFF3333)));
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(TextColor.color(0x00FF00)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("help", "spawn", "remove", "get", "set"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            List<String> keys = new ArrayList<>(PhysConfig.KEYS);
            keys.add(PhysConfig.ENTITY_PLAYER_MASS_PREFIX);
            keys.add(PhysConfig.ENTITY_TYPE_MASS_PREFIX);
            if (args[1].startsWith(PhysConfig.ENTITY_PLAYER_MASS_PREFIX)) keys.addAll(massKeySuggestions(args[1]));
            if (args[1].startsWith(PhysConfig.ENTITY_TYPE_MASS_PREFIX)) keys.addAll(entityTypeMassKeySuggestions(args[1]));
            return filter(keys, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("true", "false", "<value>");
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return filter(List.of("help", "block", "line"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return objectIdSuggestions(args[1]);
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("block")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(blockArgumentHint(args.length)), args[args.length - 1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("spawn") && args[1].equalsIgnoreCase("line")) {
            if (args.length == 3) return objectIdSuggestions(args[2]);
            return filter(List.of(lineArgumentHint(args.length)), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        return values.stream()
                .filter(value -> value != null && !value.isEmpty())
                .filter(value -> value.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private String blockArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<id>";
            case 4: return "<大小X>";
            case 5: return "<大小Y>";
            case 6: return "<大小Z>";
            case 7: return "<质量>";
            case 8: return "<vx>";
            case 9: return "<vy>";
            case 10: return "<vz>";
            case 11: return "<wx>";
            case 12: return "<wy>";
            case 13: return "<wz>";
            default: return "";
        }
    }

    private String lineArgumentHint(int argLength) {
        switch (argLength) {
            case 3: return "<id>";
            case 4: return "<x1>";
            case 5: return "<y1>";
            case 6: return "<z1>";
            case 7: return "<x2>";
            case 8: return "<y2>";
            case 9: return "<z2>";
            default: return "";
        }
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
