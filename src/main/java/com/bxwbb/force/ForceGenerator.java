package com.bxwbb.force;

import com.bxwbb.phy.RigidBody;

public interface ForceGenerator {

    void updateForce(RigidBody body, double duration);

}
