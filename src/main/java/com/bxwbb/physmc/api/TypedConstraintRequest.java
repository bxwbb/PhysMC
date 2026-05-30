package com.bxwbb.physmc.api;

/**
 * Bullet 类型约束创建参数。
 */
public final class TypedConstraintRequest {

    private final String type;
    private final PhysWorld.Anchor first;
    private final PhysWorld.Anchor second;
    private final double lowerLinear;
    private final double upperLinear;
    private final double lowerAngular;
    private final double upperAngular;
    private final double breakImpulse;

    private TypedConstraintRequest(Builder builder) {
        this.type = builder.type;
        this.first = builder.first;
        this.second = builder.second;
        this.lowerLinear = builder.lowerLinear;
        this.upperLinear = builder.upperLinear;
        this.lowerAngular = builder.lowerAngular;
        this.upperAngular = builder.upperAngular;
        this.breakImpulse = builder.breakImpulse;
    }

    /**
     * 创建构造器。
     *
     * @param type 约束类型，例如 hinge、slider、fixed、6dof、cone
     * @param first 第一个端点
     * @param second 第二个端点
     * @return 构造器
     */
    public static Builder builder(String type, PhysWorld.Anchor first, PhysWorld.Anchor second) {
        return new Builder(type, first, second);
    }

    /**
     * 返回约束类型。
     *
     * @return 约束类型
     */
    public String getType() {
        return type;
    }

    /**
     * 返回第一个端点。
     *
     * @return 第一个端点
     */
    public PhysWorld.Anchor getFirst() {
        return first;
    }

    /**
     * 返回第二个端点。
     *
     * @return 第二个端点
     */
    public PhysWorld.Anchor getSecond() {
        return second;
    }

    /**
     * 返回线性下限。
     *
     * @return 线性下限
     */
    public double getLowerLinear() {
        return lowerLinear;
    }

    /**
     * 返回线性上限。
     *
     * @return 线性上限
     */
    public double getUpperLinear() {
        return upperLinear;
    }

    /**
     * 返回角度下限。
     *
     * @return 角度下限
     */
    public double getLowerAngular() {
        return lowerAngular;
    }

    /**
     * 返回角度上限。
     *
     * @return 角度上限
     */
    public double getUpperAngular() {
        return upperAngular;
    }

    /**
     * 返回断裂冲量。
     *
     * @return 断裂冲量；小于等于 0 表示不主动设置断裂阈值
     */
    public double getBreakImpulse() {
        return breakImpulse;
    }

    /**
     * 类型约束构造器。
     */
    public static final class Builder {
        private final String type;
        private final PhysWorld.Anchor first;
        private final PhysWorld.Anchor second;
        private double lowerLinear;
        private double upperLinear;
        private double lowerAngular;
        private double upperAngular;
        private double breakImpulse;

        private Builder(String type, PhysWorld.Anchor first, PhysWorld.Anchor second) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("type 不能为空");
            }
            if (first == null || second == null) {
                throw new IllegalArgumentException("约束端点不能为空");
            }
            this.type = type;
            this.first = first;
            this.second = second;
        }

        /**
         * 设置线性限制。
         *
         * @param lower 下限
         * @param upper 上限
         * @return 当前构造器
         */
        public Builder linearLimits(double lower, double upper) {
            this.lowerLinear = lower;
            this.upperLinear = upper;
            return this;
        }

        /**
         * 设置角度限制。
         *
         * @param lower 下限
         * @param upper 上限
         * @return 当前构造器
         */
        public Builder angularLimits(double lower, double upper) {
            this.lowerAngular = lower;
            this.upperAngular = upper;
            return this;
        }

        /**
         * 设置断裂冲量。
         *
         * @param breakImpulse 断裂冲量；小于等于 0 表示不主动设置断裂阈值
         * @return 当前构造器
         */
        public Builder breakImpulse(double breakImpulse) {
            this.breakImpulse = breakImpulse;
            return this;
        }

        /**
         * 构建请求对象。
         *
         * @return 类型约束请求
         */
        public TypedConstraintRequest build() {
            return new TypedConstraintRequest(this);
        }
    }
}
