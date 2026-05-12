package com.bxwbb.force;

import com.bxwbb.math.Matrix3;
import com.bxwbb.math.Vector3;
import com.bxwbb.phy.RigidBody;

public class AeroControl extends Aero {

    public Matrix3 maxTensor;
    public Matrix3 minTensor;
    public double controlSetting;

    public AeroControl(Matrix3 base, Matrix3 max, Matrix3 min, Vector3 position, Vector3 windspeed) {
        super(base, position, windspeed);
        this.maxTensor = max;
        this.minTensor = min;
        this.controlSetting = 0;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        Matrix3 tensor = getTensor();
        updateForceFromTensor(body, duration, tensor);
    }

    public Matrix3 getTensor() {
        if (controlSetting <= -1.0f) return minTensor;
        else if (controlSetting >= 1.0f) return maxTensor;
        else if (controlSetting < 0) {
            return Matrix3.linearInterpolate(minTensor, tensor, controlSetting + 1.0f);
        } else if (controlSetting > 0) {
            return Matrix3.linearInterpolate(tensor, maxTensor, controlSetting);
        } else return tensor;
    }

    public void setControl(double value) {
        controlSetting = value;
    }
}
