package com.bxwbb.physmc.api;

/**
 * 距离约束创建参数。
 */
public final class DistanceConstraintRequest {

    private final PhysWorld.Anchor first;
    private final PhysWorld.Anchor second;
    private final double restLength;

    /**
     * 创建距离约束参数。
     *
     * @param first 第一个端点
     * @param second 第二个端点
     * @param restLength 约束距离
     */
    public DistanceConstraintRequest(PhysWorld.Anchor first, PhysWorld.Anchor second, double restLength) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("约束端点不能为空");
        }
        if (restLength < 0.0d) {
            throw new IllegalArgumentException("restLength 不能小于 0");
        }
        this.first = first;
        this.second = second;
        this.restLength = restLength;
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
     * 返回约束距离。
     *
     * @return 约束距离
     */
    public double getRestLength() {
        return restLength;
    }
}
