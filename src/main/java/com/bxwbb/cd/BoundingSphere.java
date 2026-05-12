package com.bxwbb.cd;

import com.bxwbb.math.Vector3;

public class BoundingSphere extends BoundingVolume {

    public Vector3 center;
    public double radius;

    public BoundingSphere(Vector3 center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    public BoundingSphere(BoundingSphere one, BoundingSphere two) {

    }

    @Override
    public boolean overlaps(BoundingVolume other) {
        BoundingSphere o = (BoundingSphere) other;
        double distanceSquared = (center.subNew(o.center)).squareMagnitude();
        return distanceSquared < (radius + o.radius) * (radius + o.radius);
    }

    @Override
    public double getSize() {
        return (1.333333d) * Math.PI * radius * radius * radius;
    }

    @Override
    public double getGrowth(BoundingVolume other) {
        BoundingSphere newSphere = new BoundingSphere(this, (BoundingSphere) other);
        return newSphere.radius * newSphere.radius - radius * radius;
    }
}
