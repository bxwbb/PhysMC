package com.bxwbb.physmc.api.event;

import com.bxwbb.physmc.api.PhysBody;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.joml.Vector3d;

/**
 * PhysMC 物理接触事件的基类。
 *
 * <p>事件在主线程触发，监听器可以读取接触目标、接触点、法线和冲量。
 * 需要操作刚体时应优先使用 {@link #getPhysBody()}。</p>
 */
public abstract class PhysContactEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PhysBody body;
    private final PhysContactTargetType targetType;
    private final Location contactPoint;
    private final Vector3d normal;
    private final double impulse;

    protected PhysContactEvent(PhysBody body, PhysContactTargetType targetType, Location contactPoint, Vector3d normal, double impulse) {
        this.body = body;
        this.targetType = targetType;
        this.contactPoint = contactPoint == null ? null : contactPoint.clone();
        this.normal = normal == null ? new Vector3d() : new Vector3d(normal);
        this.impulse = impulse;
    }

    /**
     * 返回发生接触的物理刚体。
     *
     * @return 物理刚体
     */
    public PhysBody getPhysBody() {
        return body;
    }

    /**
     * 返回发生接触的物理刚体。
     *
     * @return 物理刚体
     * @deprecated 请使用 {@link #getPhysBody()}。
     */
    @Deprecated
    public PhysBody getBody() {
        return body;
    }

    /**
     * 返回接触目标类型。
     *
     * @return 接触目标类型
     */
    public PhysContactTargetType getTargetType() {
        return targetType;
    }

    /**
     * 返回世界中的接触点。
     *
     * @return 接触点副本；未知时为空
     */
    public Location getContactPoint() {
        return contactPoint == null ? null : contactPoint.clone();
    }

    /**
     * 返回接触法线。
     *
     * @return 法线副本
     */
    public Vector3d getNormal() {
        return new Vector3d(normal);
    }

    /**
     * 返回接触冲量。
     *
     * @return 冲量大小；流体接触通常为 0
     */
    public double getImpulse() {
        return impulse;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
