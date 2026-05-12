package com.bxwbb;

import com.bxwbb.force.Aero;
import com.bxwbb.force.Buoyancy;
import com.bxwbb.force.Gravity;
import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Vector3;
import com.bxwbb.obj.Box;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.phy.World;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.ObjectUtil;
import com.bxwbb.util.debug.LineDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PhysCommand implements CommandExecutor {

    private final List<String> commands = new ArrayList<>();
    private int commandIndex = 0;

    public PhysCommand(PhysMC plugin) {
        PluginCommand command = plugin.getCommand("physmc");
        if (command == null) {
            throw new IllegalStateException("未在 plugin.yml 中注册 physmc 指令");
        }
        command.setExecutor(this);
//        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        if (strings.length == 0) {
            commandSender.sendMessage(Component.text("用法: /physmc help | spawn | remove").color(TextColor.color(0xFFAA00)));
            return false;
        }

        try {
            commandIndex = 0;
            commands.clear();
            commands.addAll(Arrays.asList(strings));

            String sub = consume();
            Player player = (Player) commandSender;
            switch (sub) {
                case "help":
                    commandSender.sendMessage("physmc spawn [type] [id] <color>");
                    commandSender.sendMessage("physmc remove [id]");
                    break;
                case "spawn":
                    try {
                        String type = consume();
                        String id;
                        double scale;
                        double inverseMass;
                        switch (type) {
                            case "help":
                                commandSender.sendMessage("block - 刚体方块 用法: physmc spawn block id 大小 逆质量(质量的倒数)");
                                commandSender.sendMessage("line - 测试用线段 用法: physmc spawn line id 起始坐标 终止坐标 (线段方块为手中的方块)");
                                break;
                            case "line":
                                if (!hasNext()) {
                                    commandSender.sendMessage(Component.text("请输入对象ID").color(TextColor.color(0xFF0000)));
                                    return false;
                                }
                                id = consume();
                                Location start;
                                Location end;
                                try {
                                    start = parseMCLocation(consume(), consume(), consume(), player);
                                    end = parseMCLocation(consume(), consume(), consume(), player);
                                } catch (Exception e) {
                                    commandSender.sendMessage(Component.text("坐标格式错误").color(TextColor.color(0xFF0000)));
                                    return false;
                                }
                                Material material = player.getInventory().getItemInMainHand().getType();
                                if (material.equals(Material.AIR)) material = Material.STONE;
                                LineDisplay lineDisplay = new LineDisplay(start, end, material);
                                lineDisplay.addWithName(id);
                                commandSender.sendMessage(Component.text("创建成功").color(TextColor.color(0x00FF00)));
                                return true;
                            case "block":
                                if (!hasNext()) {
                                    commandSender.sendMessage(Component.text("请输入对象ID").color(TextColor.color(0xFF0000)));
                                    return false;
                                }
                                id = consume();
                                if (!hasNext()) {
                                    commandSender.sendMessage(Component.text("请输入缩放").color(TextColor.color(0xFF0000)));
                                    return false;
                                }
                                scale = Double.parseDouble(consume());
                                if (!hasNext()) {
                                    commandSender.sendMessage(Component.text("请输入逆质量(质量的倒数)").color(TextColor.color(0xFF0000)));
                                    return false;
                                }
                                inverseMass = Double.parseDouble(consume());
                                PhysBlockDisplay physBlockDisplay = new PhysBlockDisplay(((Player) commandSender).getLocation());
                                Transformation transformation = physBlockDisplay.getBlockDisplay().getTransformation();
                                transformation.getScale().set(scale);
                                physBlockDisplay.getBlockDisplay().setTransformation(transformation);
                                physBlockDisplay.inverseMass = inverseMass;
                                physBlockDisplay.addWithName(id);
                                PhysManager.getInstance().world.boxes.add(new Box(physBlockDisplay));
                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, new Gravity(new Vector3(0, -10, 0)));
                                // ==================================================================================
//                                Vector3 windspeed = new Vector3(0.4, 0 ,0);
//                                AeroControl right_wing = new AeroControl(new Matrix3(0, 0, 0, -1, -0.5f, 0, 0, 0, 0),
//                                        new Matrix3(0, 0, 0, -0.995f, -0.5f, 0, 0, 0, 0),
//                                        new Matrix3(0, 0, 0, -1.005f, -0.5f, 0, 0, 0, 0),
//                                        new Vector3(-1.0f, 0.0f, 2.0f), windspeed);
//                                AeroControl left_wing = new AeroControl(new Matrix3(0, 0, 0, -1, -0.5f, 0, 0, 0, 0),
//                                        new Matrix3(0, 0, 0, -0.995f, -0.5f, 0, 0, 0, 0),
//                                        new Matrix3(0, 0, 0, -1.005f, -0.5f, 0, 0, 0, 0),
//                                        new Vector3(-1.0f, 0.0f, -2.0f), windspeed);
//                                AeroControl rudder = new AeroControl(new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, 0),
//                                        new Matrix3(0, 0, 0, 0, 0, 0, 0.01f, 0, 0),
//                                        new Matrix3(0, 0, 0, 0, 0, 0, -0.01f, 0, 0),
//                                        new Vector3(2.0f, 0.5f, 0), windspeed);
//                                Aero tail = new Aero(new Matrix3(0, 0, 0, -1, -0.5f, 0, 0, 0, -0.1f),
//                                        new Vector3(2.0f, 0, 0), windspeed);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, left_wing);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, right_wing);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, rudder);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, tail);
                                // ==================================================================================
//                                Vector3 windspeed = new Vector3(0, 0, 0);
//                                Aero sail = new Aero(new Matrix3(0, 0, 0, 0, 0, 0, 0, 0, -1.0f),
//                                        new Vector3(2.0f, 0, 0), windspeed);
//                                Buoyancy buoyancy = new Buoyancy(new Vector3(0.7f, 0.5f, 0.3f), 2.0f, 3.0f, 50f);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, sail);
//                                PhysManager.getInstance().world.forceRegistry.add(physBlockDisplay, buoyancy);
                                commandSender.sendMessage(Component.text("创建成功").color(TextColor.color(0x00FF00)));
                                return true;
                            default:
                                commandSender.sendMessage(Component.text("未知类型").color(TextColor.color(0xFF0000)));
                                return false;
                        }
                    } catch (IndexOutOfBoundsException e) {
                        commandSender.sendMessage(Component.text("参数不足，使用 physmc spawn help").color(TextColor.color(0xFF0000)));
                        return true;
                    }
                    break;
                case "remove":
                    if (hasNext()) {
                        String id = consume();
                        ObjectUtil.removeDisplay(id);
                        World.getInstance().boxes.removeIf(rigidBody -> {
                            boolean ret = true;
                            for (Display display : rigidBody.body.getAllDisplay()) {
                                if (!display.isDead()) {
                                    ret = false;
                                    break;
                                }
                            }
                            return ret;
                        });
//                        World.getInstance().forceRegistry.registrations.removeIf(registration -> !World.getInstance().boxes.contains(registration.body));
                        commandSender.sendMessage(Component.text("已删除对象").color(TextColor.color(0x00FF00)));
                    } else {
                        commandSender.sendMessage(Component.text("请输入要删除的ID").color(TextColor.color(0xFF0000)));
                    }
                    break;
                default:
                    commandSender.sendMessage(Component.text("未知子命令，输入 /physmc help 查看帮助").color(TextColor.color(0xFF0000)));
                    break;
            }
        } catch (IndexOutOfBoundsException e) {
            commandSender.sendMessage(Component.text("命令参数不足").color(TextColor.color(0xFF0000)));
            return true;
        }
        return true;
    }

    private String peek() {
        return commands.get(commandIndex);
    }

    private String consume() {
        return commands.get(commandIndex++);
    }

    private Location consumeLocation(Player player) {
        try {
            String x = consume();
            String y = consume();
            String z = consume();
            return parseMCLocation(x, y, z, player);
        } catch (IndexOutOfBoundsException e) {
            player.sendMessage(Component.text("坐标参数不足或格式错误").color(TextColor.color(0xFF0000)));
            return null;
        }
    }

    private boolean hasNext() {
        return commandIndex < commands.size();
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
            String numStr = input.substring(1);
            double offset = numStr.isBlank() ? 0 : Double.parseDouble(numStr);
            if (isHorizontal) {
                org.bukkit.util.Vector horizontal = dir.clone().setY(0).normalize();
                return base + horizontal.getX() * offset;
            } else {
                return base + dir.getY() * offset;
            }
        }
        if (input.startsWith("~")) {
            String numStr = input.substring(1);
            double offset = numStr.isBlank() ? 0 : Double.parseDouble(numStr);
            return base + offset;
        }
        return Double.parseDouble(input);
    }

    public static Color parseHexColor(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            if (hex.length() != 6) {
                return Color.fromRGB(255, 255, 255);
            }
            String rStr = hex.substring(0, 2);
            String gStr = hex.substring(2, 4);
            String bStr = hex.substring(4, 6);
            int r = Integer.parseInt(rStr, 16);
            int g = Integer.parseInt(gStr, 16);
            int b = Integer.parseInt(bStr, 16);
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}