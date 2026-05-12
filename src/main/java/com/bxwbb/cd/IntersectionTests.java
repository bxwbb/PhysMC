package com.bxwbb.cd;

import com.bxwbb.math.Vector3;

public class IntersectionTests {

    public static double transformToAxis(CollisionBox box, Vector3 axis) {
        return box.halfSize.x * Math.abs(axis.dot(box.getAxis(0))) + box.halfSize.y * Math.abs(axis.dot(box.getAxis(1))) + box.halfSize.z * Math.abs(axis.dot(box.getAxis(2)));
    }

    public static boolean boxAndHalfSpace(CollisionBox box, CollisionPlane plane) {
        double projectedRadius = transformToAxis(box, plane.direction);
        double boxDistance = plane.direction.dot(box.getAxis(3)) - projectedRadius;
        return boxDistance <= plane.offset;
    }

    public static boolean overlapOnAxis(CollisionBox one, CollisionBox two, Vector3 axis, Vector3 toCentre) {
        double oneProject = transformToAxis(one, axis);
        double twoProject = transformToAxis(two, axis);
        double distance = Math.abs(toCentre.dot(axis));
        return (distance < oneProject + twoProject);
    }

    public static boolean boxAndBox(CollisionBox one, CollisionBox two) {
        Vector3 toCentre = two.getAxis(3).subNew(one.getAxis(3));

        return (
                TEST_OVERLAP(one, two, one.getAxis(0), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(1), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(2), toCentre) &&
                        TEST_OVERLAP(one, two, two.getAxis(0), toCentre) &&
                        TEST_OVERLAP(one, two, two.getAxis(1), toCentre) &&
                        TEST_OVERLAP(one, two, two.getAxis(2), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(0).componentProductNew(two.getAxis(0)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(0).componentProductNew(two.getAxis(1)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(0).componentProductNew(two.getAxis(2)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(1).componentProductNew(two.getAxis(0)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(1).componentProductNew(two.getAxis(1)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(1).componentProductNew(two.getAxis(2)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(2).componentProductNew(two.getAxis(0)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(2).componentProductNew(two.getAxis(1)), toCentre) &&
                        TEST_OVERLAP(one, two, one.getAxis(2).componentProductNew(two.getAxis(2)), toCentre)
        );
    }

    public static boolean TEST_OVERLAP(CollisionBox one,
                                       CollisionBox two,
                                       Vector3 axis,
                                       Vector3 toCentre) {
        return overlapOnAxis(one, two, (axis), toCentre);
    }
}
