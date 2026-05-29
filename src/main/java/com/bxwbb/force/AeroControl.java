package com.bxwbb.force;

import com.bxwbb.phy.Maths;
import com.bxwbb.phy.RigidBody;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class AeroControl extends Aero {

    public Matrix3d maxTensor;
    public Matrix3d minTensor;
    public double controlSetting;

    public AeroControl(Matrix3d base, Matrix3d max, Matrix3d min, Vector3d position, Vector3d windspeed) {
        super(base, position, windspeed);
        this.maxTensor = max;
        this.minTensor = min;
        this.controlSetting = 0;
    }

    @Override
    public void updateForce(RigidBody body, double duration) {
        updateForceFromTensor(body, duration, getTensor());
    }

    public Matrix3d getTensor() {
        if (controlSetting <= -1.0d) return minTensor;
        if (controlSetting >= 1.0d) return maxTensor;
        if (controlSetting < 0) return Maths.linearInterpolate(minTensor, tensor, controlSetting + 1.0d);
        if (controlSetting > 0) return Maths.linearInterpolate(tensor, maxTensor, controlSetting);
        return tensor;
    }

    public void setControl(double value) {
        controlSetting = value;
    }
}
