package com.bxwbb.cd;

public abstract class BoundingVolume {

    public abstract boolean overlaps(BoundingVolume other);

    public abstract double getSize();

    public abstract double getGrowth(BoundingVolume other);

}
