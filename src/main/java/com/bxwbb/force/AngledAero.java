package com.bxwbb.force;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class AngledAero extends Aero {

    public Quaterniond orientation;

    public AngledAero(Matrix3d tensor, Vector3d position, Vector3d windspeed) {
        super(tensor, position, windspeed);
        this.orientation = new Quaterniond();
    }

    public void setOrientation(Quaterniond orientation) {
        this.orientation = orientation;
    }
}
