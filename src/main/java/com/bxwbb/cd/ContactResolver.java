package com.bxwbb.cd;

import com.bxwbb.math.Vector3;

import java.util.List;

public class ContactResolver {

    // 速度求解迭代次数（默认8）
    public int velocityIterations = 8;
    // 位置求解迭代次数（默认8）
    public int positionIterations = 8;
    // 速度收敛阈值（小于该值不再修正）
    public double velocityEpsilon = 0.01;
    // 位置收敛阈值（小于该值不再修正）
    public double positionEpsilon = 0.01;
    // 实际使用的速度迭代次数（运行时）
    public int velocityIterationsUsed = 0;
    // 实际使用的位置迭代次数（运行时）
    public int positionIterationsUsed = 0;
    // 配置是否有效
    public boolean validSettings = false;

    public void resolveContacts(Contact[] contacts, int numContacts, double duration) {
//        System.out.println("开始解决碰撞!");
        if (numContacts == 0) return;
//        System.out.println("有碰撞!" + numContacts);
        if (!isValid()) return;
//        System.out.println("参数有效!");
        prepareContacts(contacts, numContacts, duration);
        adjustPositions(contacts, numContacts, duration);
        adjustVelocities(contacts, numContacts, duration);
    }

    public boolean isValid() {
        return (velocityIterations > 0) && (positionIterations > 0) && (positionEpsilon >= 0.0f);
    }

    public void prepareContacts(Contact[] contacts, int numContacts, double duration) {
        for (int i = 0; i < numContacts; i++) {
            contacts[i].calculateInternals(duration);
        }
    }

    public void adjustPositions(Contact[] c, int numContacts, double duration) {
        int i, index;
        Vector3[] linearChange = {new Vector3(), new Vector3()}, angularChange = {new Vector3(), new Vector3()};
        double max;
        Vector3 deltaPosition;
        positionIterationsUsed = 0;
        while (positionIterationsUsed < positionIterations) {
            max = positionEpsilon;
            index = numContacts;
            for (i = 0; i < numContacts; i++) {
                if (c[i].penetration > max) {
                    max = c[i].penetration;
                    index = i;
                }
            }
            if (index == numContacts) break;
            c[index].matchAwakeState();
            c[index].applyPositionChange(
                    linearChange,
                    angularChange,
                    max);
            for (i = 0; i < numContacts; i++) {
                for (int b = 0; b < 2; b++)
                    if (c[i].body[b] != null) {
                        for (int d = 0; d < 2; d++) {
                            if (c[i].body[b] == c[index].body[d]) {
                                deltaPosition = linearChange[d].addNew(angularChange[d].vectorProductNew(c[i].relativeContactPosition[b]));
                                c[i].penetration += deltaPosition.scalarProduct(c[i].contactNormal) * (b != 0 ? 1 : -1);
                            }
                        }
                    }
            }
            positionIterationsUsed++;
        }
    }

    public void adjustVelocities(Contact[] c,
                                 int numContacts,
                                 double duration) {
        Vector3[] velocityChange = {new Vector3(), new Vector3()}, rotationChange = {new Vector3(), new Vector3()};
        Vector3 deltaVel;
        velocityIterationsUsed = 0;
        while (velocityIterationsUsed < velocityIterations) {
            double max = velocityEpsilon;
            int index = numContacts;
            for (int i = 0; i < numContacts; i++) {
                if (c[i].desiredDeltaVelocity > max) {
                    max = c[i].desiredDeltaVelocity;
                    index = i;
                }
            }
            if (index == numContacts) break;
            c[index].matchAwakeState();
            c[index].applyVelocityChange(velocityChange, rotationChange);
            for (int i = 0; i < numContacts; i++) {
                for (int b = 0; b < 2; b++) {
                    if (c[i].body[b] != null) {
                        for (int d = 0; d < 2; d++) {
                            if (c[i].body[b] == c[index].body[d]) {
                                deltaVel = velocityChange[d].addNew(rotationChange[d].vectorProductNew(c[i].relativeContactPosition[b]));
                                c[i].contactVelocity.add(c[i].contactToWorld.transformTranspose(deltaVel).mulNew(b != 0 ? -1 : 1));
                                c[i].calculateDesiredDeltaVelocity(duration);
                            }
                        }
                    }
                }
            }
            velocityIterationsUsed++;
        }
    }

    public void setIterations(int iterations) {
        setIterations(iterations, iterations);
    }

    void setIterations(int velocityIterations, int positionIterations) {
        this.velocityIterations = velocityIterations;
        this.positionIterations = positionIterations;
    }
}
