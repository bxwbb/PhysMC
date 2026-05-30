package com.bxwbb.obj;

import com.bxwbb.physmc.api.PhysBody;
import com.bxwbb.cd.CollisionBox;
import com.bxwbb.phy.RigidBody;
import com.bxwbb.phy.World;
import com.bxwbb.util.ObjectUtil;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Box extends CollisionBox implements PhysBody {

    public boolean isOverlapping;

    public Box(PhysBlockDisplay physBlockDisplay) {
        this(physBlockDisplay, new Vector3d(physBlockDisplay.getBlockDisplay().getTransformation().getScale()).mul(0.5d));
    }

    public Box(PhysBlockDisplay physBlockDisplay, Vector3d halfSize) {
        this((RigidBody) physBlockDisplay, halfSize);
    }

    public Box(RigidBody body, Vector3d halfSize) {
        super(new Vector3d(halfSize));
        this.body = body;
        configureInertia();
    }

    private void configureInertia() {
        if (!body.hasFiniteMass()) {
            body.inverseInertiaTensor.zero();
            body.calculateDerivedData();
            return;
        }

        double mass = body.getMass();
        if (body instanceof PhysSphereDisplay) {
            double radius = ((PhysSphereDisplay) body).radius();
            double inertia = 0.4d * mass * radius * radius;
            Matrix3d sphereTensor = new Matrix3d().zero();
            sphereTensor.m00(inertia);
            sphereTensor.m11(inertia);
            sphereTensor.m22(inertia);
            body.setInertiaTensor(sphereTensor);
            body.calculateDerivedData();
            return;
        }

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

    @Override
    public Optional<String> getGroupId() {
        for (Map.Entry<String, List<Display>> entry : ObjectUtil.displays.entrySet()) {
            for (Display display : body.getAllDisplay()) {
                if (entry.getValue().contains(display)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getName() {
        Optional<String> groupId = getGroupId();
        if (groupId.isEmpty()) {
            return Optional.empty();
        }
        Map<String, List<Display>> names = ObjectUtil.groups.get(groupId.get());
        if (names == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<Display>> entry : names.entrySet()) {
            for (Display display : body.getAllDisplay()) {
                if (entry.getValue().contains(display)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Display> getDisplays() {
        return new ArrayList<>(body.getAllDisplay());
    }

    @Override
    public Vector3d getPosition() {
        return new Vector3d(body.position);
    }

    @Override
    public void setPosition(Vector3d position) {
        requireVector(position, "position");
        body.position.set(position);
        body.calculateDerivedData();
    }

    @Override
    public Optional<Location> getLocation() {
        for (Display display : body.getAllDisplay()) {
            if (display != null && display.isValid()) {
                return Optional.of(new Location(display.getWorld(), body.position.x, body.position.y, body.position.z));
            }
        }
        return Optional.empty();
    }

    @Override
    public Vector3d getVelocity() {
        return new Vector3d(body.velocity);
    }

    @Override
    public void setVelocity(Vector3d velocity) {
        requireVector(velocity, "velocity");
        body.velocity.set(velocity);
        body.setAwake(true);
    }

    @Override
    public Vector3d getAngularVelocity() {
        return new Vector3d(body.rotation);
    }

    @Override
    public void setAngularVelocity(Vector3d angularVelocity) {
        requireVector(angularVelocity, "angularVelocity");
        body.rotation.set(angularVelocity);
        body.setAwake(true);
    }

    @Override
    public Quaterniond getOrientation() {
        return new Quaterniond(body.orientation);
    }

    @Override
    public void setOrientation(Quaterniond orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("orientation 不能为空");
        }
        body.orientation.set(orientation).normalize();
        body.calculateDerivedData();
    }

    @Override
    public Vector3d getSize() {
        return new Vector3d(halfSize).mul(2.0d);
    }

    @Override
    public double getMass() {
        return body.hasFiniteMass() ? body.getMass() : 0.0d;
    }

    @Override
    public void setMass(double mass) {
        if (mass < 0.0d) {
            throw new IllegalArgumentException("mass 不能小于 0");
        }
        body.inverseMass = mass == 0.0d ? 0.0d : 1.0d / mass;
        configureInertia();
        body.setAwake(true);
    }

    @Override
    public void applyImpulse(Vector3d impulse) {
        requireVector(impulse, "impulse");
        if (!body.hasFiniteMass()) {
            return;
        }
        body.velocity.fma(body.getInverseMass(), impulse);
        body.setAwake(true);
    }

    @Override
    public void addForce(Vector3d force) {
        requireVector(force, "force");
        body.addForce(force);
        body.setAwake(true);
    }

    @Override
    public void setAwake(boolean awake) {
        body.setAwake(awake);
    }

    @Override
    public boolean isAwake() {
        return body.isAwake;
    }

    @Override
    public void sync() {
        World.getInstance().syncBody(this);
        body.tick();
    }

    private void requireVector(Vector3d vector, String name) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " 必须是有限向量");
        }
    }
}
