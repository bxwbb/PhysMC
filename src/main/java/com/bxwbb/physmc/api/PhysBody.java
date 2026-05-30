package com.bxwbb.physmc.api;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Optional;

/**
 * PhysMC 中一个可被外部插件观察和控制的物理刚体。
 *
 * <p>该接口只暴露稳定的刚体状态和常用操作。实现类可能由单个
 * {@link Display} 或多个 Display 组成，调用方不应依赖具体实现类。</p>
 */
public interface PhysBody {

    /**
     * 返回该刚体所在的物理组 ID。
     *
     * @return 物理组 ID；如果刚体未注册到任何组则为空
     */
    Optional<String> getGroupId();

    /**
     * 返回该刚体在组内的名称。
     *
     * @return 组内名称；如果刚体未注册名称则为空
     */
    Optional<String> getName();

    /**
     * 返回刚体关联的 Display 实体。
     *
     * <p>返回列表是快照，修改该列表不会影响物理系统。</p>
     *
     * @return Display 实体列表
     */
    List<Display> getDisplays();

    /**
     * 返回刚体中心位置。
     *
     * @return 中心位置副本
     */
    Vector3d getPosition();

    /**
     * 设置刚体中心位置。
     *
     * @param position 新位置
     */
    void setPosition(Vector3d position);

    /**
     * 返回刚体中心位置对应的 Bukkit Location。
     *
     * @return Location 副本；如果刚体没有有效 Display 则为空
     */
    Optional<Location> getLocation();

    /**
     * 返回刚体线速度。
     *
     * @return 线速度副本
     */
    Vector3d getVelocity();

    /**
     * 设置刚体线速度。
     *
     * @param velocity 新线速度
     */
    void setVelocity(Vector3d velocity);

    /**
     * 返回刚体角速度。
     *
     * @return 角速度副本
     */
    Vector3d getAngularVelocity();

    /**
     * 设置刚体角速度。
     *
     * @param angularVelocity 新角速度
     */
    void setAngularVelocity(Vector3d angularVelocity);

    /**
     * 返回刚体旋转四元数。
     *
     * @return 旋转四元数副本
     */
    Quaterniond getOrientation();

    /**
     * 设置刚体旋转四元数。
     *
     * @param orientation 新旋转四元数
     */
    void setOrientation(Quaterniond orientation);

    /**
     * 返回刚体尺寸。
     *
     * @return 宽、高、深尺寸副本
     */
    Vector3d getSize();

    /**
     * 返回刚体质量。
     *
     * @return 质量；0 表示静态或无限质量刚体
     */
    double getMass();

    /**
     * 设置刚体质量。
     *
     * @param mass 新质量；0 表示静态或无限质量刚体
     * @throws IllegalArgumentException 当质量小于 0 时抛出
     */
    void setMass(double mass);

    /**
     * 对刚体质心施加冲量。
     *
     * @param impulse 冲量向量
     */
    void applyImpulse(Vector3d impulse);

    /**
     * 对刚体施加力。
     *
     * @param force 力向量
     */
    void addForce(Vector3d force);

    /**
     * 唤醒或休眠刚体。
     *
     * @param awake 是否唤醒
     */
    void setAwake(boolean awake);

    /**
     * 返回刚体是否处于唤醒状态。
     *
     * @return 唤醒状态
     */
    boolean isAwake();

    /**
     * 将当前刚体状态同步到显示实体。
     */
    void sync();
}
