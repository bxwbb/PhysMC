package com.bxwbb.force;

import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Quaternion;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class AngledAero extends Aero {

    public Quaternion orientation;

    public AngledAero(Matrix3 tensor, Vector3 position, Vector3 windspeed) {
        super(tensor, position, windspeed);
        this.orientation = new Quaternion(0, 0, 0, 1);
    }

    public void setOrientation(Quaternion orientation) {
        this.orientation = orientation;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        super.updateForce(body, duration);
    }
}
