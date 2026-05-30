package com.bxwbb.util;

import com.bxwbb.PhysMC;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class BlockDisplayScale {

    public static final float NORMAL = 1.0f;
    public static final float PLAYER_HEAD = 1.25f;

    private BlockDisplayScale() {
    }

    public static void setMultiplier(BlockDisplay display, float multiplier) {
        if (display == null) return;
        Transformation transformation = display.getTransformation();
        Vector3f scale = transformation.getScale();
        float current = multiplier(display);
        float baseX = Math.abs(scale.x) < 0.0001f ? NORMAL : scale.x / current;
        float baseY = Math.abs(scale.y) < 0.0001f ? NORMAL : scale.y / current;
        float baseZ = Math.abs(scale.z) < 0.0001f ? NORMAL : scale.z / current;
        scale.set(baseX * multiplier, baseY * multiplier, baseZ * multiplier);
        display.setTransformation(transformation);
        display.getPersistentDataContainer().set(key(), PersistentDataType.FLOAT, multiplier);
    }

    public static float multiplier(BlockDisplay display) {
        if (display == null) return NORMAL;
        Float factor = display.getPersistentDataContainer().get(key(), PersistentDataType.FLOAT);
        return factor == null || Math.abs(factor) < 0.0001f ? NORMAL : factor;
    }

    private static NamespacedKey key() {
        return new NamespacedKey(PhysMC.getPlugin(PhysMC.class), "material_head_scale");
    }
}
