package com.bxwbb.cd;

import com.bxwbb.phy.Maths;
import com.bxwbb.phy.RigidBody;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Arrays;

public class Contact {

    public static double velocityLimit = 0.25d;

    public Vector3d contactPoint = new Vector3d();
    public Vector3d contactNormal = new Vector3d();
    public double penetration = 0;
    public RigidBody[] body = new RigidBody[2];
    public double friction = 0;
    public double restitution = 0;
    public Matrix3d contactToWorld = new Matrix3d();
    public Vector3d contactVelocity = new Vector3d();
    public double desiredDeltaVelocity = 0;
    public Vector3d[] relativeContactPosition = new Vector3d[2];

    public void setBodyData(RigidBody one, RigidBody two, double friction, double restitution) {
        this.body[0] = one;
        this.body[1] = two;
        this.friction = friction;
        this.restitution = restitution;
    }

    public void calculateContactBasis() {
        Vector3d tangent0;
        Vector3d tangent1;

        if (Math.abs(contactNormal.x) > Math.abs(contactNormal.y)) {
            double s = 1.0d / Math.sqrt(contactNormal.z * contactNormal.z + contactNormal.x * contactNormal.x);
            tangent0 = new Vector3d(contactNormal.z * s, 0, -contactNormal.x * s);
        } else {
            double s = 1.0d / Math.sqrt(contactNormal.z * contactNormal.z + contactNormal.y * contactNormal.y);
            tangent0 = new Vector3d(0, -contactNormal.z * s, contactNormal.y * s);
        }

        tangent1 = new Vector3d(contactNormal).cross(tangent0).normalize();
        contactToWorld.set(
                contactNormal.x, contactNormal.y, contactNormal.z,
                tangent0.x, tangent0.y, tangent0.z,
                tangent1.x, tangent1.y, tangent1.z
        );
    }

    public Vector3d calculateFrictionlessImpulse(Matrix3d[] inverseInertiaTensor) {
        Vector3d deltaVelWorld = new Vector3d(relativeContactPosition[0]).cross(contactNormal);
        inverseInertiaTensor[0].transform(deltaVelWorld);
        deltaVelWorld.cross(relativeContactPosition[0]);

        double deltaVelocity = deltaVelWorld.dot(contactNormal) + body[0].getInverseMass();
        if (body[1] != null) {
            Vector3d deltaVelWorld1 = new Vector3d(relativeContactPosition[1]).cross(contactNormal);
            inverseInertiaTensor[1].transform(deltaVelWorld1);
            deltaVelWorld1.cross(relativeContactPosition[1]);
            deltaVelocity += deltaVelWorld1.dot(contactNormal) + body[1].getInverseMass();
        }

        return new Vector3d(desiredDeltaVelocity / deltaVelocity, 0, 0);
    }

    public void applyVelocityChange(Vector3d[] velocityChange, Vector3d[] rotationChange) {
        Matrix3d[] inverseInertiaTensor = {body[0].getInverseInertiaTensorWorld(), new Matrix3d()};
        if (body[1] != null) inverseInertiaTensor[1] = body[1].getInverseInertiaTensorWorld();

        Vector3d impulseContact = friction == 0.0d
                ? calculateFrictionlessImpulse(inverseInertiaTensor)
                : calculateFrictionImpulse(inverseInertiaTensor);
        Vector3d impulse = contactToWorld.transform(new Vector3d(impulseContact));

        Vector3d impulsiveTorque = new Vector3d(relativeContactPosition[0]).cross(impulse);
        rotationChange[0] = inverseInertiaTensor[0].transform(impulsiveTorque);
        velocityChange[0].set(impulse).mul(body[0].getInverseMass());
        body[0].addVelocity(velocityChange[0]);
        body[0].addRotation(rotationChange[0]);

        if (body[1] != null) {
            Vector3d impulsiveTorque1 = new Vector3d(impulse).cross(relativeContactPosition[1]);
            rotationChange[1] = inverseInertiaTensor[1].transform(impulsiveTorque1);
            velocityChange[1].set(impulse).mul(-body[1].getInverseMass());
            body[1].addVelocity(velocityChange[1]);
            body[1].addRotation(rotationChange[1]);
        }
    }

    public Vector3d calculateFrictionImpulse(Matrix3d[] inverseInertiaTensor) {
        double inverseMass = body[0].getInverseMass();
        Matrix3d impulseToTorque = Maths.skewSymmetric(relativeContactPosition[0]);
        Matrix3d deltaVelWorld = new Matrix3d(impulseToTorque)
                .mul(inverseInertiaTensor[0])
                .mul(impulseToTorque)
                .scale(-1);

        if (body[1] != null) {
            Matrix3d impulseToTorque2 = Maths.skewSymmetric(relativeContactPosition[1]);
            Matrix3d deltaVelWorld2 = new Matrix3d(impulseToTorque2)
                    .mul(inverseInertiaTensor[1])
                    .mul(impulseToTorque2)
                    .scale(-1);
            deltaVelWorld.add(deltaVelWorld2);
            inverseMass += body[1].getInverseMass();
        }

        Matrix3d deltaVelocity = new Matrix3d(contactToWorld).transpose()
                .mul(deltaVelWorld)
                .mul(contactToWorld);
        deltaVelocity.m00(deltaVelocity.m00() + inverseMass);
        deltaVelocity.m11(deltaVelocity.m11() + inverseMass);
        deltaVelocity.m22(deltaVelocity.m22() + inverseMass);

        Vector3d impulseContact = deltaVelocity.invert().transform(
                new Vector3d(desiredDeltaVelocity, -contactVelocity.y, -contactVelocity.z)
        );
        double planarImpulse = Math.sqrt(impulseContact.y * impulseContact.y + impulseContact.z * impulseContact.z);
        if (planarImpulse > impulseContact.x * friction) {
            impulseContact.y /= planarImpulse;
            impulseContact.z /= planarImpulse;
            impulseContact.x = deltaVelocity.m00() +
                    deltaVelocity.m01() * friction * impulseContact.y +
                    deltaVelocity.m02() * friction * impulseContact.z;
            impulseContact.x = desiredDeltaVelocity / impulseContact.x;
            impulseContact.y *= friction * impulseContact.x;
            impulseContact.z *= friction * impulseContact.x;
        }
        return impulseContact;
    }

    public void applyPositionChange(Vector3d[] linearChange, Vector3d[] angularChange, double penetration) {
        double angularLimit = 0.2d;
        double[] angularMove = new double[2];
        double[] linearMove = new double[2];
        double totalInertia = 0;
        double[] linearInertia = new double[2];
        double[] angularInertia = new double[2];

        for (int i = 0; i < 2; i++) {
            if (body[i] != null) {
                Matrix3d inverseInertiaTensor = body[i].getInverseInertiaTensorWorld();
                Vector3d angularInertiaWorld = new Vector3d(relativeContactPosition[i]).cross(contactNormal);
                inverseInertiaTensor.transform(angularInertiaWorld);
                angularInertiaWorld.cross(relativeContactPosition[i]);
                angularInertia[i] = angularInertiaWorld.dot(contactNormal);
                linearInertia[i] = body[i].getInverseMass();
                totalInertia += linearInertia[i] + angularInertia[i];
            }
        }

        if (totalInertia <= 0) return;

        for (int i = 0; i < 2; i++) {
            if (body[i] == null) continue;

            double sign = (i == 0) ? 1 : -1;
            angularMove[i] = sign * penetration * (angularInertia[i] / totalInertia);
            linearMove[i] = sign * penetration * (linearInertia[i] / totalInertia);

            Vector3d projection = new Vector3d(relativeContactPosition[i])
                    .fma(-relativeContactPosition[i].dot(contactNormal), contactNormal);
            double maxMagnitude = angularLimit * projection.length();
            if (angularMove[i] < -maxMagnitude) {
                double totalMove = angularMove[i] + linearMove[i];
                angularMove[i] = -maxMagnitude;
                linearMove[i] = totalMove - angularMove[i];
            } else if (angularMove[i] > maxMagnitude) {
                double totalMove = angularMove[i] + linearMove[i];
                angularMove[i] = maxMagnitude;
                linearMove[i] = totalMove - angularMove[i];
            }

            if (angularMove[i] == 0 || Math.abs(angularInertia[i]) < 0.0001d) {
                angularChange[i].zero();
            } else {
                Vector3d targetAngularDirection = new Vector3d(relativeContactPosition[i]).cross(contactNormal);
                angularChange[i] = body[i].getInverseInertiaTensorWorld()
                        .transform(targetAngularDirection)
                        .mul(angularMove[i] / angularInertia[i]);
            }

            linearChange[i] = new Vector3d(contactNormal).mul(linearMove[i]);
            body[i].position.add(linearChange[i]);
            body[i].orientation.integrate(1.0d, angularChange[i].x, angularChange[i].y, angularChange[i].z).normalize();
            if (!body[i].isAwake) body[i].calculateDerivedData();
        }
    }

    public void calculateInternals(double duration) {
        if (body[0] == null) swapBodies();
        assert (body[0] != null);
        calculateContactBasis();
        relativeContactPosition[0] = new Vector3d(contactPoint).sub(body[0].getPosition());
        if (body[1] != null) {
            relativeContactPosition[1] = new Vector3d(contactPoint).sub(body[1].getPosition());
        }
        contactVelocity = calculateLocalVelocity(0, duration);
        if (body[1] != null) contactVelocity.sub(calculateLocalVelocity(1, duration));
        calculateDesiredDeltaVelocity(duration);
    }

    public void swapBodies() {
        contactNormal.negate();
        RigidBody temp = body[0];
        body[0] = body[1];
        body[1] = temp;
    }

    public Vector3d calculateLocalVelocity(int bodyIndex, double duration) {
        RigidBody thisBody = body[bodyIndex];
        Vector3d velocity = new Vector3d(thisBody.rotation).cross(relativeContactPosition[bodyIndex]).add(thisBody.velocity);
        Vector3d contactVelocity = new Matrix3d(contactToWorld).transpose().transform(velocity);
        Vector3d accVelocity = new Vector3d(thisBody.lastFrameAcceleration).mul(duration);
        new Matrix3d(contactToWorld).transpose().transform(accVelocity);
        accVelocity.x = 0;
        contactVelocity.add(accVelocity);
        return contactVelocity;
    }

    public void calculateDesiredDeltaVelocity(double duration) {
        double velocityFromAcc = 0;
        if (body[0].isAwake) {
            velocityFromAcc += body[0].lastFrameAcceleration.dot(new Vector3d(contactNormal).mul(duration));
        }

        if (body[1] != null && body[1].isAwake) {
            velocityFromAcc -= body[1].lastFrameAcceleration.dot(new Vector3d(contactNormal).mul(duration));
        }

        double thisRestitution = restitution;
        if (Math.abs(contactVelocity.x) < velocityLimit) thisRestitution = 0.0d;
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
                ", contactVelocity=" + contactVelocity +
                ", desiredDeltaVelocity=" + desiredDeltaVelocity +
                '}';
    }
}
