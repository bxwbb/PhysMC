package com.bxwbb.physmc.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.joml.Vector3d;

/**
 * 创建方块刚体所需的参数。
 */
public final class PhysBlockRequest {

    private final String groupId;
    private final String name;
    private final Location location;
    private final Material material;
    private final Vector3d size;
    private final double mass;
    private final Vector3d velocity;
    private final Vector3d angularVelocity;

    private PhysBlockRequest(Builder builder) {
        this.groupId = builder.groupId;
        this.name = builder.name;
        this.location = builder.location == null ? null : builder.location.clone();
        this.material = builder.material;
        this.size = new Vector3d(builder.size);
        this.mass = builder.mass;
        this.velocity = new Vector3d(builder.velocity);
        this.angularVelocity = new Vector3d(builder.angularVelocity);
    }

    /**
     * 创建请求构造器。
     *
     * @param groupId 物理组 ID
     * @param name 组内名称
     * @param location 生成位置
     * @return 构造器
     */
    public static Builder builder(String groupId, String name, Location location) {
        return new Builder(groupId, name, location);
    }

    /**
     * 返回物理组 ID。
     *
     * @return 物理组 ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * 返回组内名称。
     *
     * @return 组内名称
     */
    public String getName() {
        return name;
    }

    /**
     * 返回生成位置。
     *
     * @return Location 副本
     */
    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    /**
     * 返回显示材质。
     *
     * @return 材质
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * 返回刚体尺寸。
     *
     * @return 尺寸副本
     */
    public Vector3d getSize() {
        return new Vector3d(size);
    }

    /**
     * 返回刚体质量。
     *
     * @return 质量；0 表示静态或无限质量刚体
     */
    public double getMass() {
        return mass;
    }

    /**
     * 返回初始线速度。
     *
     * @return 线速度副本
     */
    public Vector3d getVelocity() {
        return new Vector3d(velocity);
    }

    /**
     * 返回初始角速度。
     *
     * @return 角速度副本
     */
    public Vector3d getAngularVelocity() {
        return new Vector3d(angularVelocity);
    }

    /**
     * 方块刚体请求构造器。
     */
    public static final class Builder {
        private final String groupId;
        private final String name;
        private final Location location;
        private Material material = Material.COMMAND_BLOCK;
        private Vector3d size = new Vector3d(1.0d);
        private double mass = 1.0d;
        private Vector3d velocity = new Vector3d();
        private Vector3d angularVelocity = new Vector3d();

        private Builder(String groupId, String name, Location location) {
            if (groupId == null || groupId.isBlank()) {
                throw new IllegalArgumentException("groupId 不能为空");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name 不能为空");
            }
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("location 必须包含有效世界");
            }
            this.groupId = groupId;
            this.name = name;
            this.location = location.clone();
        }

        /**
         * 设置显示材质。
         *
         * @param material 材质
         * @return 当前构造器
         */
        public Builder material(Material material) {
            if (material == null || !material.isBlock()) {
                throw new IllegalArgumentException("material 必须是方块材质");
            }
            this.material = material;
            return this;
        }

        /**
         * 设置刚体尺寸。
         *
         * @param size 尺寸
         * @return 当前构造器
         */
        public Builder size(Vector3d size) {
            if (size == null || size.x <= 0.0d || size.y <= 0.0d || size.z <= 0.0d) {
                throw new IllegalArgumentException("size 的三个分量都必须大于 0");
            }
            this.size = new Vector3d(size);
            return this;
        }

        /**
         * 设置刚体质量。
         *
         * @param mass 质量；0 表示静态或无限质量刚体
         * @return 当前构造器
         */
        public Builder mass(double mass) {
            if (mass < 0.0d) {
                throw new IllegalArgumentException("mass 不能小于 0");
            }
            this.mass = mass;
            return this;
        }

        /**
         * 设置初始线速度。
         *
         * @param velocity 线速度
         * @return 当前构造器
         */
        public Builder velocity(Vector3d velocity) {
            this.velocity = velocity == null ? new Vector3d() : new Vector3d(velocity);
            return this;
        }

        /**
         * 设置初始角速度。
         *
         * @param angularVelocity 角速度
         * @return 当前构造器
         */
        public Builder angularVelocity(Vector3d angularVelocity) {
            this.angularVelocity = angularVelocity == null ? new Vector3d() : new Vector3d(angularVelocity);
            return this;
        }

        /**
         * 构建请求对象。
         *
         * @return 方块刚体请求
         */
        public PhysBlockRequest build() {
            return new PhysBlockRequest(this);
        }
    }
}
