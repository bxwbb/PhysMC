package com.bxwbb.phy;

import com.bxwbb.force.ForceRegistry;
import com.bxwbb.obj.Box;
import com.bxwbb.phys.BulletPhysicsEngine;
import com.bxwbb.phys.BulletPhysicsEngine.BulletDebugSnapshot;
import com.bxwbb.phys.BulletPhysicsEngine.ConstraintDetail;
import com.bxwbb.phys.BulletPhysicsEngine.ConstraintSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;

public class World {

    public final List<Box> boxes = new LinkedList<>();
    public final ForceRegistry forceRegistry = new ForceRegistry();

    private final BulletPhysicsEngine physicsEngine = new BulletPhysicsEngine();
    private static final World instance = new World();

    public static World getInstance() {
        return instance;
    }

    public void startFrame() {
    }

    public void runPhysics(double duration) {
        physicsEngine.step(boxes, duration);
    }

    public boolean applyImpulse(Display display, Vector impulse, Location hitLocation) {
        return physicsEngine.applyImpulse(display, impulse, hitLocation);
    }

    public boolean hold(Display display, Player player, double distance) {
        return physicsEngine.hold(display, player, distance);
    }

    public boolean holdAt(Display display, Location target) {
        return physicsEngine.holdAt(display, target);
    }

    public void release(Display display) {
        physicsEngine.release(display);
    }

    public void addSpring(Display firstDisplay, Display secondDisplay, double restLength, double stiffness, double damping) {
        physicsEngine.addSpring(firstDisplay, secondDisplay, restLength, stiffness, damping);
    }

    public boolean addDistanceConstraint(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint, double restLength) {
        return physicsEngine.addDistanceConstraint(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, restLength);
    }

    public boolean addTypedConstraint(String type, Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint,
                                      double lowerLinear, double upperLinear, double lowerAngular, double upperAngular) {
        return physicsEngine.addTypedConstraint(type, firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, lowerLinear, upperLinear, lowerAngular, upperAngular);
    }

    public boolean addTypedConstraint(String type, Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint,
                                      double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
        return physicsEngine.addTypedConstraint(type, firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, lowerLinear, upperLinear, lowerAngular, upperAngular, breakImpulse);
    }

    public boolean connect(Display firstDisplay, Location firstPoint, Display secondDisplay, Location secondPoint) {
        return physicsEngine.connect(firstDisplay, firstPoint, secondDisplay, secondPoint);
    }

    public boolean connect(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint) {
        return physicsEngine.connect(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint);
    }

    public boolean connect(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint, boolean preserveCenterDistance) {
        return physicsEngine.connect(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, preserveCenterDistance);
    }

    public List<ConstraintSnapshot> pointConstraints() {
        return physicsEngine.pointConstraints();
    }

    public List<ConstraintDetail> constraintDetails() {
        return physicsEngine.constraintDetails();
    }

    public BulletDebugSnapshot debugSnapshot(Display display) {
        return physicsEngine.debugSnapshot(display);
    }

    public boolean removeConstraint(int id) {
        return physicsEngine.removeConstraint(id);
    }

    public boolean syncBody(Box box) {
        return physicsEngine.syncBody(box);
    }
}
