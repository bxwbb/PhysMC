package com.bxwbb.obj;

import com.bxwbb.cd.CollisionBox;
import com.bxwbb.math.Matrix4;
import com.bxwbb.math.Vector3;

public class Box extends CollisionBox {

    public boolean isOverlapping;

    public Box(PhysBlockDisplay physBlockDisplay) {
        super(new Vector3(physBlockDisplay.getBlockDisplay().getTransformation().getScale().x * 0.5, physBlockDisplay.getBlockDisplay().getTransformation().getScale().y * 0.5, physBlockDisplay.getBlockDisplay().getTransformation().getScale().z * 0.5));
        this.body = physBlockDisplay;
        this.offset = new Matrix4();
    }

}
