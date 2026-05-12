package com.bxwbb.cd;

import com.bxwbb.math.RefDouble;
import com.bxwbb.math.RefInt;
import com.bxwbb.math.Vector3;

public class CollisionDetector {

    private static final double[][] mults = {
            {1, 1, 1}, {-1, 1, 1}, {1, -1, 1}, {-1, -1, 1},
            {1, 1, -1}, {-1, 1, -1}, {1, -1, -1}, {-1, -1, -1}
    };

    private static boolean tryAxis(
            CollisionBox one,
            CollisionBox two,
            Vector3 axis,
            Vector3 toCentre,
            int index,
            RefDouble smallestPenetration,
            RefInt smallestCase
    ) {
        if (axis.squareMagnitude() < 0.0001) return true;
        axis.normalise();
        double penetration = penetrationOnAxis(one, two, axis, toCentre);
        if (penetration < 0) return false;
        if (penetration < smallestPenetration.value) {
            smallestPenetration.value = penetration;
            smallestCase.value = index;
        }
        return true;
    }

    private static double transformToAxis(
            CollisionBox box,
            Vector3 axis
    ) {
        return box.halfSize.x * Math.abs(axis.dot(box.getAxis(0))) +
                box.halfSize.y * Math.abs(axis.dot(box.getAxis(1))) +
                box.halfSize.z * Math.abs(axis.dot(box.getAxis(2)));
    }

    private static double penetrationOnAxis(
            CollisionBox one,
            CollisionBox two,
            Vector3 axis,
            Vector3 toCentre
    ) {
        double oneProject = transformToAxis(one, axis);
        double twoProject = transformToAxis(two, axis);
        double distance = Math.abs(toCentre.dot(axis));
        return oneProject + twoProject - distance;
    }

    private static boolean CHECK_OVERLAP(CollisionBox one, CollisionBox two, Vector3 axis, Vector3 toCentre, int index, RefDouble pen, RefInt best
    ) {
        return tryAxis(one, two, axis, toCentre, index, pen, best);
    }

    private static void fillPointFaceBoxBox(
            CollisionBox one,
            CollisionBox two,
            Vector3 toCentre,
            CollisionData data,
            int best,
            double pen
    ) {
        Contact contact = data.contacts[data.contactCount];
        Vector3 normal = one.getAxis(best);
        if (one.getAxis(best).dot(toCentre) > 0) {
            normal.mul(-1.0f);
        }
        Vector3 vertex = new Vector3();
        vertex.add(two.halfSize);
        if (two.getAxis(0).dot(normal) < 0) vertex.x = -vertex.x;
        if (two.getAxis(1).dot(normal) < 0) vertex.y = -vertex.y;
        if (two.getAxis(2).dot(normal) < 0) vertex.z = -vertex.z;
        contact.contactNormal = normal;
        contact.penetration = pen;
        contact.contactPoint = two.getTransform().transform(vertex);
        contact.setBodyData(one.body, two.body,
                data.friction, data.restitution);
    }

    private static Vector3 contactPoint(
            Vector3 pOne,
            Vector3 dOne,
            double oneSize,
            Vector3 pTwo,
            Vector3 dTwo,
            double twoSize,
            boolean useOne) {
        Vector3 toSt, cOne, cTwo;
        double dpStaOne, dpStaTwo, dpOneTwo, smOne, smTwo;
        double denom, mua, mub;
        smOne = dOne.squareMagnitude();
        smTwo = dTwo.squareMagnitude();
        dpOneTwo = dTwo.dot(dOne);
        toSt = pOne.subNew(pTwo);
        dpStaOne = dOne.dot(toSt);
        dpStaTwo = dTwo.dot(toSt);
        denom = smOne * smTwo - dpOneTwo * dpOneTwo;
        if (Math.abs(denom) < 0.0001f) {
            return useOne ? pOne : pTwo;
        }
        mua = (dpOneTwo * dpStaTwo - smTwo * dpStaOne) / denom;
        mub = (smOne * dpStaTwo - dpOneTwo * dpStaOne) / denom;
        if (mua > oneSize ||
                mua < -oneSize ||
                mub > twoSize ||
                mub < -twoSize) {
            return useOne ? pOne : pTwo;
        } else {
            cOne = pOne.addNew(dOne.mulNew(mua));
            cTwo = pTwo.addNew(dTwo.mulNew(mub));
            return cOne.mulNew(0.5).addNew(cTwo.mulNew(0.5));
        }
    }

    public int sphereAndSphere(CollisionSphere one, CollisionSphere two, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;
        Vector3 positionOne = one.getAxis(3);
        Vector3 positionTwo = two.getAxis(3);
        Vector3 midline = positionOne.subNew(positionTwo);
        double size = midline.magnitude();
        if (size <= 0.0f || size >= one.radius + two.radius) {
            return 0;
        }
        Vector3 normal = midline.mulNew((1.0d) / size);
        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = normal;
        contact.contactPoint = positionOne.addNew(midline.mulNew(0.5d));
        contact.penetration = (one.radius + two.radius - size);
        contact.setBodyData(one.body, two.body, data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public int sphereAndHalfSpace(CollisionSphere sphere, CollisionPlane plane, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;
        Vector3 position = sphere.getAxis(3);
        double ballDistance = plane.direction.dot(position) - sphere.radius - plane.offset;
        if (ballDistance >= 0) return 0;
        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = plane.direction;
        contact.penetration = -ballDistance;
        contact.contactPoint = position.subNew(plane.direction.mulNew(ballDistance + (sphere.radius)));
        contact.setBodyData(sphere.body, null, data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public int sphereAndTruePlane(CollisionSphere sphere, CollisionPlane plane, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;
        Vector3 position = sphere.getAxis(3);
        double centreDistance = plane.direction.dot(position) - plane.offset;
        if (centreDistance * centreDistance > sphere.radius * sphere.radius) {
            return 0;
        }
        Vector3 normal = plane.direction;
        double penetration = -centreDistance;
        if (centreDistance < 0) {
            normal.mul(-1);
            penetration = -penetration;
        }
        penetration += sphere.radius;
        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = normal;
        contact.penetration = penetration;
        contact.contactPoint = position.subNew(plane.direction.mulNew(centreDistance));
        contact.setBodyData(sphere.body, null, data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public static int boxAndHalfSpace(CollisionBox box, CollisionPlane plane, CollisionData data) {
        if (data.contactsLeft <= 0) return 0;
        if (!IntersectionTests.boxAndHalfSpace(box, plane)) {
            return 0;
        }

        Contact contact;
        int index = data.contactCount;
        int contactsUsed = 0;
        for (int i = 0; i < 8; i++) {
            contact = data.contacts[index];
            Vector3 vertexPos = new Vector3(mults[i][0], mults[i][1], mults[i][2]);
            vertexPos.componentProductUpdate(box.halfSize);
            vertexPos = box.transform.transform(vertexPos);
            double vertexDistance = vertexPos.dot(plane.direction);
            if (vertexDistance <= plane.offset) {
                contact.contactPoint = new Vector3();
                contact.contactPoint.add(plane.direction);
                contact.contactPoint.mul(vertexDistance - plane.offset);
                contact.contactPoint.add(vertexPos);
                contact.contactNormal = new Vector3();
                contact.contactNormal.add(plane.direction);
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

    public int boxAndSphere(CollisionBox box, CollisionSphere sphere, CollisionData data) {
        Vector3 centre = sphere.getAxis(3);
        Vector3 relCentre = box.transform.transformInverse(centre);
        if (Math.abs(relCentre.x) - sphere.radius > box.halfSize.x || Math.abs(relCentre.y) - sphere.radius > box.halfSize.y || Math.abs(relCentre.z) - sphere.radius > box.halfSize.z) {
            return 0;
        }
        Vector3 closestPt = new Vector3(0, 0, 0);
        double dist;
        dist = relCentre.x;
        if (dist > box.halfSize.x) dist = box.halfSize.x;
        if (dist < -box.halfSize.x) dist = -box.halfSize.x;
        closestPt.x = dist;
        dist = relCentre.y;
        if (dist > box.halfSize.y) dist = box.halfSize.y;
        if (dist < -box.halfSize.y) dist = -box.halfSize.y;
        closestPt.y = dist;
        dist = relCentre.z;
        if (dist > box.halfSize.z) dist = box.halfSize.z;
        if (dist < -box.halfSize.z) dist = -box.halfSize.z;
        closestPt.z = dist;
        dist = (closestPt.subNew(relCentre)).squareMagnitude();
        if (dist > sphere.radius * sphere.radius) return 0;
        Vector3 closestPtWorld = box.transform.transform(closestPt);
        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = (closestPtWorld.subNew(centre));
        contact.contactNormal.normalise();
        contact.contactPoint = closestPtWorld;
        contact.penetration = sphere.radius - Math.sqrt(dist);
        contact.setBodyData(box.body, sphere.body, data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public int boxAndPoint(CollisionBox box, Vector3 point, CollisionData data) {
        Vector3 relPt = box.transform.transformInverse(point);
        Vector3 normal;
        double min_depth = box.halfSize.x - Math.abs(relPt.x);
        if (min_depth < 0) return 0;
        normal = box.getAxis(0).mulNew((relPt.x < 0) ? -1 : 1);
        double depth = box.halfSize.y - Math.abs(relPt.y);
        if (depth < 0) return 0;
        else if (depth < min_depth) {
            min_depth = depth;
            normal = box.getAxis(1).mulNew((relPt.y < 0) ? -1 : 1);
        }
        depth = box.halfSize.z - Math.abs(relPt.z);
        if (depth < 0) return 0;
        else if (depth < min_depth) {
            min_depth = depth;
            normal = box.getAxis(2).mulNew((relPt.z < 0) ? -1 : 1);
        }
        Contact contact = data.contacts[data.contactCount];
        contact.contactNormal = normal;
        contact.contactPoint = point;
        contact.penetration = min_depth;
        contact.setBodyData(box.body, null,
                data.friction, data.restitution);
        data.addContacts(1);
        return 1;
    }

    public static int boxAndBox(CollisionBox one, CollisionBox two, CollisionData data) {
        Vector3 toCentre = two.getAxis(3).subNew(one.getAxis(3));
        RefDouble pen = new RefDouble(Double.MAX_VALUE);
        RefInt best = new RefInt(0xffffff);
        if (CHECK_OVERLAP(one, two, one.getAxis(0), toCentre, 0, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(1), toCentre, 1, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(2), toCentre, 2, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, two.getAxis(0), toCentre, 3, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, two.getAxis(1), toCentre, 4, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, two.getAxis(2), toCentre, 5, pen, best)) return 0;
        RefInt bestSingleAxis = new RefInt(best.value);
        if (CHECK_OVERLAP(one, two, one.getAxis(0).vectorProductNew(two.getAxis(0)), toCentre, 6, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(0).vectorProductNew(two.getAxis(1)), toCentre, 7, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(0).vectorProductNew(two.getAxis(2)), toCentre, 8, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(1).vectorProductNew(two.getAxis(0)), toCentre, 9, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(1).vectorProductNew(two.getAxis(1)), toCentre, 10, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(1).vectorProductNew(two.getAxis(2)), toCentre, 11, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(2).vectorProductNew(two.getAxis(0)), toCentre, 12, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(2).vectorProductNew(two.getAxis(1)), toCentre, 13, pen, best)) return 0;
        if (CHECK_OVERLAP(one, two, one.getAxis(2).vectorProductNew(two.getAxis(2)), toCentre, 14, pen, best)) return 0;
        assert (best.value != 0xffffff);
        if (best.value < 3) {
            fillPointFaceBoxBox(one, two, toCentre, data, best.value, pen.value);
            data.addContacts(1);
            return 1;
        } else if (best.value < 6) {
            fillPointFaceBoxBox(two, one, toCentre.mulNew(-1.0f), data, best.value - 3, pen.value);
            data.addContacts(1);
            return 1;
        } else {
            best.value -= 6;
            int oneAxisIndex = best.value / 3;
            int twoAxisIndex = best.value % 3;
            Vector3 oneAxis = one.getAxis(oneAxisIndex);
            Vector3 twoAxis = two.getAxis(twoAxisIndex);
            Vector3 axis = oneAxis.componentProductNew(twoAxis);
            axis.normalise();
            if (axis.dot(toCentre) > 0) axis.mul(-1.0f);
            Vector3 ptOnOneEdge = new Vector3(one.halfSize);
            Vector3 ptOnTwoEdge = new Vector3(two.halfSize);
            for (int i = 0; i < 3; i++) {
                if (i == oneAxisIndex) ptOnOneEdge.set(i, 0);
                else if (one.getAxis(i).dot(axis) > 0) ptOnOneEdge.set(i, -ptOnOneEdge.get(i));

                if (i == twoAxisIndex) ptOnTwoEdge.set(i, 0);
                else if (two.getAxis(i).dot(axis) < 0) ptOnTwoEdge.set(i, -ptOnTwoEdge.get(i));
            }
            ptOnOneEdge = one.transform.transform(ptOnOneEdge);
            ptOnTwoEdge = two.transform.transform(ptOnTwoEdge);
            Vector3 vertex = contactPoint(ptOnOneEdge, oneAxis, one.halfSize.get(oneAxisIndex), ptOnTwoEdge, twoAxis, two.halfSize.get(twoAxisIndex), bestSingleAxis.value > 2);
            Contact contact = data.contacts[data.contactCount];
            contact.penetration = pen.value;
            contact.contactNormal = axis;
            contact.contactPoint = vertex;
            contact.setBodyData(one.body, two.body,
                    data.friction, data.restitution);
            data.addContacts(1);
            return 1;
        }
    }
}
