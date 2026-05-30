package com.bxwbb.physmc.api.event;

import com.bxwbb.physmc.api.PhysBody;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.joml.Vector3d;

/**
 * 物理刚体与 Minecraft 方块发生接触时触发的事件。
 */
public class PhysBlockContactEvent extends PhysContactEvent {

    private final Block block;
    private final Material material;

    public PhysBlockContactEvent(PhysBody body, Block block, Location contactPoint, Vector3d normal, double impulse) {
        super(body, PhysContactTargetType.BLOCK, contactPoint, normal, impulse);
        this.block = block;
        this.material = block == null ? Material.AIR : block.getType();
    }

    /**
     * 返回接触到的方块。
     *
     * @return 方块；未知时为空
     */
    public Block getBlock() {
        return block;
    }

    /**
     * 返回接触方块的材质。
     *
     * @return 材质；未知时为 {@link Material#AIR}
     */
    public Material getMaterial() {
        return material;
    }
}
