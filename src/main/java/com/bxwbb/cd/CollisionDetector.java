package com.bxwbb.cd;

import org.joml.Vector3d;

public class CollisionDetector {

    private static final double[][] MULTS = {
            {1, 1, 1}, {-1, 1, 1}, {1, -1, 1}, {-1, -1, 1},
            {1, 1, -1}, {-1, 1, -1}, {1, -1, -1}, {-1, -1, -1}
    };

    private static boolean tryAxis(
            CollisionBox one,
            CollisionBox two,
            Vector3d axis,
            Vector3d toCentre,
            AxisTest best
    ) {
        if (axis.lengthSquared() < 0.0001d) return true;

        axis.normalize();
        double penetration = penetrationOnAxis(one, two, axis, toCentre);
        if (penetration < 0) return false;
        if (penetration < best.penetration) {
            best.penetration = penetration;
            best.normal.set(axis);
        }
        return true;
    }

    private static double transformToAxis(CollisionBox box, Vector3d axis) {
        return box.halfSize.x * Math.abs(axis.dot(box.getAxis(0))) +
                box.halfSize.y * Math.abs(axis.dot(box.getAxis(1))) +
                box.halfSize.z * Math.abs(axis.dot(box.getAxis(2)));
    }

    private static double penetrationOnAxis(
            CollisionBox one,
            CollisionBox two,
            Vector3d axis,
            Vector3d toCentre
    ) {
        double oneProject = transformToAxis(one, axis);
        double twoProject = transformToAxis(two, axis);
        double distance = Math.abs(toCentre.dot(axis));
        return oneProject + twoProject - distance;
    }

    public static int boxAndHalfSpace(CollisionBox box, CollisionPlane plane, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;
        if (!IntersectionTests.boxAndHalfSpace(box, plane)) return 0;

        int index = data.contactCount;
        int contactsUsed = 0;
        for (double[] mult : MULTS) {
            Contact contact = data.contacts[index];
            Vector3d vertexPos = new Vector3d(mult[0], mult[1], mult[2]).mul(box.halfSize);
            box.transform.transformPosition(vertexPos);
            double vertexDistance = vertexPos.dot(plane.direction);

            if (vertexDistance <= plane.offset) {
                contact.contactPoint = new Vector3d(vertexPos).fma(vertexDistance - plane.offset, plane.direction);
                contact.contactNormal = new Vector3d(plane.direction);
                contact.penetration = plane.offset - vertexDistance;
                contact.setBodyData(box.body, null, data.friction, data.restitution);
                index++;
                contactsUsed++;
                if (contactsUsed == data.contactsLeft) return contactsUsed;
            }
        }

        data.addContacts(contactsUsed);
        return contactsUsed;
    }

    public static int boxAndBox(CollisionBox one, CollisionBox two, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;

        Vector3d toCentre = two.getAxis(3).sub(one.getAxis(3), new Vector3d());
        AxisTest best = new AxisTest();

        for (int i = 0; i < 3; i++) {
            if (!tryAxis(one, two, one.getAxis(i), toCentre, best)) return 0;
        }
        for (int i = 0; i < 3; i++) {
            if (!tryAxis(one, two, two.getAxis(i), toCentre, best)) return 0;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (!tryAxis(one, two, one.getAxis(i).cross(two.getAxis(j), new Vector3d()), toCentre, best)) return 0;
            }
        }

        Vector3d normal = new Vector3d(best.normal);
        if (normal.dot(toCentre) > 0) normal.negate();

        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = normal;
        contact.penetration = best.penetration;
        contact.contactPoint = closestContactPoint(one, two);
        contact.setBodyData(one.body, two.body, data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public static int boxAndStaticAabb(CollisionBox box, CollisionBox staticBox, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;

        Vector3d center = staticBox.getAxis(3);
        Vector3d min = new Vector3d(center).sub(staticBox.halfSize);
        Vector3d max = new Vector3d(center).add(staticBox.halfSize);
        int contactsUsed = 0;

        for (double[] mult : MULTS) {
            if (contactsUsed >= data.contactsLeft) break;

            Vector3d vertex = new Vector3d(mult[0], mult[1], mult[2]).mul(box.halfSize);
            box.transform.transformPosition(vertex);
            if (!contains(min, max, vertex)) continue;

            FaceContact face = nearestFace(min, max, vertex);
            Contact contact = data.contacts[data.contactCount + contactsUsed];
            contact.contactNormal = face.normal;
            contact.penetration = face.penetration;
            contact.contactPoint = face.point;
            contact.setBodyData(box.body, null, data.friction, data.restitution);
            contactsUsed++;
        }

        if (contactsUsed == 0 && IntersectionTests.boxAndBox(box, staticBox)) {
            Contact contact = data.contacts[data.contactCount];
            Vector3d toBox = box.getAxis(3).sub(staticBox.getAxis(3), new Vector3d());
            if (Math.abs(toBox.x) >= Math.abs(toBox.y) && Math.abs(toBox.x) >= Math.abs(toBox.z)) {
                contact.contactNormal = new Vector3d(Math.signum(toBox.x), 0, 0);
            } else if (Math.abs(toBox.y) >= Math.abs(toBox.z)) {
                contact.contactNormal = new Vector3d(0, Math.signum(toBox.y), 0);
            } else {
                contact.contactNormal = new Vector3d(0, 0, Math.signum(toBox.z));
            }
            if (contact.contactNormal.lengthSquared() < 0.0001d) contact.contactNormal.set(0, 1, 0);
            contact.penetration = 0.01d;
            contact.contactPoint = closestContactPoint(box, staticBox);
            contact.setBodyData(box.body, null, data.friction, data.restitution);
            contactsUsed = 1;
        }

        data.addContacts(contactsUsed);
        return contactsUsed;
    }

    private static Vector3d closestContactPoint(CollisionBox one, CollisionBox two) {
        Vector3d centerOne = one.getAxis(3);
        Vector3d centerTwo = two.getAxis(3);
        return centerOne.add(centerTwo, new Vector3d()).mul(0.5d);
    }

    private static boolean contains(Vector3d min, Vector3d max, Vector3d point) {
        return point.x >= min.x && point.x <= max.x &&
                point.y >= min.y && point.y <= max.y &&
                point.z >= min.z && point.z <= max.z;
    }

    private static FaceContact nearestFace(Vector3d min, Vector3d max, Vector3d point) {
        FaceContact face = new FaceContact(new Vector3d(-1, 0, 0), point.x - min.x, new Vector3d(min.x, point.y, point.z));
        face = minFace(face, new FaceContact(new Vector3d(1, 0, 0), max.x - point.x, new Vector3d(max.x, point.y, point.z)));
        face = minFace(face, new FaceContact(new Vector3d(0, -1, 0), point.y - min.y, new Vector3d(point.x, min.y, point.z)));
        face = minFace(face, new FaceContact(new Vector3d(0, 1, 0), max.y - point.y, new Vector3d(point.x, max.y, point.z)));
        face = minFace(face, new FaceContact(new Vector3d(0, 0, -1), point.z - min.z, new Vector3d(point.x, point.y, min.z)));
        return minFace(face, new FaceContact(new Vector3d(0, 0, 1), max.z - point.z, new Vector3d(point.x, point.y, max.z)));
    }

    private static FaceContact minFace(FaceContact current, FaceContact candidate) {
        return candidate.penetration < current.penetration ? candidate : current;
    }

    private static class AxisTest {
        private double penetration = Double.MAX_VALUE;
        private Vector3d normal = new Vector3d(0, 1, 0);
    }

    private static class FaceContact {
        private final Vector3d normal;
        private final double penetration;
        private final Vector3d point;

        private FaceContact(Vector3d normal, double penetration, Vector3d point) {
            this.normal = normal;
            this.penetration = penetration;
            this.point = point;
        }
    }
}
