package com.bxwbb.physmc.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Optional;

/**
 * PhysMC 物理世界的公开访问入口。
 *
 * <p>外部插件应通过该接口查询和操作物理对象，而不是直接访问内部
 * World、Bullet 引擎或 Display 注册表。</p>
 */
public interface PhysWorld {

    /**
     * 返回当前所有物理刚体的快照。
     *
     * @return 物理刚体集合
     */
    Collection<PhysBody> getBodies();

    /**
     * 查找指定组内的第一个刚体。
     *
     * @param groupId 物理组 ID
     * @return 匹配的刚体；不存在时为空
     */
    Optional<PhysBody> findBody(String groupId);

    /**
     * 查找指定组和名称对应的第一个刚体。
     *
     * @param groupId 物理组 ID
     * @param name 组内名称
     * @return 匹配的刚体；不存在时为空
     */
    Optional<PhysBody> findBody(String groupId, String name);

    /**
     * 创建一个方块显示刚体。
     *
     * @param request 创建参数
     * @return 创建出的刚体
     */
    PhysBody spawnBlock(PhysBlockRequest request);

    /**
     * 删除指定组内所有物理对象和显示实体。
     *
     * @param groupId 物理组 ID
     * @return 是否删除了至少一个对象
     */
    boolean removeGroup(String groupId);

    /**
     * 删除指定组和名称对应的物理对象和显示实体。
     *
     * @param groupId 物理组 ID
     * @param name 组内名称
     * @return 是否删除了至少一个对象
     */
    boolean remove(String groupId, String name);

    /**
     * 对 Display 对应的物理刚体施加冲量。
     *
     * @param display Display 实体
     * @param impulse Bukkit 冲量向量
     * @param hitLocation 命中位置；可为空
     * @return 是否成功施加冲量
     */
    boolean applyImpulse(Display display, Vector impulse, Location hitLocation);

    /**
     * 让玩家抓取一个 Display 对应的物理刚体。
     *
     * @param display Display 实体
     * @param player 玩家
     * @param distance 抓取距离
     * @return 是否成功抓取
     */
    boolean hold(Display display, Player player, double distance);

    /**
     * 将 Display 对应的物理刚体约束到目标位置。
     *
     * @param display Display 实体
     * @param target 目标位置
     * @return 是否成功抓取
     */
    boolean holdAt(Display display, Location target);

    /**
     * 释放 Display 对应的抓取状态。
     *
     * @param display Display 实体
     */
    void release(Display display);

    /**
     * 创建一个点约束。
     *
     * @param request 约束参数
     * @return 是否创建成功
     */
    boolean connect(PointConstraintRequest request);

    /**
     * 创建一个距离约束。
     *
     * @param request 约束参数
     * @return 是否创建成功
     */
    boolean addDistanceConstraint(DistanceConstraintRequest request);

    /**
     * 创建一个弹簧约束。
     *
     * @param firstDisplay 第一个 Display
     * @param secondDisplay 第二个 Display
     * @param restLength 弹簧原长
     * @param stiffness 刚度
     * @param damping 阻尼
     */
    void addSpring(Display firstDisplay, Display secondDisplay, double restLength, double stiffness, double damping);

    /**
     * 创建一个 Bullet 类型约束。
     *
     * @param request 约束参数
     * @return 是否创建成功
     */
    boolean addTypedConstraint(TypedConstraintRequest request);

    /**
     * 保存指定组到持久化文件。
     *
     * @param groupId 物理组 ID
     * @return 保存的刚体数量
     */
    int savePersistentGroup(String groupId);

    /**
     * 取消指定组的持久化记录。
     *
     * @param groupId 物理组 ID
     * @return 是否存在并删除了记录
     */
    boolean removePersistentGroup(String groupId);

    /**
     * 返回指定材质的动态摩擦配置值。
     *
     * @param material 材质
     * @return 摩擦系数
     */
    double getMaterialFriction(Material material);

    /**
     * 判断物理模拟是否暂停。
     *
     * @return 暂停状态
     */
    boolean isPaused();

    /**
     * 暂停物理模拟。
     */
    void pause();

    /**
     * 继续物理模拟。
     */
    void resume();

    /**
     * 手动步进指定 tick 数。
     *
     * @param ticks tick 数
     * @return 实际执行的 tick 数
     */
    int stepTicks(int ticks);

    /**
     * 快速模拟指定秒数。
     *
     * @param seconds 秒数
     * @return 执行的物理步数
     */
    int runFast(double seconds);

    /**
     * 表示一个约束端点。
     *
     * <p>端点可以绑定到 Display、实体或仅使用世界坐标。不同约束类型
     * 对端点类型的支持由底层物理引擎决定。</p>
     */
    final class Anchor {
        private final Display display;
        private final Entity entity;
        private final Location point;

        private Anchor(Display display, Entity entity, Location point) {
            this.display = display;
            this.entity = entity;
            this.point = point == null ? null : point.clone();
        }

        /**
         * 创建绑定 Display 的约束端点。
         *
         * @param display Display 实体
         * @param point 世界坐标约束点
         * @return 约束端点
         */
        public static Anchor display(Display display, Location point) {
            return new Anchor(display, null, point);
        }

        /**
         * 创建绑定实体的约束端点。
         *
         * @param entity Bukkit 实体
         * @param point 世界坐标约束点
         * @return 约束端点
         */
        public static Anchor entity(Entity entity, Location point) {
            return new Anchor(null, entity, point);
        }

        /**
         * 创建仅使用世界坐标的约束端点。
         *
         * @param point 世界坐标约束点
         * @return 约束端点
         */
        public static Anchor point(Location point) {
            return new Anchor(null, null, point);
        }

        /**
         * 返回绑定的 Display。
         *
         * @return Display；未绑定时为空
         */
        public Optional<Display> getDisplay() {
            return Optional.ofNullable(display);
        }

        /**
         * 返回绑定的实体。
         *
         * @return 实体；未绑定时为空
         */
        public Optional<Entity> getEntity() {
            return Optional.ofNullable(entity);
        }

        /**
         * 返回约束点。
         *
         * @return Location 副本；未设置时为空
         */
        public Optional<Location> getPoint() {
            return Optional.ofNullable(point == null ? null : point.clone());
        }
    }
}
