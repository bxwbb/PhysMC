package com.bxwbb.cd;

import org.joml.Vector3d;

public class IntersectionTests {

    public static double transformToAxis(CollisionBox box, Vector3d axis) {
        return box.halfSize.x * Math.abs(axis.dot(box.getAxis(0))) +
                box.halfSize.y * Math.abs(axis.dot(box.getAxis(1))) +
                box.halfSize.z * Math.abs(axis.dot(box.getAxis(2)));
    }

    public static boolean boxAndHalfSpace(CollisionBox box, CollisionPlane plane) {
        double projectedRadius = transformToAxis(box, plane.direction);
        double boxDistance = plane.direction.dot(box.getAxis(3)) - projectedRadius;
        return boxDistance <= plane.offset;
    }

    public static boolean overlapOnAxis(CollisionBox one, CollisionBox two, Vector3d axis, Vector3d toCentre) {
        if (axis.lengthSquared() < 0.0001d) return true;
        axis.normalize();
        double oneProject = transformToAxis(one, axis);
        double twoProject = transformToAxis(two, axis);
        double distance = Math.abs(toCentre.dot(axis));
        return distance < oneProject + twoProject;
    }

    public static boolean boxAndBox(CollisionBox one, CollisionBox two) {
        Vector3d toCentre = two.getAxis(3).sub(one.getAxis(3), new Vector3d());

        for (int i = 0; i < 3; i++) {
            if (!overlapOnAxis(one, two, one.getAxis(i), toCentre)) return false;
            if (!overlapOnAxis(one, two, two.getAxis(i), toCentre)) return false;
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (!overlapOnAxis(one, two, one.getAxis(i).cross(two.getAxis(j), new Vector3d()), toCentre)) {
                    return false;
                }
            }
        }

        return true;
    }
}
