package com.bxwbb.cd;

import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Quaternion;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

import java.util.Arrays;

public class Contact {

    public static double velocityLimit = 0.25d;

    public Vector3 contactPoint = new Vector3();
    public Vector3 contactNormal = new Vector3();
    public double penetration = 0;
    public RigidBody[] body = new RigidBody[2];
    public double friction = 0;
    public double restitution = 0;
    public Matrix3 contactToWorld = new Matrix3();
    public Vector3 contactVelocity = new Vector3();
    public double desiredDeltaVelocity = 0;
    public Vector3[] relativeContactPosition = new Vector3[2];

    public void setBodyData(RigidBody one, RigidBody two, double friction, double restitution) {
        this.body[0] = one;
        this.body[1] = two;
        this.friction = friction;
        this.restitution = restitution;
    }

    public void calculateContactBasis() {
        Vector3[] contactTangent = {new Vector3(), new Vector3()};
        if (Math.abs(contactNormal.x) > Math.abs(contactNormal.y)) {
            double s = 1.0d / Math.sqrt(contactNormal.z * contactNormal.z + contactNormal.x * contactNormal.x);
            contactTangent[0].x = contactNormal.z * s;
            contactTangent[0].y = 0;
            contactTangent[0].z = -contactNormal.x * s;
            contactTangent[1].x = contactNormal.y * contactTangent[0].x;
            contactTangent[1].y = contactNormal.z * contactTangent[0].x - contactNormal.x * contactTangent[0].z;
            contactTangent[1].z = -contactNormal.y * contactTangent[0].x;
        } else {
            double s = 1.0d / Math.sqrt(contactNormal.z * contactNormal.z + contactNormal.y * contactNormal.y);
            contactTangent[0].x = 0;
            contactTangent[0].y = -contactNormal.z * s;
            contactTangent[0].z = contactNormal.y * s;
            contactTangent[1].x = contactNormal.y * contactTangent[0].z - contactNormal.z * contactTangent[0].y;
            contactTangent[1].y = -contactNormal.x * contactTangent[0].z;
            contactTangent[1].z = contactNormal.x * contactTangent[0].y;
        }
        contactToWorld.setComponents(contactNormal, contactTangent[0], contactTangent[1]);
    }

    public Vector3 calculateFrictionlessImpulse(Matrix3[] inverseInertiaTensor) {
        Vector3 impulseContact = new Vector3();
        Vector3 deltaVelWorld = relativeContactPosition[0].componentProductNew(contactNormal);
        deltaVelWorld = inverseInertiaTensor[0].transform(deltaVelWorld);
        deltaVelWorld = deltaVelWorld.componentProductNew(relativeContactPosition[0]);
        double deltaVelocity = deltaVelWorld.dot(contactNormal);
        deltaVelocity += body[0].getInverseMass();
        if (body[1] != null) {
            Vector3 deltaVelWorld1 = relativeContactPosition[1].componentProductNew(contactNormal);
            deltaVelWorld1 = inverseInertiaTensor[1].transform(deltaVelWorld1);
            deltaVelWorld1 = deltaVelWorld1.componentProductNew(relativeContactPosition[1]);
            deltaVelocity += deltaVelWorld1.dot(contactNormal);
            deltaVelocity += body[1].getInverseMass();
        }
        impulseContact.x = desiredDeltaVelocity / deltaVelocity;
        impulseContact.y = 0;
        impulseContact.z = 0;
        return impulseContact;
    }

    public void applyVelocityChange(Vector3[] velocityChange, Vector3[] rotationChange) {
        Matrix3[] inverseInertiaTensor = {new Matrix3(), new Matrix3()};
        inverseInertiaTensor[0] = body[0].getInverseInertiaTensorWorld();
        if (body[1] != null) inverseInertiaTensor[1] = body[1].getInverseInertiaTensorWorld();
        Vector3 impulseContact;
        if (friction == 0.0d) {
            impulseContact = calculateFrictionlessImpulse(inverseInertiaTensor);
        } else {
            impulseContact = calculateFrictionImpulse(inverseInertiaTensor);
        }
        Vector3 impulse = contactToWorld.transform(impulseContact);
        Vector3 impulsiveTorque = relativeContactPosition[0].componentProductNew(impulse);
        rotationChange[0] = inverseInertiaTensor[0].transform(impulsiveTorque);
        velocityChange[0].clear();
        velocityChange[0].addScaledVector(impulse, body[0].getInverseMass());
        body[0].addVelocity(velocityChange[0]);
        body[0].addRotation(rotationChange[0]);
        if (body[1] != null) {
            Vector3 impulsiveTorque1 = impulse.componentProductNew(relativeContactPosition[1]);
            rotationChange[1] = inverseInertiaTensor[1].transform(impulsiveTorque1);
            velocityChange[1].clear();
            velocityChange[1].addScaledVector(impulse, -body[1].getInverseMass());
            body[1].addVelocity(velocityChange[1]);
            body[1].addRotation(rotationChange[1]);
        }
    }

    public Vector3 calculateFrictionImpulse(Matrix3[] inverseInertiaTensor) {
        Vector3 impulseContact;
        double inverseMass = body[0].getInverseMass();
        Matrix3 impulseToTorque = new Matrix3();
        impulseToTorque.setSkewSymmetric(relativeContactPosition[0]);
        Matrix3 deltaVelWorld = new Matrix3(impulseToTorque);
        deltaVelWorld.mul(inverseInertiaTensor[0]);
        deltaVelWorld.mul(impulseToTorque);
        deltaVelWorld.mul(-1);

        if (body[1] != null) {
            impulseToTorque.setSkewSymmetric(relativeContactPosition[1]);
            Matrix3 deltaVelWorld2 = new Matrix3(impulseToTorque);
            deltaVelWorld2.mul(inverseInertiaTensor[1]);
            deltaVelWorld2.mul(impulseToTorque);
            deltaVelWorld2.mul(-1);
            deltaVelWorld.add(deltaVelWorld2);
            inverseMass += body[1].getInverseMass();
        }
        Matrix3 deltaVelocity = contactToWorld.transpose();
        deltaVelocity.mul(deltaVelWorld);
        deltaVelocity.mul(contactToWorld);
        deltaVelocity.data[0] += inverseMass;
        deltaVelocity.data[4] += inverseMass;
        deltaVelocity.data[8] += inverseMass;
        Matrix3 impulseMatrix = deltaVelocity.inverse();
        Vector3 velKill = new Vector3(desiredDeltaVelocity, -contactVelocity.y, -contactVelocity.z);
        impulseContact = impulseMatrix.transform(velKill);
        double planarImpulse = Math.sqrt(impulseContact.y * impulseContact.y + impulseContact.z * impulseContact.z);
        if (planarImpulse > impulseContact.x * friction) {
            impulseContact.y /= planarImpulse;
            impulseContact.z /= planarImpulse;
            impulseContact.x = deltaVelocity.data[0] +
                    deltaVelocity.data[1] * friction * impulseContact.y +
                    deltaVelocity.data[2] * friction * impulseContact.z;
            impulseContact.x = desiredDeltaVelocity / impulseContact.x;
            impulseContact.y *= friction * impulseContact.x;
            impulseContact.z *= friction * impulseContact.x;
        }
        return impulseContact;
    }

    public void applyPositionChange(Vector3[] linearChange, Vector3[] angularChange, double penetration) {
        double angularLimit = 0.2d;
        double[] angularMove = new double[2];
        double[] linearMove = new double[2];

        double totalInertia = 0;
        double[] linearInertia = new double[2];
        double[] angularInertia = new double[2];
        for (int i = 0; i < 2; i++)
            if (body[i] != null) {
                Matrix3 inverseInertiaTensor;
                inverseInertiaTensor = body[i].getInverseInertiaTensorWorld();
                Vector3 angularInertiaWorld = relativeContactPosition[i].componentProductNew(contactNormal);
                angularInertiaWorld = inverseInertiaTensor.transform(angularInertiaWorld);
                angularInertiaWorld = angularInertiaWorld.componentProductNew(relativeContactPosition[i]);
                angularInertia[i] = angularInertiaWorld.dot(contactNormal);
                linearInertia[i] = body[i].getInverseMass();
                totalInertia += linearInertia[i] + angularInertia[i];
            }
        for (int i = 0; i < 2; i++) {
            if (body[i] != null) {
                double sign = (i == 0) ? 1 : -1;
                angularMove[i] =
                        sign * penetration * (angularInertia[i] / totalInertia);
                linearMove[i] =
                        sign * penetration * (linearInertia[i] / totalInertia);
                Vector3 projection = relativeContactPosition[i];
                projection.addScaledVector(contactNormal, -relativeContactPosition[i].scalarProduct(contactNormal));
                double maxMagnitude = angularLimit * projection.magnitude();
                if (angularMove[i] < -maxMagnitude) {
                    double totalMove = angularMove[i] + linearMove[i];
                    angularMove[i] = -maxMagnitude;
                    linearMove[i] = totalMove - angularMove[i];
                } else if (angularMove[i] > maxMagnitude) {
                    double totalMove = angularMove[i] + linearMove[i];
                    angularMove[i] = maxMagnitude;
                    linearMove[i] = totalMove - angularMove[i];
                }
                if (angularMove[i] == 0) {
                    angularChange[i].clear();
                } else {
                    Vector3 targetAngularDirection = relativeContactPosition[i].vectorProductNew(contactNormal);

                    Matrix3 inverseInertiaTensor;
                    inverseInertiaTensor = body[i].getInverseInertiaTensorWorld();
                    angularChange[i] = inverseInertiaTensor.transform(targetAngularDirection).mulNew(angularMove[i] / angularInertia[i]);
                }
                linearChange[i] = contactNormal.mulNew(linearMove[i]);
                Vector3 pos = body[i].getPosition();
                pos.addScaledVector(contactNormal, linearMove[i]);
                body[i].setPosition(pos);
                Quaternion q = body[i].orientation;
                q.addScaledVector(angularChange[i], (1.0d));
                body[i].orientation = q;
                if (!body[i].isAwake) body[i].calculateDerivedData();
            }
        }
    }

    public void calculateInternals(double duration) {
        if (body[0] == null) swapBodies();
        assert (body[0] != null);
        calculateContactBasis();
        System.out.println(Arrays.toString(body));
        relativeContactPosition[0] = contactPoint.subNew(body[0].getPosition());
        if (body[1] != null) {
            relativeContactPosition[1] = contactPoint.subNew(body[1].getPosition());
        }
        contactVelocity = calculateLocalVelocity(0, duration);
        if (body[1] != null) {
            contactVelocity.sub(calculateLocalVelocity(1, duration));
        }
        calculateDesiredDeltaVelocity(duration);
    }

    public void swapBodies() {
        contactNormal.mul(-1);
        RigidBody temp = body[0];
        body[0] = body[1];
        body[1] = temp;
    }

    public Vector3 calculateLocalVelocity(int bodyIndex, double duration) {
        RigidBody thisBody = body[bodyIndex];
        Vector3 velocity = thisBody.rotation.componentProductNew(relativeContactPosition[bodyIndex]);
        velocity.add(thisBody.velocity);
        Vector3 contactVelocity = contactToWorld.transformTranspose(velocity);
        Vector3 accVelocity = thisBody.lastFrameAcceleration.mulNew(duration);
        accVelocity = contactToWorld.transformTranspose(accVelocity);
        accVelocity.x = 0;
        contactVelocity.add(accVelocity);
        return contactVelocity;
    }

    public void calculateDesiredDeltaVelocity(double duration) {
        double velocityFromAcc = 0;
        if (body[0].isAwake) {
            velocityFromAcc += body[0].lastFrameAcceleration.dot(contactNormal.mulNew(duration));
        }

        if (body[1] != null && body[1].isAwake) {
            velocityFromAcc -= body[1].lastFrameAcceleration.dot(contactNormal.mulNew(duration));
        }
        double thisRestitution = restitution;
        if (Math.abs(contactVelocity.x) < velocityLimit) {
            thisRestitution = 0.0d;
        }
        desiredDeltaVelocity = -contactVelocity.x - thisRestitution * (contactVelocity.x - velocityFromAcc);
    }

    public void matchAwakeState() {
        if (body[1] == null) return;

        boolean body0awake = body[0].isAwake;
        boolean body1awake = body[1].isAwake;
        if (body0awake ^ body1awake) {
            if (body0awake) body[1].setAwake();
            else body[0].setAwake();
        }
    }

    @Override
    public String toString() {
        return "Contact{" +
                "contactPoint=" + contactPoint +
                ", contactNormal=" + contactNormal +
                ", penetration=" + penetration +
                ", body=" + Arrays.toString(body) +
                ", friction=" + friction +
                ", restitution=" + restitution +
                ", contactToWorld=" + contactToWorld +
                ", contactVelocity=" + contactVelocity +
                ", desiredDeltaVelocity=" + desiredDeltaVelocity +
                ", relativeContactPosition=" + Arrays.toString(relativeContactPosition) +
                '}';
    }
}
