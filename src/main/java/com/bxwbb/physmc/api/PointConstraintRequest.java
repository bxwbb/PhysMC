package com.bxwbb.physmc.api;

/**
 * 点约束创建参数。
 */
public final class PointConstraintRequest {

    private final PhysWorld.Anchor first;
    private final PhysWorld.Anchor second;
    private final boolean preserveCenterDistance;

    /**
     * 创建点约束参数。
     *
     * @param first 第一个端点
     * @param second 第二个端点
     */
    public PointConstraintRequest(PhysWorld.Anchor first, PhysWorld.Anchor second) {
        this(first, second, false);
    }

    /**
     * 创建点约束参数。
     *
     * @param first 第一个端点
     * @param second 第二个端点
     * @param preserveCenterDistance 是否保留两个刚体中心的距离
     */
    public PointConstraintRequest(PhysWorld.Anchor first, PhysWorld.Anchor second, boolean preserveCenterDistance) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("约束端点不能为空");
        }
        this.first = first;
        this.second = second;
        this.preserveCenterDistance = preserveCenterDistance;
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
     * 返回是否保留中心距离。
     *
     * @return 是否保留中心距离
     */
    public boolean isPreserveCenterDistance() {
        return preserveCenterDistance;
    }
}
