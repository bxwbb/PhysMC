package com.bxwbb.physmc.api.event;

import com.bxwbb.physmc.api.PhysBody;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.joml.Vector3d;

/**
 * 物理刚体与 Bukkit 实体发生接触时触发的事件。
 */
public class PhysEntityContactEvent extends PhysContactEvent {

    private final Entity entity;

    public PhysEntityContactEvent(PhysBody body, Entity entity, Location contactPoint, Vector3d normal, double impulse) {
        super(body, PhysContactTargetType.ENTITY, contactPoint, normal, impulse);
        this.entity = entity;
    }

    /**
     * 返回接触到的实体。
     *
     * @return Bukkit 实体
     */
    public Entity getEntity() {
        return entity;
    }
}
