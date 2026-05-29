package com.bxwbb.obj;

import com.bxwbb.cd.CollisionBox;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class Box extends CollisionBox {

    public boolean isOverlapping;

    public Box(PhysBlockDisplay physBlockDisplay) {
        super(new Vector3d(physBlockDisplay.getBlockDisplay().getTransformation().getScale()).mul(0.5d));
        this.body = physBlockDisplay;
        configureInertia();
    }

    private void configureInertia() {
        if (!body.hasFiniteMass()) {
            body.inverseInertiaTensor.zero();
            body.calculateDerivedData();
            return;
        }

        double mass = body.getMass();
        double width = halfSize.x * 2.0d;
        double height = halfSize.y * 2.0d;
        double depth = halfSize.z * 2.0d;
        double coefficient = mass / 12.0d;

        Matrix3d inertiaTensor = new Matrix3d().zero();
        inertiaTensor.m00(coefficient * (height * height + depth * depth));
        inertiaTensor.m11(coefficient * (width * width + depth * depth));
        inertiaTensor.m22(coefficient * (width * width + height * height));
        body.setInertiaTensor(inertiaTensor);
        body.calculateDerivedData();
    }
}
