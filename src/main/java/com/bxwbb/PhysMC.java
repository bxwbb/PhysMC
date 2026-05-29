package com.bxwbb;

import com.bxwbb.event.DisplayHitListener;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PhysMC extends JavaPlugin {

    @Override
    public void onEnable() {
        super.onEnable();
        printStartupLogo();
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        migrateLegacyConfigKeys();
        populateEntityTypeMassDefaults();
        saveConfig();

        new PhysCommand(this);
        getServer().getPluginManager().registerEvents(new DisplayHitListener(), this);

        Bukkit.getScheduler().runTaskTimer(this, PhysManager.getInstance()::tick, 0L, 1L);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        SpawnUtil.removeAll();
    }

    private void migrateLegacyConfigKeys() {
        for (String key : PhysConfig.KEYS) {
            String legacyKey = PhysConfig.LEGACY_KEYS.get(key);
            if (legacyKey != null && !getConfig().contains(key) && getConfig().contains(legacyKey)) {
                getConfig().set(key, getConfig().get(legacyKey));
            }
        }
    }

    private void populateEntityTypeMassDefaults() {
        for (EntityType type : EntityType.values()) {
            String key = PhysConfig.ENTITY_TYPE_MASS_PREFIX + type.name();
            if (!getConfig().contains(key)) {
                getConfig().set(key, defaultMassForType(type));
            }
        }
    }

    private double defaultMassForType(EntityType type) {
        String name = type.name();
        if ("PLAYER".equals(name)) {
            return getConfig().getDouble(PhysConfig.ENTITY_DEFAULT_MASS_PLAYER, 80.0d);
        }
        if (isLightEntityType(name)) {
            return 1.0d;
        }
        if ("ARMOR_STAND".equals(name)) {
            return 10.0d;
        }
        if (isVehicleEntityType(name)) {
            return 40.0d;
        }
        return getConfig().getDouble(PhysConfig.ENTITY_DEFAULT_MASS_NON_PLAYER, 30.0d);
    }

    private boolean isLightEntityType(String name) {
        return "ITEM".equals(name)
                || "EXPERIENCE_ORB".equals(name)
                || "ARROW".equals(name)
                || "SPECTRAL_ARROW".equals(name)
                || "SNOWBALL".equals(name)
                || "EGG".equals(name)
                || "FIREBALL".equals(name)
                || "SMALL_FIREBALL".equals(name)
                || "DRAGON_FIREBALL".equals(name)
                || "WITHER_SKULL".equals(name)
                || "TRIDENT".equals(name)
                || "ENDER_PEARL".equals(name);
    }

    private boolean isVehicleEntityType(String name) {
        return name.endsWith("BOAT")
                || "MINECART".equals(name)
                || name.endsWith("_MINECART");
    }

    private void printStartupLogo() {
        try (InputStream inputStream = getResource("logo.txt")) {
            if (inputStream == null) return;

            List<String> lines = readLogoLines(inputStream);
            if (lines.isEmpty()) return;

            int width = logoWidth(lines);
            String border = "+" + repeat("-", width + 2) + "+";
            Bukkit.getConsoleSender().sendMessage(border);
            for (String line : lines) {
                Bukkit.getConsoleSender().sendMessage("| " + padRight(line, width) + " |");
            }
            Bukkit.getConsoleSender().sendMessage(border);
        } catch (IOException e) {
            getLogger().warning("读取 logo.txt 失败: " + e.getMessage());
        }
    }

    private List<String> readLogoLines(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private int logoWidth(List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        return width;
    }

    private String padRight(String value, int width) {
        if (value.length() >= width) return value;
        return value + repeat(" ", width - value.length());
    }

    private String repeat(String value, int times) {
        return value.repeat(Math.max(0, times));
    }
}
