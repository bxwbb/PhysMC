package com.bxwbb.physmc.api.event;

import com.bxwbb.physmc.api.PhysBody;
import org.bukkit.Location;
import org.bukkit.Material;
import org.joml.Vector3d;

/**
 * 物理刚体进入水或岩浆等流体采样区域时触发的事件。
 */
public class PhysFluidContactEvent extends PhysContactEvent {

    private final Material fluidType;
    private final double submergedFraction;
    private final Vector3d fluidVelocity;
    private final double density;

    public PhysFluidContactEvent(PhysBody body, Material fluidType, Location contactPoint, Vector3d normal, double submergedFraction, Vector3d fluidVelocity, double density) {
        super(body, PhysContactTargetType.FLUID, contactPoint, normal, 0.0d);
        this.fluidType = fluidType;
        this.submergedFraction = submergedFraction;
        this.fluidVelocity = fluidVelocity == null ? new Vector3d() : new Vector3d(fluidVelocity);
        this.density = density;
    }

    /**
     * 返回流体材质。
     *
     * @return 流体材质
     */
    public Material getFluidType() {
        return fluidType;
    }

    /**
     * 返回刚体估算浸没比例。
     *
     * @return 浸没比例，通常为 0 到 1
     */
    public double getSubmergedFraction() {
        return submergedFraction;
    }

    /**
     * 返回流体速度。
     *
     * @return 流体速度副本
     */
    public Vector3d getFluidVelocity() {
        return new Vector3d(fluidVelocity);
    }

    /**
     * 返回流体密度配置值。
     *
     * @return 密度
     */
    public double getDensity() {
        return density;
    }
}
