package com.bxwbb.phy;

import com.bxwbb.cd.*;
import com.bxwbb.force.ForceRegistry;
import com.bxwbb.math.Matrix4;
import com.bxwbb.math.Vector3;
import com.bxwbb.obj.Box;

import java.util.LinkedList;
import java.util.List;

public class World {

    //    public final List<RigidBody> rigidBodies = new LinkedList<>();
    public final List<Box> boxes = new LinkedList<>();
    public final ForceRegistry forceRegistry = new ForceRegistry();
    public final ContactResolver resolver = new ContactResolver();
    public boolean calculateIterations = true;
    public CollisionData collisionData = new CollisionData();
    public int maxContacts = 100;

    private static final World instance = new World();

    public World() {
    }

    public static World getInstance() {
        return instance;
    }

    public void startFrame() {
        for (Box box : boxes) {
            box.body.clearAccumulators();
            box.body.calculateDerivedData();
        }
    }

    public void runPhysics(double duration) {
        // 更新物体
        forceRegistry.updateForces(duration);
        for (Box box : boxes) {
            box.body.integrate(duration);
            box.calculateInternals();
            box.isOverlapping = false;
            box.body.tick();
        }

        generateContacts();

        resolver.resolveContacts(
                collisionData.contacts,
                collisionData.contactCount,
                duration
        );
    }

//    public int generateContacts() {
//        int limit = maxContacts;
//
//        for (int i = 0; i < boxes.size(); i++) {
//            Box box = boxes.get(i);
//
//            if (limit <= 0) break;
//
//            int used = CollisionDetector.boxAndHalfSpace(box, ground, collisionData);
//            if (used > 0) {
//                limit--;
//            }
//
//            for (int j = i + 1; j < boxes.size(); j++) {
//                if (limit <= 0) break;
//
//                used = CollisionDetector.boxAndBox(box, boxes.get(j), collisionData);
//                if (used > 0) {
//                    limit--;
//                }
//            }
//        }
//
//        return maxContacts - limit;
//    }

    public void generateContacts() {
        // 1. 创建水平地面 Y = 50
        CollisionPlane plane = new CollisionPlane(new Vector3(0, 1, 0), 50);

        // 2. 重置碰撞数据
        collisionData.reset(maxContacts);
        collisionData.friction = 0.9d;
        collisionData.restitution = 0.6d;
        collisionData.tolerance = 0.1d;

//        System.out.println("碰撞数据初始化完成!");

        // 3. 遍历所有盒子 ← 核心逻辑
//        System.out.println("遍历所有盒子开始!");
//        System.out.println("盒子数量为:" + boxes.size());
        for (int i = 0; i < boxes.size(); i++) {
            Box boxA = boxes.get(i);

            // 如果没有空间存更多碰撞，直接结束
            if (!collisionData.hasMoreContacts())
                break;
//            System.out.println("盒子:" + boxA);

            // 盒子 vs 地面
//            System.out.println("盒子vs地面开始!");
            CollisionDetector.boxAndHalfSpace(boxA, plane, collisionData);
//            System.out.println("盒子vs地面结束!");

            // 盒子 vs 其他盒子（不重复检测）
            for (int j = i + 1; j < boxes.size(); j++) {
                if (!collisionData.hasMoreContacts())
                    break;

                Box boxB = boxes.get(j);
                CollisionDetector.boxAndBox(boxA, boxB, collisionData);

                // 标记重叠
                if (IntersectionTests.boxAndBox(boxA, boxB)) {
                    boxA.isOverlapping = true;
                    boxB.isOverlapping = true;
                }
            }
        }
    }
}
