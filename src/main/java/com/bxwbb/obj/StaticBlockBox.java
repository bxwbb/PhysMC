package com.bxwbb.obj;

import com.bxwbb.cd.CollisionBox;
import com.bxwbb.phy.RigidBody;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.BoundingBox;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;

public class StaticBlockBox extends CollisionBox {

    public StaticBlockBox(Location location) {
        super(new Vector3d(0.5d, 0.5d, 0.5d));
        this.body = new StaticBlockBody(new Vector3d(
                location.getBlockX() + 0.5d,
                location.getBlockY() + 0.5d,
                location.getBlockZ() + 0.5d
        ));
        this.body.calculateDerivedData();
        calculateInternals();
    }

    public StaticBlockBox(Location blockLocation, BoundingBox boundingBox) {
        super(new Vector3d(
                boundingBox.getWidthX() * 0.5d,
                boundingBox.getHeight() * 0.5d,
                boundingBox.getWidthZ() * 0.5d
        ));

        Vector3d center = new Vector3d(
                boundingBox.getCenterX(),
                boundingBox.getCenterY(),
                boundingBox.getCenterZ()
        );
        if (isBlockLocal(boundingBox)) {
            center.add(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
        }

        this.body = new StaticBlockBody(center);
        this.body.calculateDerivedData();
        calculateInternals();
    }

    private boolean isBlockLocal(BoundingBox boundingBox) {
        return boundingBox.getMinX() >= -0.0001d && boundingBox.getMaxX() <= 1.0001d &&
                boundingBox.getMinY() >= -0.0001d && boundingBox.getMaxY() <= 1.0001d &&
                boundingBox.getMinZ() >= -0.0001d && boundingBox.getMaxZ() <= 1.0001d;
    }

    private static class StaticBlockBody extends RigidBody {

        private StaticBlockBody(Vector3d position) {
            this.position = position;
            this.inverseMass = 0.0d;
            this.linearDamping = 1.0d;
            this.angularDamping = 1.0d;
        }

        @Override
        public List<Display> getAllDisplay() {
            return Collections.emptyList();
        }

        @Override
        public Vector3d getPosition() {
            return position;
        }

        @Override
        public void setPosition(Vector3d location) {
            position.set(location);
        }
    }
}
