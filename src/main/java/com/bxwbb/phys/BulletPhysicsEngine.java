package com.bxwbb.phys;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CompoundShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bxwbb.physmc.api.event.PhysBlockContactEvent;
import com.bxwbb.physmc.api.event.PhysEntityContactEvent;
import com.bxwbb.physmc.api.event.PhysFluidContactEvent;
import com.bxwbb.physmc.api.event.PhysRigidBodyContactEvent;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.ConeTwistConstraint;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.HingeConstraint;
import com.bulletphysics.dynamics.constraintsolver.Point2PointConstraint;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SliderConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.obj.Box;
import com.bxwbb.obj.CompoundPhysBlockDisplay;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.obj.PhysSphereDisplay;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.vecmath.Quat4f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;

public class BulletPhysicsEngine {

    private static final int POSITION_CONSTRAINT_ITERATIONS = 24;
    private static final float POINT_CONSTRAINT_TAU = 1.0f;
    private static final float POINT_CONSTRAINT_DAMPING = 1.0f;
    private static final float POINT_CONSTRAINT_IMPULSE_CLAMP = 0.0f;
    private static final float MAX_PROJECTION_CORRECTION = 0.35f;
    private static final float MAX_LINEAR_SPEED = 80.0f;
    private static final float MAX_ANGULAR_SPEED = 80.0f;
    private static final float MAX_DISPLAY_COORDINATE = 2.9e7f;

    private final DiscreteDynamicsWorld dynamicsWorld;
    private final CollisionDispatcher dispatcher;
    private final Map<Box, com.bulletphysics.dynamics.RigidBody> dynamicBodies = new HashMap<>();
    private final Map<Entity, com.bulletphysics.dynamics.RigidBody> entityBodies = new HashMap<>();
    private final Map<String, com.bulletphysics.dynamics.RigidBody> staticBodies = new HashMap<>();
    private final Map<String, FluidSample> fluidSampleCache = new HashMap<>();
    private final Map<Box, BodyPartitionState> partitionStates = new HashMap<>();
    private final Map<Box, SuspendedMotion> suspendedMotions = new HashMap<>();
    private final List<ConstraintBinding> constraints = new ArrayList<>();
    private final List<DistanceConstraintBinding> distanceConstraints = new ArrayList<>();
    private final List<PendingConnectionRequest> pendingConnections = new ArrayList<>();
    private final List<PendingTypedConstraintRequest> pendingTypedConstraints = new ArrayList<>();
    private final List<SpringBinding> springs = new ArrayList<>();
    private int nextConstraintId = 1;
    private Set<Entity> cachedRequiredEntities = new HashSet<>();
    private String dynamicSettingsSignature = "";
    private String staticSettingsSignature = "";
    private int terrainSyncTicker = 0;
    private int entitySyncTicker = 0;
    private long simulationTick = 0L;

    public BulletPhysicsEngine() {
        DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
        dispatcher = new CollisionDispatcher(collisionConfiguration);
        DbvtBroadphase broadphase = new DbvtBroadphase();
        SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();
        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        applyGravity();
    }

    public void step(List<Box> boxes, double duration) {
        simulationTick++;
        applyGravity();
        fluidSampleCache.clear();
        syncDynamicBodies(boxes);
        Set<Box> activeBoxes = partitionActiveBoxes(boxes);
        expandActiveBoxesThroughConstraints(activeBoxes);
        syncEntityBodies(activeBoxes);
        processPendingConnections();
        processPendingTypedConstraints();
        wakeLinkedBodies(activeBoxes);
        applyDynamicSettingsIfNeeded();
        if (shouldSyncTerrain()) {
            syncStaticTerrain(activeBoxes);
        }
        applyFluidForces(activeBoxes);
        applyMaterialContactEffects(activeBoxes);
        applySpringForces();
        dynamicsWorld.stepSimulation(
                (float) duration,
                getInt(PhysConfig.PHYSICS_MAX_SUB_STEPS),
                (float) getDouble(PhysConfig.PHYSICS_FIXED_STEP)
        );
        fireContactEvents();
        breakOverloadedFixedConstraints();
        projectPointConstraints();
        syncDisplays(activeBoxes);
        syncEntities();
    }

    public boolean applyImpulse(Display display, org.bukkit.util.Vector impulse, Location hitLocation) {
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            Box box = entry.getKey();
            if (!box.body.getAllDisplay().contains(display)) continue;

            com.bulletphysics.dynamics.RigidBody body = entry.getValue();
            if (body.getInvMass() <= 0.0f) return false;

            suspendedMotions.remove(box);
            wakeBody(body);
            Vector3f impulseVector = new Vector3f((float) impulse.getX(), (float) impulse.getY(), (float) impulse.getZ());
            Vector3f relativePosition = new Vector3f(
                    (float) (hitLocation.getX() - box.body.position.x),
                    (float) (hitLocation.getY() - box.body.position.y),
                    (float) (hitLocation.getZ() - box.body.position.z)
            );
            body.applyImpulse(impulseVector, relativePosition);
            wakeBody(body);
            return true;
        }
        return false;
    }

    public boolean syncBody(Box box) {
        com.bulletphysics.dynamics.RigidBody body = dynamicBodies.get(box);
        if (body == null) return false;
        suspendedMotions.remove(box);

        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set(toBullet(box.body.position));
        transform.setRotation(toBullet(box.body.orientation));
        body.setCenterOfMassTransform(transform);
        body.setWorldTransform(transform);
        body.getMotionState().setWorldTransform(transform);
        body.setInterpolationWorldTransform(transform);
        body.setLinearVelocity(toBullet(box.body.velocity));
        body.setAngularVelocity(toBullet(box.body.rotation));
        body.clearForces();
        if (box.body.hasFiniteMass()) {
            Vector3f inertia = new Vector3f();
            body.getCollisionShape().calculateLocalInertia((float) box.body.getMass(), inertia);
            body.setMassProps((float) box.body.getMass(), inertia);
        } else {
            body.setMassProps(0.0f, new Vector3f());
        }
        body.updateInertiaTensor();
        wakeBody(body);
        dynamicsWorld.updateSingleAabb(body);
        return true;
    }

    public boolean hold(Display display, Player player, double distance) {
        Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(distance));
        return holdAt(display, target);
    }

    public boolean holdAt(Display display, Location target) {
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            Box box = entry.getKey();
            if (!box.body.getAllDisplay().contains(display)) continue;

            com.bulletphysics.dynamics.RigidBody body = entry.getValue();
            if (body.getInvMass() <= 0.0f) return false;

            suspendedMotions.remove(box);
            Transform transform = new Transform();
            body.getMotionState().getWorldTransform(transform);
            transform.origin.set((float) target.getX(), (float) target.getY(), (float) target.getZ());

            body.setCenterOfMassTransform(transform);
            body.setWorldTransform(transform);
            body.getMotionState().setWorldTransform(transform);
            body.setInterpolationWorldTransform(transform);
            body.setLinearVelocity(new Vector3f());
            body.setAngularVelocity(new Vector3f());
            wakeBody(body);

            box.body.position.set(target.getX(), target.getY(), target.getZ());
            box.body.velocity.zero();
            box.body.rotation.zero();
            return true;
        }
        return false;
    }

    public void release(Display display) {
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            if (!entry.getKey().body.getAllDisplay().contains(display)) continue;
            wakeBody(entry.getValue());
            return;
        }
    }

    public void addSpring(Display firstDisplay, Display secondDisplay, double restLength, double stiffness, double damping) {
        springs.add(new SpringBinding(nextConstraintId++, firstDisplay, secondDisplay, (float) restLength, (float) stiffness, (float) damping));
    }

    public boolean addDistanceConstraint(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint, double restLength) {
        BodyAnchor first = anchorFor(firstDisplay, firstEntity, firstPoint);
        BodyAnchor second = anchorFor(secondDisplay, secondEntity, secondPoint);
        if (first == null && second == null) return false;
        distanceConstraints.add(new DistanceConstraintBinding(
                nextConstraintId++,
                first == null ? null : first.body,
                second == null ? null : second.body,
                first == null ? toBullet(firstPoint) : first.localPivot,
                second == null ? toBullet(secondPoint) : second.localPivot,
                first == null,
                second == null,
                (float) restLength,
                firstPoint.getWorld()
        ));
        wakeIfDynamic(first == null ? null : first.body);
        wakeIfDynamic(second == null ? null : second.body);
        return true;
    }

    public boolean addTypedConstraint(String type, Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint,
                                      double lowerLinear, double upperLinear, double lowerAngular, double upperAngular) {
        return addTypedConstraint(type, firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint,
                lowerLinear, upperLinear, lowerAngular, upperAngular, defaultBreakImpulse(type));
    }

    public boolean addTypedConstraint(String type, Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint,
                                      double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
        if (createTypedConstraint(type, firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, lowerLinear, upperLinear, lowerAngular, upperAngular, breakImpulse)) {
            return true;
        }
        if (!hasConnectionTarget(firstDisplay, firstEntity, secondDisplay, secondEntity)) {
            return false;
        }
        pendingTypedConstraints.add(new PendingTypedConstraintRequest(type, firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint,
                lowerLinear, upperLinear, lowerAngular, upperAngular, breakImpulse));
        return true;
    }

    private boolean createTypedConstraint(String type, Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint,
                                      double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
        BodyAnchor first = anchorFor(firstDisplay, firstEntity, firstPoint);
        BodyAnchor second = anchorFor(secondDisplay, secondEntity, secondPoint);
        if (first == null || second == null) return false;

        Transform frameA = constraintFrame(first.body, firstPoint, secondPoint);
        Transform frameB = constraintFrame(second.body, secondPoint, firstPoint);
        TypedConstraint constraint;
        String normalized = type.toLowerCase();
        switch (normalized) {
            case "hinge":
                constraint = new HingeConstraint(first.body, second.body, frameA, frameB);
                break;
            case "slider":
                SliderConstraint slider = new SliderConstraint(first.body, second.body, frameA, frameB, true);
                slider.setLowerLinLimit((float) lowerLinear);
                slider.setUpperLinLimit((float) upperLinear);
                slider.setLowerAngLimit((float) lowerAngular);
                slider.setUpperAngLimit((float) upperAngular);
                constraint = slider;
                break;
            case "fixed":
                Generic6DofConstraint fixed = new Generic6DofConstraint(first.body, second.body, frameA, frameB, true);
                fixed.setLinearLowerLimit(new Vector3f());
                fixed.setLinearUpperLimit(new Vector3f());
                fixed.setAngularLowerLimit(new Vector3f());
                fixed.setAngularUpperLimit(new Vector3f());
                constraint = fixed;
                break;
            case "6dof":
                Generic6DofConstraint dof = new Generic6DofConstraint(first.body, second.body, frameA, frameB, true);
                dof.setLinearLowerLimit(new Vector3f((float) lowerLinear, (float) lowerLinear, (float) lowerLinear));
                dof.setLinearUpperLimit(new Vector3f((float) upperLinear, (float) upperLinear, (float) upperLinear));
                dof.setAngularLowerLimit(new Vector3f((float) lowerAngular, (float) lowerAngular, (float) lowerAngular));
                dof.setAngularUpperLimit(new Vector3f((float) upperAngular, (float) upperAngular, (float) upperAngular));
                constraint = dof;
                break;
            case "cone":
                ConeTwistConstraint cone = new ConeTwistConstraint(first.body, second.body, frameA, frameB);
                cone.setLimit((float) lowerAngular, (float) upperAngular, (float) upperAngular);
                constraint = cone;
                break;
            default:
                return false;
        }
        dynamicsWorld.addConstraint(constraint, true);
        constraints.add(new ConstraintBinding(
                nextConstraintId++,
                normalized,
                constraint,
                first.body,
                second.body,
                first.localPivot,
                second.localPivot,
                false,
                false,
                false,
                0.0f,
                firstPoint.getWorld(),
                (float) lowerLinear,
                (float) upperLinear,
                (float) lowerAngular,
                (float) upperAngular,
                (float) breakImpulse
        ));
        wakeBody(first.body);
        wakeBody(second.body);
        return true;
    }

    private double defaultBreakImpulse(String type) {
        return "fixed".equalsIgnoreCase(type) ? getDouble(PhysConfig.PHYSICS_FIXED_CONSTRAINT_BREAK_IMPULSE) : 0.0d;
    }

    public boolean connect(Display firstDisplay, Location firstPoint, Display secondDisplay, Location secondPoint) {
        return connect(firstDisplay, null, firstPoint, secondDisplay, null, secondPoint);
    }

    public boolean connect(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint) {
        return connect(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, false);
    }

    public boolean connect(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint, boolean preserveCenterDistance) {
        if (createPointConstraint(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, preserveCenterDistance)) {
            return true;
        }
        if (!hasConnectionTarget(firstDisplay, firstEntity, secondDisplay, secondEntity)) {
            return false;
        }
        pendingConnections.add(new PendingConnectionRequest(firstDisplay, firstEntity, firstPoint, secondDisplay, secondEntity, secondPoint, preserveCenterDistance));
        return true;
    }

    private boolean createPointConstraint(Display firstDisplay, Entity firstEntity, Location firstPoint, Display secondDisplay, Entity secondEntity, Location secondPoint, boolean preserveCenterDistance) {
        BodyAnchor first = anchorFor(firstDisplay, firstEntity, firstPoint);
        BodyAnchor second = anchorFor(secondDisplay, secondEntity, secondPoint);
        if (first == null && second == null) return false;

        Point2PointConstraint constraint;
        if (first != null && second != null) {
            constraint = new Point2PointConstraint(first.body, second.body, first.localPivot, second.localPivot);
            wakeBody(first.body);
            wakeBody(second.body);
        } else if (first != null) {
            constraint = new Point2PointConstraint(first.body, first.localPivot);
            constraint.setPivotB(toBullet(secondPoint));
            wakeBody(first.body);
        } else {
            constraint = new Point2PointConstraint(second.body, second.localPivot);
            constraint.setPivotB(toBullet(firstPoint));
            wakeBody(second.body);
        }
        configurePointConstraint(constraint);

        dynamicsWorld.addConstraint(constraint, true);
        constraints.add(new ConstraintBinding(
                nextConstraintId++,
                "point",
                constraint,
                first == null ? null : first.body,
                second == null ? null : second.body,
                first == null ? toBullet(firstPoint) : first.localPivot,
                second == null ? toBullet(secondPoint) : second.localPivot,
                first == null,
                second == null,
                preserveCenterDistance && first != null && second != null,
                centerDistance(first, second),
                firstPoint.getWorld(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f
        ));
        return true;
    }

    private float centerDistance(BodyAnchor first, BodyAnchor second) {
        if (first == null || second == null) return 0.0f;
        Transform firstTransform = new Transform();
        Transform secondTransform = new Transform();
        first.body.getCenterOfMassTransform(firstTransform);
        second.body.getCenterOfMassTransform(secondTransform);
        Vector3f delta = new Vector3f(secondTransform.origin);
        delta.sub(firstTransform.origin);
        return delta.length();
    }

    private void configurePointConstraint(Point2PointConstraint constraint) {
        constraint.setting.tau = POINT_CONSTRAINT_TAU;
        constraint.setting.damping = POINT_CONSTRAINT_DAMPING;
        constraint.setting.impulseClamp = POINT_CONSTRAINT_IMPULSE_CLAMP;
    }

    private boolean hasConnectionTarget(Display firstDisplay, Entity firstEntity, Display secondDisplay, Entity secondEntity) {
        return firstDisplay != null || firstEntity != null || secondDisplay != null || secondEntity != null;
    }

    private void processPendingConnections() {
        Iterator<PendingConnectionRequest> iterator = pendingConnections.iterator();
        while (iterator.hasNext()) {
            PendingConnectionRequest request = iterator.next();
            if (!request.isStillValid()) {
                iterator.remove();
                continue;
            }
            if (createPointConstraint(request.firstDisplay, request.firstEntity, request.firstPoint, request.secondDisplay, request.secondEntity, request.secondPoint, request.preserveCenterDistance)) {
                iterator.remove();
                continue;
            }
            request.attempts++;
            if (request.attempts > 40) {
                iterator.remove();
            }
        }
    }

    private void processPendingTypedConstraints() {
        Iterator<PendingTypedConstraintRequest> iterator = pendingTypedConstraints.iterator();
        while (iterator.hasNext()) {
            PendingTypedConstraintRequest request = iterator.next();
            if (!request.isStillValid()) {
                iterator.remove();
                continue;
            }
            if (createTypedConstraint(request.type, request.firstDisplay, request.firstEntity, request.firstPoint,
                    request.secondDisplay, request.secondEntity, request.secondPoint,
                    request.lowerLinear, request.upperLinear, request.lowerAngular, request.upperAngular, request.breakImpulse)) {
                iterator.remove();
                continue;
            }
            request.attempts++;
            if (request.attempts > 40) {
                iterator.remove();
            }
        }
    }

    private void breakOverloadedFixedConstraints() {
        Iterator<ConstraintBinding> iterator = constraints.iterator();
        while (iterator.hasNext()) {
            ConstraintBinding binding = iterator.next();
            if (!"fixed".equals(binding.type) || binding.breakImpulse <= 0.0f) continue;
            float appliedImpulse = Math.abs(binding.constraint.getAppliedImpulse());
            if (appliedImpulse <= binding.breakImpulse) continue;

            dynamicsWorld.removeConstraint(binding.constraint);
            iterator.remove();
            wakeIfDynamic(binding.first);
            wakeIfDynamic(binding.second);
        }
    }

    private void fireContactEvents() {
        int manifoldCount = dispatcher.getNumManifolds();
        for (int i = 0; i < manifoldCount; i++) {
            PersistentManifold manifold = dispatcher.getManifoldByIndexInternal(i);
            BodyContactRef first = contactRef(manifold.getBody0());
            BodyContactRef second = contactRef(manifold.getBody1());
            if (first == null || second == null) continue;
            if (!first.isRigidBody() && !second.isRigidBody()) continue;

            for (int contactIndex = 0; contactIndex < manifold.getNumContacts(); contactIndex++) {
                ManifoldPoint point = manifold.getContactPoint(contactIndex);
                if (point.getDistance() > 0.0f) continue;
                fireContactPair(first, second, point, true);
                fireContactPair(second, first, point, false);
            }
        }
    }

    private BodyContactRef contactRef(Object value) {
        if (!(value instanceof CollisionObject)) return null;
        Object ref = ((CollisionObject) value).getUserPointer();
        return ref instanceof BodyContactRef ? (BodyContactRef) ref : null;
    }

    private void fireContactPair(BodyContactRef source, BodyContactRef target, ManifoldPoint point, boolean sourceIsBodyA) {
        if (!source.isRigidBody()) return;
        Box body = source.box;
        Location contactPoint = contactLocation(body, sourceIsBodyA ? point.positionWorldOnA : point.positionWorldOnB);
        Vector3d normal = vec(point.normalWorldOnB);
        if (sourceIsBodyA) normal.negate();
        double impulse = Math.max(0.0d, point.appliedImpulse);

        switch (target.type) {
            case RIGID:
                if (target.box != null && target.box != body) {
                    Bukkit.getPluginManager().callEvent(new PhysRigidBodyContactEvent(body, target.box, contactPoint, normal, impulse));
                }
                break;
            case BLOCK:
                Bukkit.getPluginManager().callEvent(new PhysBlockContactEvent(body, target.block(body), contactPoint, normal, impulse));
                break;
            case ENTITY:
                if (target.entity != null && target.entity.isValid()) {
                    Bukkit.getPluginManager().callEvent(new PhysEntityContactEvent(body, target.entity, contactPoint, normal, impulse));
                }
                break;
            default:
                break;
        }
    }

    private Location contactLocation(Box box, Vector3f point) {
        World world = boxWorld(box);
        return new Location(world, point.x, point.y, point.z);
    }

    private void fireFluidContactEvent(Box box, FluidSample sample, float submerged) {
        Location point = new Location(boxWorld(box), box.body.position.x, sample.surfaceY, box.body.position.z);
        Bukkit.getPluginManager().callEvent(new PhysFluidContactEvent(
                box,
                sample.fluidType,
                point,
                new Vector3d(0.0d, 1.0d, 0.0d),
                submerged,
                vec(sample.velocity),
                sample.density
        ));
    }

    public List<ConstraintSnapshot> pointConstraints() {
        List<ConstraintSnapshot> snapshots = new ArrayList<>();
        for (ConstraintBinding binding : constraints) {
            snapshots.add(new ConstraintSnapshot(
                    binding.id,
                    binding.type,
                    toLocation(binding.world, binding.firstIsWorldPoint ? binding.firstPivot : pivotToWorld(binding.first, binding.firstPivot)),
                    toLocation(binding.world, binding.secondIsWorldPoint ? binding.secondPivot : pivotToWorld(binding.second, binding.secondPivot))
            ));
        }
        for (DistanceConstraintBinding binding : distanceConstraints) {
            snapshots.add(new ConstraintSnapshot(
                    binding.id,
                    "distance",
                    toLocation(binding.world, binding.firstIsWorldPoint ? binding.firstPivot : pivotToWorld(binding.first, binding.firstPivot)),
                    toLocation(binding.world, binding.secondIsWorldPoint ? binding.secondPivot : pivotToWorld(binding.second, binding.secondPivot))
            ));
        }
        for (SpringBinding binding : springs) {
            com.bulletphysics.dynamics.RigidBody first = bodyFor(binding.firstDisplay);
            com.bulletphysics.dynamics.RigidBody second = bodyFor(binding.secondDisplay);
            if (first == null || second == null) continue;
            Transform firstTransform = new Transform();
            Transform secondTransform = new Transform();
            first.getCenterOfMassTransform(firstTransform);
            second.getCenterOfMassTransform(secondTransform);
            snapshots.add(new ConstraintSnapshot(
                    binding.id,
                    "spring",
                    toLocation(binding.firstDisplay.getWorld(), firstTransform.origin),
                    toLocation(binding.secondDisplay.getWorld(), secondTransform.origin)
            ));
        }
        return snapshots;
    }

    public List<ConstraintDetail> constraintDetails() {
        List<ConstraintDetail> details = new ArrayList<>();
        for (ConstraintBinding binding : constraints) {
            details.add(new ConstraintDetail(
                    binding.id,
                    binding.type,
                    displayFor(binding.first),
                    displayFor(binding.second),
                toLocation(binding.world, binding.firstIsWorldPoint ? binding.firstPivot : pivotToWorld(binding.first, binding.firstPivot)),
                toLocation(binding.world, binding.secondIsWorldPoint ? binding.secondPivot : pivotToWorld(binding.second, binding.secondPivot)),
                0.0d,
                0.0d,
                0.0d,
                binding.lowerLinear,
                binding.upperLinear,
                binding.lowerAngular,
                binding.upperAngular,
                binding.breakImpulse
            ));
        }
        for (DistanceConstraintBinding binding : distanceConstraints) {
            details.add(new ConstraintDetail(
                    binding.id,
                    "distance",
                    displayFor(binding.first),
                    displayFor(binding.second),
                    toLocation(binding.world, binding.firstIsWorldPoint ? binding.firstPivot : pivotToWorld(binding.first, binding.firstPivot)),
                    toLocation(binding.world, binding.secondIsWorldPoint ? binding.secondPivot : pivotToWorld(binding.second, binding.secondPivot)),
                    binding.restLength,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d
            ));
        }
        for (SpringBinding binding : springs) {
            com.bulletphysics.dynamics.RigidBody first = bodyFor(binding.firstDisplay);
            com.bulletphysics.dynamics.RigidBody second = bodyFor(binding.secondDisplay);
            if (first == null || second == null) continue;
            Transform firstTransform = new Transform();
            Transform secondTransform = new Transform();
            first.getCenterOfMassTransform(firstTransform);
            second.getCenterOfMassTransform(secondTransform);
            details.add(new ConstraintDetail(
                    binding.id,
                    "spring",
                    binding.firstDisplay,
                    binding.secondDisplay,
                    toLocation(binding.firstDisplay.getWorld(), firstTransform.origin),
                    toLocation(binding.secondDisplay.getWorld(), secondTransform.origin),
                    binding.restLength,
                    binding.stiffness,
                    binding.damping,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d
            ));
        }
        return details;
    }

    public BulletDebugSnapshot debugSnapshot(Display display) {
        com.bulletphysics.dynamics.RigidBody body = bodyFor(display);
        if (body == null) return null;

        Transform worldTransform = new Transform();
        Transform centerTransform = new Transform();
        Transform interpolationTransform = new Transform();
        body.getWorldTransform(worldTransform);
        body.getCenterOfMassTransform(centerTransform);
        body.getInterpolationWorldTransform(interpolationTransform);

        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        Vector3f interpolationLinearVelocity = new Vector3f();
        Vector3f interpolationAngularVelocity = new Vector3f();
        Vector3f gravity = new Vector3f();
        Vector3f invInertiaDiagLocal = new Vector3f();
        Vector3f centerPosition = new Vector3f();
        Vector3f aabbMin = new Vector3f();
        Vector3f aabbMax = new Vector3f();
        Vector3f localScaling = new Vector3f();
        Vector3f boundingSphereCenter = new Vector3f();
        float[] boundingSphereRadius = new float[1];
        Quat4f orientation = new Quat4f();
        javax.vecmath.Matrix3f invInertiaTensorWorld = new javax.vecmath.Matrix3f();

        body.getLinearVelocity(linearVelocity);
        body.getAngularVelocity(angularVelocity);
        body.getInterpolationLinearVelocity(interpolationLinearVelocity);
        body.getInterpolationAngularVelocity(interpolationAngularVelocity);
        body.getGravity(gravity);
        body.getInvInertiaDiagLocal(invInertiaDiagLocal);
        body.getCenterOfMassPosition(centerPosition);
        body.getOrientation(orientation);
        body.getInvInertiaTensorWorld(invInertiaTensorWorld);
        body.getAabb(aabbMin, aabbMax);

        com.bulletphysics.collision.shapes.CollisionShape shape = body.getCollisionShape();
        String shapeName = "none";
        String shapeType = "none";
        float shapeMargin = 0.0f;
        if (shape != null) {
            shapeName = shape.getName();
            shapeType = String.valueOf(shape.getShapeType());
            shapeMargin = shape.getMargin();
            shape.getLocalScaling(localScaling);
            shape.getBoundingSphere(boundingSphereCenter, boundingSphereRadius);
        }

        return new BulletDebugSnapshot(
                vec(worldTransform.origin),
                vec(centerTransform.origin),
                vec(interpolationTransform.origin),
                vec(centerPosition),
                new Quaterniond(orientation.x, orientation.y, orientation.z, orientation.w),
                vec(linearVelocity),
                vec(angularVelocity),
                vec(interpolationLinearVelocity),
                vec(interpolationAngularVelocity),
                vec(gravity),
                body.getInvMass(),
                body.getLinearDamping(),
                body.getAngularDamping(),
                body.getLinearSleepingThreshold(),
                body.getAngularSleepingThreshold(),
                body.getFriction(),
                body.getRestitution(),
                body.getActivationState(),
                body.isActive(),
                body.wantsSleeping(),
                body.isInWorld(),
                body.getAngularFactor(),
                body.getNumConstraintRefs(),
                body.getCollisionFlags(),
                body.getIslandTag(),
                body.getCompanionId(),
                body.getHitFraction(),
                body.getCcdSweptSphereRadius(),
                body.getCcdMotionThreshold(),
                shapeName,
                shapeType,
                shapeMargin,
                vec(localScaling),
                vec(boundingSphereCenter),
                boundingSphereRadius[0],
                vec(aabbMin),
                vec(aabbMax),
                mat(invInertiaTensorWorld),
                vec(invInertiaDiagLocal)
        );
    }

    public boolean removeConstraint(int id) {
        Iterator<ConstraintBinding> iterator = constraints.iterator();
        while (iterator.hasNext()) {
            ConstraintBinding binding = iterator.next();
            if (binding.id != id) continue;
            dynamicsWorld.removeConstraint(binding.constraint);
            iterator.remove();
            wakeIfDynamic(binding.first);
            wakeIfDynamic(binding.second);
            return true;
        }
        Iterator<DistanceConstraintBinding> distanceIterator = distanceConstraints.iterator();
        while (distanceIterator.hasNext()) {
            DistanceConstraintBinding binding = distanceIterator.next();
            if (binding.id != id) continue;
            distanceIterator.remove();
            wakeIfDynamic(binding.first);
            wakeIfDynamic(binding.second);
            return true;
        }
        Iterator<SpringBinding> springIterator = springs.iterator();
        while (springIterator.hasNext()) {
            SpringBinding binding = springIterator.next();
            if (binding.id != id) continue;
            springIterator.remove();
            wakeIfDynamic(bodyFor(binding.firstDisplay));
            wakeIfDynamic(bodyFor(binding.secondDisplay));
            return true;
        }
        return false;
    }

    private BodyAnchor anchorFor(Display display, Entity entity, Location worldPoint) {
        if (display != null) {
            for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
                Box box = entry.getKey();
                if (!box.body.getAllDisplay().contains(display)) continue;
                return bodyAnchor(entry.getValue(), worldPoint);
            }
        }
        if (entity != null) {
            com.bulletphysics.dynamics.RigidBody body = entityBodies.get(entity);
            if (body != null && body.getInvMass() > 0.0f) return bodyAnchor(body, worldPoint);
        }
        return null;
    }

    private BodyAnchor bodyAnchor(com.bulletphysics.dynamics.RigidBody body, Location worldPoint) {
        Transform transform = new Transform();
        body.getCenterOfMassTransform(transform);
        Transform inverse = new Transform();
        inverse.inverse(transform);
        Vector3f localPivot = toBullet(worldPoint);
        inverse.transform(localPivot);
        return new BodyAnchor(body, localPivot);
    }

    private Transform constraintFrame(com.bulletphysics.dynamics.RigidBody body, Location pivot, Location axisTarget) {
        Transform worldFrame = new Transform();
        worldFrame.setIdentity();
        worldFrame.origin.set(toBullet(pivot));
        worldFrame.basis.set(basisFromAxis(pivot, axisTarget));

        Transform bodyTransform = new Transform();
        body.getCenterOfMassTransform(bodyTransform);
        Transform inverse = new Transform();
        inverse.inverse(bodyTransform);
        Transform localFrame = new Transform();
        localFrame.mul(inverse, worldFrame);
        return localFrame;
    }

    private Matrix3f basisFromAxis(Location pivot, Location axisTarget) {
        Vector3f xAxis = new Vector3f(
                (float) (axisTarget.getX() - pivot.getX()),
                (float) (axisTarget.getY() - pivot.getY()),
                (float) (axisTarget.getZ() - pivot.getZ())
        );
        if (xAxis.lengthSquared() < 1.0e-6f) {
            xAxis.set(1.0f, 0.0f, 0.0f);
        } else {
            xAxis.normalize();
        }

        Vector3f up = Math.abs(xAxis.y) < 0.9f ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(0.0f, 0.0f, 1.0f);
        Vector3f zAxis = new Vector3f();
        zAxis.cross(xAxis, up);
        zAxis.normalize();
        Vector3f yAxis = new Vector3f();
        yAxis.cross(zAxis, xAxis);
        yAxis.normalize();

        Matrix3f basis = new Matrix3f();
        basis.setColumn(0, xAxis);
        basis.setColumn(1, yAxis);
        basis.setColumn(2, zAxis);
        return basis;
    }

    private void wakeBody(com.bulletphysics.dynamics.RigidBody body) {
        body.forceActivationState(CollisionObject.ACTIVE_TAG);
        body.setDeactivationTime(0.0f);
        body.activate(true);
        dynamicsWorld.updateSingleAabb(body);
    }

    private void removeConstraintsFor(com.bulletphysics.dynamics.RigidBody body) {
        Iterator<ConstraintBinding> iterator = constraints.iterator();
        while (iterator.hasNext()) {
            ConstraintBinding binding = iterator.next();
            if (binding.first == body || binding.second == body) {
                dynamicsWorld.removeConstraint(binding.constraint);
                iterator.remove();
            }
        }
        distanceConstraints.removeIf(binding -> binding.first == body || binding.second == body);
        springs.removeIf(binding -> bodyFor(binding.firstDisplay) == body || bodyFor(binding.secondDisplay) == body);
    }

    private void wakeIfDynamic(com.bulletphysics.dynamics.RigidBody body) {
        if (body != null && body.getInvMass() > 0.0f) {
            wakeBody(body);
        }
    }

    private Vector3f pivotToWorld(com.bulletphysics.dynamics.RigidBody body, Vector3f localPivot) {
        Transform transform = new Transform();
        body.getCenterOfMassTransform(transform);
        Vector3f worldPivot = new Vector3f(localPivot);
        transform.transform(worldPivot);
        return worldPivot;
    }

    private void projectPointConstraints() {
        for (int i = 0; i < POSITION_CONSTRAINT_ITERATIONS; i++) {
            for (ConstraintBinding binding : constraints) {
                Vector3f firstPoint = binding.firstIsWorldPoint ? new Vector3f(binding.firstPivot) : pivotToWorld(binding.first, binding.firstPivot);
                Vector3f secondPoint = binding.secondIsWorldPoint ? new Vector3f(binding.secondPivot) : pivotToWorld(binding.second, binding.secondPivot);
                Vector3f error = new Vector3f(secondPoint);
                error.sub(firstPoint);
                if (error.lengthSquared() < 1.0e-8f) continue;

                float firstInvMass = binding.firstIsWorldPoint ? 0.0f : binding.first.getInvMass();
                float secondInvMass = binding.secondIsWorldPoint ? 0.0f : binding.second.getInvMass();
                float totalInvMass = firstInvMass + secondInvMass;
                if (totalInvMass <= 0.0f) continue;

                if (!binding.firstIsWorldPoint && firstInvMass > 0.0f) {
                    Vector3f correction = new Vector3f(error);
                    correction.scale(firstInvMass / totalInvMass);
                    moveBody(binding.first, correction);
                }
                if (!binding.secondIsWorldPoint && secondInvMass > 0.0f) {
                    Vector3f correction = new Vector3f(error);
                    correction.scale(-secondInvMass / totalInvMass);
                    moveBody(binding.second, correction);
                }
                if (binding.preserveCenterDistance) {
                    projectCenterDistance(binding);
                }
            }
            projectDistanceConstraints();
        }
    }

    private void projectDistanceConstraints() {
        Iterator<DistanceConstraintBinding> iterator = distanceConstraints.iterator();
        while (iterator.hasNext()) {
            DistanceConstraintBinding binding = iterator.next();
            if ((binding.firstIsWorldPoint || binding.first != null) && (binding.secondIsWorldPoint || binding.second != null)) {
                projectPivotDistance(binding);
                continue;
            }
            iterator.remove();
        }
    }

    private void projectPivotDistance(DistanceConstraintBinding binding) {
        Vector3f firstPoint = binding.firstIsWorldPoint ? new Vector3f(binding.firstPivot) : pivotToWorld(binding.first, binding.firstPivot);
        Vector3f secondPoint = binding.secondIsWorldPoint ? new Vector3f(binding.secondPivot) : pivotToWorld(binding.second, binding.secondPivot);
        Vector3f delta = new Vector3f(secondPoint);
        delta.sub(firstPoint);
        float distance = delta.length();
        if (distance < 1.0e-6f) return;

        float firstInvMass = binding.firstIsWorldPoint ? 0.0f : binding.first.getInvMass();
        float secondInvMass = binding.secondIsWorldPoint ? 0.0f : binding.second.getInvMass();
        float totalInvMass = firstInvMass + secondInvMass;
        if (totalInvMass <= 0.0f) return;

        float error = distance - binding.restLength;
        if (Math.abs(error) < 1.0e-4f) return;
        delta.scale(error / distance);

        if (!binding.firstIsWorldPoint && firstInvMass > 0.0f) {
            Vector3f correction = new Vector3f(delta);
            correction.scale(firstInvMass / totalInvMass);
            limitVector(correction, MAX_PROJECTION_CORRECTION);
            moveBody(binding.first, correction);
        }
        if (!binding.secondIsWorldPoint && secondInvMass > 0.0f) {
            Vector3f correction = new Vector3f(delta);
            correction.scale(-secondInvMass / totalInvMass);
            limitVector(correction, MAX_PROJECTION_CORRECTION);
            moveBody(binding.second, correction);
        }
    }

    private void projectCenterDistance(ConstraintBinding binding) {
        if (binding.first == null || binding.second == null) return;
        projectBodyDistance(binding.first, binding.second, binding.centerDistance);
    }

    private void projectBodyDistance(com.bulletphysics.dynamics.RigidBody first, com.bulletphysics.dynamics.RigidBody second, float restLength) {
        float firstInvMass = first.getInvMass();
        float secondInvMass = second.getInvMass();
        float totalInvMass = firstInvMass + secondInvMass;
        if (totalInvMass <= 0.0f) return;

        Transform firstTransform = new Transform();
        Transform secondTransform = new Transform();
        first.getCenterOfMassTransform(firstTransform);
        second.getCenterOfMassTransform(secondTransform);

        Vector3f delta = new Vector3f(secondTransform.origin);
        delta.sub(firstTransform.origin);
        float distance = delta.length();
        if (distance < 1.0e-6f) return;

        float error = distance - restLength;
        if (Math.abs(error) < 1.0e-4f) return;
        delta.scale(error / distance);

        if (firstInvMass > 0.0f) {
            Vector3f correction = new Vector3f(delta);
            correction.scale(firstInvMass / totalInvMass);
            limitVector(correction, MAX_PROJECTION_CORRECTION);
            moveBody(first, correction);
        }
        if (secondInvMass > 0.0f) {
            Vector3f correction = new Vector3f(delta);
            correction.scale(-secondInvMass / totalInvMass);
            limitVector(correction, MAX_PROJECTION_CORRECTION);
            moveBody(second, correction);
        }
    }

    private void moveBody(com.bulletphysics.dynamics.RigidBody body, Vector3f correction) {
        Transform transform = new Transform();
        body.getCenterOfMassTransform(transform);
        transform.origin.add(correction);
        body.setCenterOfMassTransform(transform);
        body.setWorldTransform(transform);
        body.getMotionState().setWorldTransform(transform);
        body.setInterpolationWorldTransform(transform);
        dampVelocityAfterProjection(body);
        wakeBody(body);
    }

    private void limitVector(Vector3f vector, float maxLength) {
        float length = vector.length();
        if (length <= maxLength || length < 1.0e-6f) return;
        vector.scale(maxLength / length);
    }

    private void dampVelocityAfterProjection(com.bulletphysics.dynamics.RigidBody body) {
        Vector3f velocity = new Vector3f();
        body.getLinearVelocity(velocity);
        limitVector(velocity, MAX_LINEAR_SPEED);
        body.setLinearVelocity(velocity);

        Vector3f angularVelocity = new Vector3f();
        body.getAngularVelocity(angularVelocity);
        limitVector(angularVelocity, MAX_ANGULAR_SPEED);
        body.setAngularVelocity(angularVelocity);
    }

    private void applySpringForces() {
        Iterator<SpringBinding> iterator = springs.iterator();
        while (iterator.hasNext()) {
            SpringBinding spring = iterator.next();
            com.bulletphysics.dynamics.RigidBody first = bodyFor(spring.firstDisplay);
            com.bulletphysics.dynamics.RigidBody second = bodyFor(spring.secondDisplay);
            if (first == null || second == null) {
                iterator.remove();
                continue;
            }
            if (!isPartitionActive(first) && !isPartitionActive(second)) continue;

            Transform firstTransform = new Transform();
            Transform secondTransform = new Transform();
            first.getCenterOfMassTransform(firstTransform);
            second.getCenterOfMassTransform(secondTransform);

            Vector3f delta = new Vector3f(secondTransform.origin);
            delta.sub(firstTransform.origin);
            float length = delta.length();
            if (length < 1.0e-6f) continue;
            delta.scale(1.0f / length);

            Vector3f firstVelocity = new Vector3f();
            Vector3f secondVelocity = new Vector3f();
            first.getLinearVelocity(firstVelocity);
            second.getLinearVelocity(secondVelocity);
            secondVelocity.sub(firstVelocity);

            float relativeSpeed = secondVelocity.dot(delta);
            float forceMagnitude = spring.stiffness * (length - spring.restLength) + spring.damping * relativeSpeed;
            Vector3f force = new Vector3f(delta);
            force.scale(forceMagnitude);

            if (first.getInvMass() > 0.0f) {
                first.applyCentralForce(force);
                wakeBody(first);
            }
            if (second.getInvMass() > 0.0f) {
                force.negate();
                second.applyCentralForce(force);
                wakeBody(second);
            }
        }
    }

    private void wakeLinkedBodies(Set<Box> activeBoxes) {
        for (ConstraintBinding binding : constraints) {
            if (!isPartitionActive(binding.first) && !isPartitionActive(binding.second)) continue;
            wakeIfDynamic(binding.first);
            wakeIfDynamic(binding.second);
        }
        for (SpringBinding spring : springs) {
            com.bulletphysics.dynamics.RigidBody first = bodyFor(spring.firstDisplay);
            com.bulletphysics.dynamics.RigidBody second = bodyFor(spring.secondDisplay);
            if (!isPartitionActive(first) && !isPartitionActive(second)) continue;
            wakeIfDynamic(first);
            wakeIfDynamic(second);
        }
        for (DistanceConstraintBinding binding : distanceConstraints) {
            if (!isPartitionActive(binding.first) && !isPartitionActive(binding.second)) continue;
            wakeIfDynamic(binding.first);
            wakeIfDynamic(binding.second);
        }
    }

    private boolean isPartitionActive(com.bulletphysics.dynamics.RigidBody body) {
        if (body == null || body.getInvMass() <= 0.0f) return false;
        Box box = boxFor(body);
        if (box == null) return false;
        BodyPartitionState state = partitionStates.get(box);
        return state == null || state.simulate;
    }

    private Box boxFor(com.bulletphysics.dynamics.RigidBody body) {
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            if (entry.getValue() == body) return entry.getKey();
        }
        return null;
    }

    private com.bulletphysics.dynamics.RigidBody bodyFor(Display display) {
        if (display == null || display.isDead()) return null;
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            if (entry.getKey().body.getAllDisplay().contains(display)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Display displayFor(com.bulletphysics.dynamics.RigidBody body) {
        if (body == null) return null;
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            if (entry.getValue() != body || entry.getKey().body.getAllDisplay().isEmpty()) continue;
            return entry.getKey().body.getAllDisplay().get(0);
        }
        return null;
    }

    private void applyGravity() {
        dynamicsWorld.setGravity(new Vector3f(
                (float) getDouble(PhysConfig.PHYSICS_GRAVITY_X),
                (float) getDouble(PhysConfig.PHYSICS_GRAVITY_Y),
                (float) getDouble(PhysConfig.PHYSICS_GRAVITY_Z)
        ));
    }

    private void syncDynamicBodies(List<Box> boxes) {
        Set<Box> liveBoxes = new HashSet<>(boxes);
        Iterator<Map.Entry<Box, com.bulletphysics.dynamics.RigidBody>> iterator = dynamicBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry = iterator.next();
            if (!liveBoxes.contains(entry.getKey())) {
                removeConstraintsFor(entry.getValue());
                dynamicsWorld.removeRigidBody(entry.getValue());
                partitionStates.remove(entry.getKey());
                suspendedMotions.remove(entry.getKey());
                iterator.remove();
            }
        }

        for (Box box : boxes) {
            if (!dynamicBodies.containsKey(box)) {
                com.bulletphysics.dynamics.RigidBody body = createDynamicBody(box);
                body.setUserPointer(BodyContactRef.rigidBody(box));
                dynamicBodies.put(box, body);
                dynamicsWorld.addRigidBody(body);
            }
        }
    }

    private Set<Box> partitionActiveBoxes(List<Box> boxes) {
        Set<Box> activeBoxes = new HashSet<>();
        if (!getBoolean(PhysConfig.PERFORMANCE_PARTITION_ENABLED)) {
            for (Box box : boxes) {
                com.bulletphysics.dynamics.RigidBody body = dynamicBodies.get(box);
                if (body != null) {
                    partitionStates.put(box, BodyPartitionState.active());
                    if (body.getInvMass() > 0.0f) {
                        restoreSuspendedMotion(box, body);
                        wakeBody(body);
                    }
                    activeBoxes.add(box);
                }
            }
            return activeBoxes;
        }

        double activeRadius = Math.max(0.0d, getDouble(PhysConfig.PERFORMANCE_ACTIVE_RADIUS));
        double throttleRadius = Math.max(activeRadius, getDouble(PhysConfig.PERFORMANCE_THROTTLE_RADIUS));
        double sleepRadius = Math.max(throttleRadius, getDouble(PhysConfig.PERFORMANCE_SLEEP_RADIUS));
        double activeRadiusSquared = square(activeRadius);
        double throttleRadiusSquared = square(throttleRadius);
        double sleepRadiusSquared = square(sleepRadius);
        int throttleInterval = Math.max(1, getInt(PhysConfig.PERFORMANCE_THROTTLE_INTERVAL));
        double cellSize = Math.max(1.0d, getDouble(PhysConfig.PERFORMANCE_CELL_SIZE));
        partitionStates.clear();

        Map<String, List<Box>> cells = new HashMap<>();
        for (Box box : boxes) {
            if (!isLoaded(box)) {
                partitionStates.put(box, BodyPartitionState.paused());
                continue;
            }
            cells.computeIfAbsent(cellKey(boxWorld(box), box.body.position.x, box.body.position.z, cellSize), ignored -> new ArrayList<>()).add(box);
        }

        Map<Box, BodyPartitionState> nextStates = new HashMap<>();
        int cellRadius = (int) Math.ceil(sleepRadius / cellSize);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid()) continue;
            int playerCellX = floor(player.getLocation().getX() / cellSize);
            int playerCellZ = floor(player.getLocation().getZ() / cellSize);
            for (int dx = -cellRadius; dx <= cellRadius; dx++) {
                for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                    List<Box> candidates = cells.get(cellKey(player.getWorld(), playerCellX + dx, playerCellZ + dz));
                    if (candidates == null) continue;
                    for (Box box : candidates) {
                        double distanceSquared = distanceSquared(box, player.getLocation());
                        BodyPartitionState state = stateForDistance(distanceSquared, activeRadiusSquared, throttleRadiusSquared, sleepRadiusSquared, throttleInterval, box);
                        nextStates.merge(box, state, BodyPartitionState::moreActive);
                    }
                }
            }
        }

        for (Box box : boxes) {
            com.bulletphysics.dynamics.RigidBody body = dynamicBodies.get(box);
            if (body == null) continue;
            BodyPartitionState state = partitionStates.get(box);
            if (state == null || state.priority != BodyPartitionState.PAUSED_PRIORITY) {
                state = nextStates.getOrDefault(box, BodyPartitionState.sleeping());
            }
            partitionStates.put(box, state);
            if (state.simulate) {
                activeBoxes.add(box);
                if (body.getInvMass() > 0.0f) {
                    restoreSuspendedMotion(box, body);
                    wakeBody(body);
                }
                continue;
            }
            captureSuspendedMotion(box, body);
            if (state.priority == BodyPartitionState.PAUSED_PRIORITY) pauseBody(body);
            else sleepBody(body);
        }
        return activeBoxes;
    }

    private void captureSuspendedMotion(Box box, com.bulletphysics.dynamics.RigidBody body) {
        if (body == null || body.getInvMass() <= 0.0f || suspendedMotions.containsKey(box)) return;

        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        body.getLinearVelocity(linearVelocity);
        body.getAngularVelocity(angularVelocity);
        if (!isFinite(linearVelocity.x) || !isFinite(linearVelocity.y) || !isFinite(linearVelocity.z)
                || !isFinite(angularVelocity.x) || !isFinite(angularVelocity.y) || !isFinite(angularVelocity.z)) {
            return;
        }
        suspendedMotions.put(box, new SuspendedMotion(linearVelocity, angularVelocity));
    }

    private void restoreSuspendedMotion(Box box, com.bulletphysics.dynamics.RigidBody body) {
        SuspendedMotion motion = suspendedMotions.remove(box);
        if (motion == null || body == null || body.getInvMass() <= 0.0f) return;

        body.setLinearVelocity(new Vector3f(motion.linearVelocity));
        body.setAngularVelocity(new Vector3f(motion.angularVelocity));
        box.body.velocity.set(motion.linearVelocity.x, motion.linearVelocity.y, motion.linearVelocity.z);
        box.body.rotation.set(motion.angularVelocity.x, motion.angularVelocity.y, motion.angularVelocity.z);
    }

    private BodyPartitionState stateForDistance(double distanceSquared, double activeRadiusSquared, double throttleRadiusSquared, double sleepRadiusSquared, int throttleInterval, Box box) {
        if (distanceSquared <= activeRadiusSquared) return BodyPartitionState.active();
        if (distanceSquared <= throttleRadiusSquared) {
            long cellHash = cellHash(box);
            boolean simulate = Math.floorMod(simulationTick + cellHash, throttleInterval) == 0;
            return BodyPartitionState.throttled(simulate);
        }
        if (distanceSquared <= sleepRadiusSquared) return BodyPartitionState.sleeping();
        return BodyPartitionState.sleeping();
    }

    private World boxWorld(Box box) {
        Display display = primaryDisplay(box);
        return display == null ? null : display.getWorld();
    }

    private Location boxLocation(Box box) {
        World world = boxWorld(box);
        if (world == null) {
            return null;
        }
        return new Location(world, box.body.position.x, box.body.position.y, box.body.position.z);
    }

    private Display primaryDisplay(Box box) {
        if (box == null || box.body == null) {
            return null;
        }
        for (Display display : box.body.getAllDisplay()) {
            if (display != null && display.isValid() && !display.isDead()) {
                return display;
            }
        }
        return null;
    }

    private String cellKey(World world, double x, double z, double cellSize) {
        return cellKey(world, floor(x / cellSize), floor(z / cellSize));
    }

    private String cellKey(World world, int cellX, int cellZ) {
        return (world == null ? "null" : world.getUID().toString()) + ":" + cellX + ":" + cellZ;
    }

    private double distanceSquared(Box box, Location location) {
        double dx = box.body.position.x - location.getX();
        double dy = box.body.position.y - location.getY();
        double dz = box.body.position.z - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean isLoaded(Box box) {
        if (!getBoolean(PhysConfig.PERFORMANCE_PAUSE_UNLOADED_CHUNKS)) return true;
        World world = boxWorld(box);
        if (world == null) return false;
        int chunkX = floor(box.body.position.x) >> 4;
        int chunkZ = floor(box.body.position.z) >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    private long cellHash(Box box) {
        double cellSize = Math.max(1.0d, getDouble(PhysConfig.PERFORMANCE_CELL_SIZE));
        long x = floor(box.body.position.x / cellSize);
        long y = floor(box.body.position.y / cellSize);
        long z = floor(box.body.position.z / cellSize);
        return x * 73428767L ^ y * 91278353L ^ z * 43828991L;
    }

    private void sleepBody(com.bulletphysics.dynamics.RigidBody body) {
        if (body == null || body.getInvMass() <= 0.0f) return;
        body.forceActivationState(CollisionObject.ISLAND_SLEEPING);
    }

    private void pauseBody(com.bulletphysics.dynamics.RigidBody body) {
        if (body == null || body.getInvMass() <= 0.0f) return;
        body.forceActivationState(CollisionObject.DISABLE_SIMULATION);
    }

    private void expandActiveBoxesThroughConstraints(Set<Box> activeBoxes) {
        boolean changed;
        do {
            changed = false;
            for (ConstraintBinding binding : constraints) {
                changed |= addLinkedBox(activeBoxes, binding.first, binding.second);
                changed |= addLinkedBox(activeBoxes, binding.second, binding.first);
            }
            for (DistanceConstraintBinding binding : distanceConstraints) {
                changed |= addLinkedBox(activeBoxes, binding.first, binding.second);
                changed |= addLinkedBox(activeBoxes, binding.second, binding.first);
            }
            for (SpringBinding spring : springs) {
                changed |= addLinkedBox(activeBoxes, bodyFor(spring.firstDisplay), bodyFor(spring.secondDisplay));
                changed |= addLinkedBox(activeBoxes, bodyFor(spring.secondDisplay), bodyFor(spring.firstDisplay));
            }
        } while (changed);
    }

    private boolean addLinkedBox(Set<Box> activeBoxes, com.bulletphysics.dynamics.RigidBody activeBody, com.bulletphysics.dynamics.RigidBody linkedBody) {
        Box activeBox = boxFor(activeBody);
        Box linkedBox = boxFor(linkedBody);
        if (activeBox == null || linkedBox == null || !activeBoxes.contains(activeBox) || activeBoxes.contains(linkedBox)) return false;
        if (!isLoaded(linkedBox)) return false;
        activeBoxes.add(linkedBox);
        partitionStates.put(linkedBox, BodyPartitionState.active());
        restoreSuspendedMotion(linkedBox, linkedBody);
        wakeIfDynamic(linkedBody);
        return true;
    }

    private com.bulletphysics.dynamics.RigidBody createDynamicBody(Box box) {
        float mass = box.body.hasFiniteMass() ? (float) box.body.getMass() : 0.0f;
        CollisionShape shape = collisionShapeFor(box);
        shape.setMargin(dynamicMargin(box));
        Vector3f localInertia = new Vector3f();
        if (mass > 0.0f) {
            shape.calculateLocalInertia(mass, localInertia);
        }

        Transform startTransform = new Transform();
        startTransform.setIdentity();
        startTransform.origin.set(toBullet(box.body.position));
        startTransform.setRotation(toBullet(box.body.orientation));

        RigidBodyConstructionInfo constructionInfo = new RigidBodyConstructionInfo(
                mass,
                new DefaultMotionState(startTransform),
                shape,
                localInertia
        );
        constructionInfo.friction = (float) getDouble(PhysConfig.PHYSICS_STATIC_FRICTION);
        constructionInfo.restitution = (float) getDouble(PhysConfig.PHYSICS_RESTITUTION);
        constructionInfo.linearDamping = (float) getDouble(PhysConfig.PHYSICS_LINEAR_DAMPING);
        constructionInfo.angularDamping = (float) getDouble(PhysConfig.PHYSICS_ANGULAR_DAMPING);

        com.bulletphysics.dynamics.RigidBody rigidBody = new com.bulletphysics.dynamics.RigidBody(constructionInfo);
        rigidBody.setLinearVelocity(toBullet(box.body.velocity));
        rigidBody.setAngularVelocity(toBullet(box.body.rotation));
        applyDynamicSettings(rigidBody);
        return rigidBody;
    }

    private CollisionShape collisionShapeFor(Box box) {
        if (box.body instanceof PhysSphereDisplay) {
            return new SphereShape((float) ((PhysSphereDisplay) box.body).radius());
        }
        if (box.body instanceof CompoundPhysBlockDisplay) {
            CompoundShape compoundShape = new CompoundShape();
            boolean hasChildShape = false;
            for (CompoundPhysBlockDisplay.Part part : ((CompoundPhysBlockDisplay) box.body).parts()) {
                if (!part.hasCollisionBoxes) {
                    Transform childTransform = new Transform();
                    childTransform.setIdentity();
                    childTransform.origin.set(toBullet(part.localOffset));
                    Vector3d halfSize = new Vector3d(part.scale.x * 0.5d, part.scale.y * 0.5d, part.scale.z * 0.5d);
                    compoundShape.addChildShape(childTransform, new BoxShape(toBullet(halfSize)));
                    hasChildShape = true;
                    continue;
                }
                for (CompoundPhysBlockDisplay.CollisionBox collisionBox : part.collisionBoxes) {
                    Transform childTransform = new Transform();
                    childTransform.setIdentity();
                    childTransform.origin.set(toBullet(new Vector3d(part.localOffset).add(collisionBox.localCenter)));
                    compoundShape.addChildShape(childTransform, new BoxShape(toBullet(collisionBox.halfSize)));
                    hasChildShape = true;
                }
            }
            if (!hasChildShape) {
                return new BoxShape(toBullet(box.halfSize));
            }
            compoundShape.recalculateLocalAabb();
            return compoundShape;
        }
        return new BoxShape(toBullet(box.halfSize));
    }

    private void applyDynamicSettingsIfNeeded() {
        String signature = dynamicSettingsSignature();
        if (signature.equals(dynamicSettingsSignature)) return;
        dynamicSettingsSignature = signature;

        for (com.bulletphysics.dynamics.RigidBody body : dynamicBodies.values()) {
            applyDynamicSettings(body);
        }
    }

    private void applyDynamicSettings(com.bulletphysics.dynamics.RigidBody body) {
        body.setFriction((float) getDouble(PhysConfig.PHYSICS_STATIC_FRICTION));
        body.setRestitution((float) getDouble(PhysConfig.PHYSICS_RESTITUTION));
        body.setDamping(
                (float) getDouble(PhysConfig.PHYSICS_LINEAR_DAMPING),
                (float) getDouble(PhysConfig.PHYSICS_ANGULAR_DAMPING)
        );
        body.setSleepingThresholds(
                (float) getDouble(PhysConfig.PHYSICS_LINEAR_SLEEPING_THRESHOLD),
                (float) getDouble(PhysConfig.PHYSICS_ANGULAR_SLEEPING_THRESHOLD)
        );
    }

    private float dynamicMargin(Box box) {
        double configured = getDouble(PhysConfig.PHYSICS_DYNAMIC_MARGIN);
        double smallestHalfSize = Math.min(box.halfSize.x, Math.min(box.halfSize.y, box.halfSize.z));
        return (float) Math.max(0.0d, Math.min(configured, smallestHalfSize * 0.25d));
    }

    private void syncEntityBodies(Collection<Box> boxes) {
        Set<Entity> requiredEntities = boxes.isEmpty() ? new HashSet<>() : (shouldSyncEntities() ? collectNearbyEntities(boxes) : cachedRequiredEntities);
        cachedRequiredEntities = requiredEntities;
        Iterator<Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody>> iterator = entityBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody> entry = iterator.next();
            if (!entry.getKey().isValid() || !requiredEntities.contains(entry.getKey())) {
                removeConstraintsFor(entry.getValue());
                dynamicsWorld.removeRigidBody(entry.getValue());
                iterator.remove();
            }
        }

        for (Entity entity : requiredEntities) {
            double mass = entityMass(entity);
            com.bulletphysics.dynamics.RigidBody existing = entityBodies.get(entity);
            if (existing != null && Math.abs(existing.getInvMass() - inverseMass(mass)) > 0.0001f) {
                removeConstraintsFor(existing);
                dynamicsWorld.removeRigidBody(existing);
                entityBodies.remove(entity);
                existing = null;
            }

            if (existing == null) {
                com.bulletphysics.dynamics.RigidBody body = createEntityBody(entity, mass);
                body.setUserPointer(BodyContactRef.entity(entity));
                entityBodies.put(entity, body);
                dynamicsWorld.addRigidBody(body);
            } else {
                syncEntityCollisionBody(entity, existing);
            }
        }
    }

    private Set<Entity> collectNearbyEntities(Collection<Box> boxes) {
        Set<Entity> entities = new HashSet<>();
        if (!getBoolean(PhysConfig.ENTITY_COLLISION_ENABLED)) return entities;

        for (Box box : boxes) {
            Location location = boxLocation(box);
            if (location == null || location.getWorld() == null) continue;
            double radius = getDouble(PhysConfig.ENTITY_SCAN_RADIUS) + box.halfSize.length();
            for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
                if (shouldCollideWithEntity(entity)) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    private boolean shouldCollideWithEntity(Entity entity) {
        return entity.isValid() && !(entity instanceof Display);
    }

    private com.bulletphysics.dynamics.RigidBody createEntityBody(Entity entity, double massValue) {
        BoundingBox box = entity.getBoundingBox();
        Vector3d halfSize = new Vector3d(
                Math.max(0.05d, box.getWidthX() * 0.5d),
                Math.max(0.05d, box.getHeight() * 0.5d),
                Math.max(0.05d, box.getWidthZ() * 0.5d)
        );

        CollisionShape shape = new BoxShape(toBullet(halfSize));
        shape.setMargin((float) getDouble(PhysConfig.PHYSICS_DYNAMIC_MARGIN));
        float mass = (float) Math.max(0.0d, massValue);
        Vector3f localInertia = new Vector3f();
        if (mass > 0.0f) shape.calculateLocalInertia(mass, localInertia);

        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set((float) box.getCenterX(), (float) box.getCenterY(), (float) box.getCenterZ());

        com.bulletphysics.dynamics.RigidBody body = new com.bulletphysics.dynamics.RigidBody(
                new RigidBodyConstructionInfo(mass, new DefaultMotionState(transform), shape, localInertia)
        );
        if (mass <= 0.0f) {
            body.setCollisionFlags(body.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
            body.setActivationState(com.bulletphysics.collision.dispatch.CollisionObject.DISABLE_DEACTIVATION);
        }
        body.setFriction((float) getDouble(PhysConfig.PHYSICS_STATIC_FRICTION));
        body.setRestitution((float) getDouble(PhysConfig.PHYSICS_RESTITUTION));
        body.setGravity(new Vector3f());
        body.setLinearVelocity(new Vector3f(
                (float) entity.getVelocity().getX(),
                (float) entity.getVelocity().getY(),
                (float) entity.getVelocity().getZ()
        ));
        return body;
    }

    private void syncEntityCollisionBody(Entity entity, com.bulletphysics.dynamics.RigidBody body) {
        BoundingBox box = entity.getBoundingBox();
        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set((float) box.getCenterX(), (float) box.getCenterY(), (float) box.getCenterZ());
        body.setCenterOfMassTransform(transform);
        body.setWorldTransform(transform);
        body.getMotionState().setWorldTransform(transform);
        body.setInterpolationWorldTransform(transform);
        body.setLinearVelocity(new Vector3f(
                (float) entity.getVelocity().getX(),
                (float) entity.getVelocity().getY(),
                (float) entity.getVelocity().getZ()
        ));
        body.setAngularVelocity(new Vector3f());
        body.clearForces();
        body.setGravity(new Vector3f());
        dynamicsWorld.updateSingleAabb(body);
        body.activate();
    }

    private void syncStaticTerrain(Collection<Box> boxes) {
        String settingsSignature = staticSettingsSignature();
        if (!settingsSignature.equals(staticSettingsSignature)) {
            clearStaticTerrain();
            staticSettingsSignature = settingsSignature;
        }

        Set<String> requiredShapes = new HashSet<>();
        for (Box box : boxes) {
            com.bulletphysics.dynamics.RigidBody rigidBody = dynamicBodies.get(box);
            if (!getBoolean(PhysConfig.PHYSICS_SCAN_SLEEPING_BODIES) && rigidBody != null && !rigidBody.isActive()) {
                continue;
            }

            Location origin = boxLocation(box);
            if (origin == null || origin.getWorld() == null) continue;
            double radius = box.halfSize.length() + getInt(PhysConfig.PHYSICS_TERRAIN_SCAN_MARGIN);
            int minX = floor(origin.getX() - radius);
            int maxX = floor(origin.getX() + radius);
            int minY = floor(origin.getY() - radius);
            int maxY = floor(origin.getY() + radius);
            int minZ = floor(origin.getZ() - radius);
            int maxZ = floor(origin.getZ() + radius);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = origin.getWorld().getBlockAt(x, y, z);
                        if (block.isEmpty() || !block.isCollidable()) continue;

                        int index = 0;
                        for (BoundingBox boundingBox : block.getCollisionShape().getBoundingBoxes()) {
                            String key = terrainKey(block.getWorld(), x, y, z, index++);
                            if (requiredShapes.add(key) && !staticBodies.containsKey(key)) {
                                addStaticBlockShape(key, block.getLocation(), block.getType(), boundingBox);
                            }
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<String, com.bulletphysics.dynamics.RigidBody>> iterator = staticBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, com.bulletphysics.dynamics.RigidBody> entry = iterator.next();
            if (!requiredShapes.contains(entry.getKey())) {
                dynamicsWorld.removeRigidBody(entry.getValue());
                iterator.remove();
            }
        }
    }

    private void applyFluidForces(Collection<Box> boxes) {
        if (!getBoolean(PhysConfig.FLUID_BUOYANCY_ENABLED)) return;

        for (Box box : boxes) {
            com.bulletphysics.dynamics.RigidBody rigidBody = dynamicBodies.get(box);
            if (rigidBody == null || rigidBody.getInvMass() <= 0.0f) continue;

            FluidSample sample = sampleFluid(box);
            if (sample == null) continue;

            float submerged = submergedFraction(box, sample.surfaceY);
            if (submerged <= 0.0f) continue;
            fireFluidContactEvent(box, sample, submerged);

            wakeBody(rigidBody);
            float mass = 1.0f / rigidBody.getInvMass();
            float gravity = Math.abs((float) getDouble(PhysConfig.PHYSICS_GRAVITY_Y));
            float buoyancy = sample.density * volume(box) * gravity * submerged / 1000.0f;
            rigidBody.applyCentralForce(new Vector3f(0.0f, buoyancy, 0.0f));

            Vector3f linearVelocity = new Vector3f();
            rigidBody.getLinearVelocity(linearVelocity);
            Vector3f relativeVelocity = new Vector3f(linearVelocity);
            relativeVelocity.sub(sample.velocity);
            relativeVelocity.scale(-sample.linearDrag * submerged * mass);
            rigidBody.applyCentralForce(relativeVelocity);

            Vector3f flowForce = new Vector3f(sample.velocity);
            flowForce.scale(sample.linearDrag * submerged * mass);
            rigidBody.applyCentralForce(flowForce);

            Vector3f angularVelocity = new Vector3f();
            rigidBody.getAngularVelocity(angularVelocity);
            angularVelocity.scale(-sample.angularDrag * submerged * mass);
            rigidBody.applyTorque(angularVelocity);
            wakeBody(rigidBody);
        }
    }

    private void applyMaterialContactEffects(Collection<Box> boxes) {
        for (Box box : boxes) {
            com.bulletphysics.dynamics.RigidBody rigidBody = dynamicBodies.get(box);
            if (rigidBody == null || rigidBody.getInvMass() <= 0.0f) continue;

            Material material = supportMaterial(box);
            if (material == null) continue;

            double dynamicFriction = clampMaterialDamping(materialDynamicFriction(material));
            double rollingFriction = clampMaterialDamping(materialRollingFriction(material));
            if (dynamicFriction <= 0.0d && rollingFriction <= 0.0d) continue;

            Vector3f linearVelocity = new Vector3f();
            rigidBody.getLinearVelocity(linearVelocity);
            double linearFactor = Math.max(0.0d, 1.0d - dynamicFriction);
            linearVelocity.x *= (float) linearFactor;
            linearVelocity.z *= (float) linearFactor;
            if (Math.abs(linearVelocity.x) < 0.002f) linearVelocity.x = 0.0f;
            if (Math.abs(linearVelocity.z) < 0.002f) linearVelocity.z = 0.0f;
            rigidBody.setLinearVelocity(linearVelocity);

            Vector3f angularVelocity = new Vector3f();
            rigidBody.getAngularVelocity(angularVelocity);
            double angularFactor = Math.max(0.0d, 1.0d - rollingFriction);
            angularVelocity.scale((float) angularFactor);
            if (angularVelocity.lengthSquared() < 1.0e-5f) angularVelocity.set(0.0f, 0.0f, 0.0f);
            rigidBody.setAngularVelocity(angularVelocity);
        }
    }

    private Material supportMaterial(Box box) {
        World world = boxWorld(box);
        if (world == null) return null;
        int y = floor(box.body.position.y - box.halfSize.y - 0.05d);
        int minX = floor(box.body.position.x - box.halfSize.x);
        int maxX = floor(box.body.position.x + box.halfSize.x);
        int minZ = floor(box.body.position.z - box.halfSize.z);
        int maxZ = floor(box.body.position.z + box.halfSize.z);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (!block.isEmpty() && block.isCollidable()) return block.getType();
            }
        }
        return null;
    }

    private FluidSample sampleFluid(Box box) {
        World world = boxWorld(box);
        if (world == null) return null;
        Vector3d[] points = fluidSamplePoints(box);

        FluidAccumulator water = new FluidAccumulator();
        FluidAccumulator lava = new FluidAccumulator();

        for (Vector3d point : points) {
            sampleFluidBlock(world.getBlockAt(floor(point.x), floor(point.y), floor(point.z)), water, lava);
        }

        FluidAccumulator selected = lava.samples > 0 ? lava : water;
        if (selected.samples == 0) return null;
        return selected.toSample();
    }

    private Vector3d[] fluidSamplePoints(Box box) {
        double x = box.body.position.x;
        double y = box.body.position.y;
        double z = box.body.position.z;
        double hx = box.halfSize.x * 0.9d;
        double hy = box.halfSize.y * 0.9d;
        double hz = box.halfSize.z * 0.9d;

        if (getInt(PhysConfig.FLUID_SAMPLE_POINTS) >= 27) {
            double[] xs = {x - hx, x, x + hx};
            double[] ys = {y - hy, y, y + hy};
            double[] zs = {z - hz, z, z + hz};
            Vector3d[] points = new Vector3d[27];
            int index = 0;
            for (double px : xs) {
                for (double py : ys) {
                    for (double pz : zs) {
                        points[index++] = new Vector3d(px, py, pz);
                    }
                }
            }
            return points;
        }

        return new Vector3d[]{
                new Vector3d(x, y, z),
                new Vector3d(x - hx, y, z),
                new Vector3d(x + hx, y, z),
                new Vector3d(x, y - hy, z),
                new Vector3d(x, y + hy, z),
                new Vector3d(x, y, z - hz),
                new Vector3d(x, y, z + hz)
        };
    }

    private void sampleFluidBlock(Block block, FluidAccumulator water, FluidAccumulator lava) {
        String key = terrainKey(block.getWorld(), block.getX(), block.getY(), block.getZ(), 0);
        FluidSample cached = fluidSampleCache.get(key);
        if (cached != null) {
            if (cached.density == 0) return;
            if (cached.fluidType == Material.LAVA) lava.add(cached);
            else water.add(cached);
            return;
        }

        Material type = block.getType();
        if (type == Material.WATER) {
            FluidSample sample = fluidSampleFor(block, Material.WATER);
            fluidSampleCache.put(key, sample);
            water.add(sample);
        } else if (type == Material.LAVA) {
            FluidSample sample = fluidSampleFor(block, Material.LAVA);
            fluidSampleCache.put(key, sample);
            lava.add(sample);
        } else if (type == Material.BUBBLE_COLUMN) {
            Block below = block.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ());
            if (below.getType() == Material.WATER) {
                FluidSample sample = fluidSampleFor(below, Material.WATER);
                sample.velocity.y += bubbleColumnVelocity(block);
                fluidSampleCache.put(key, sample);
                water.add(sample);
            } else {
                fluidSampleCache.put(key, FluidSample.empty());
            }
        } else {
            fluidSampleCache.put(key, FluidSample.empty());
        }
    }

    private FluidSample fluidSampleFor(Block block, Material fluidType) {
        if (fluidType == Material.WATER) {
            return new FluidSample(
                    block.getY() + fluidHeight(block, fluidType),
                    (float) getDouble(PhysConfig.FLUID_DENSITY_WATER),
                    (float) getDouble(PhysConfig.FLUID_LINEAR_DRAG_WATER),
                    (float) getDouble(PhysConfig.FLUID_ANGULAR_DRAG_WATER),
                    fluidVelocity(block.getWorld(), block, Material.WATER),
                    1,
                    Material.WATER
            );
        }
        return new FluidSample(
                block.getY() + fluidHeight(block, fluidType),
                (float) getDouble(PhysConfig.FLUID_DENSITY_LAVA),
                (float) getDouble(PhysConfig.FLUID_LINEAR_DRAG_LAVA),
                (float) getDouble(PhysConfig.FLUID_ANGULAR_DRAG_LAVA),
                fluidVelocity(block.getWorld(), block, Material.LAVA),
                1,
                Material.LAVA
        );
    }

    private Vector3f fluidVelocity(World world, Block block, Material fluidType) {
        Vector3f velocity = new Vector3f();
        double centerHeight = fluidHeight(block, fluidType);
        double westHeight = fluidHeight(world.getBlockAt(block.getX() - 1, block.getY(), block.getZ()), fluidType);
        double eastHeight = fluidHeight(world.getBlockAt(block.getX() + 1, block.getY(), block.getZ()), fluidType);
        double northHeight = fluidHeight(world.getBlockAt(block.getX(), block.getY(), block.getZ() - 1), fluidType);
        double southHeight = fluidHeight(world.getBlockAt(block.getX(), block.getY(), block.getZ() + 1), fluidType);
        velocity.x = (float) ((eastHeight - westHeight) * horizontalFlowScale(fluidType));
        velocity.z = (float) ((southHeight - northHeight) * horizontalFlowScale(fluidType));

        if (isFallingFluid(block)) {
            velocity.y = (float) -fallingFlowScale(fluidType);
        } else if (centerHeight > 0 && velocity.lengthSquared() > 0.0001f) {
            velocity.normalize();
            velocity.scale((float) horizontalFlowScale(fluidType));
        }
        return velocity;
    }

    private double fluidHeight(Block block, Material fluidType) {
        if (block.getType() == Material.BUBBLE_COLUMN && fluidType == Material.WATER) return 1.0d;
        if (block.getType() != fluidType) return 0.0d;

        BlockData data = block.getBlockData();
        if (!(data instanceof Levelled)) return 1.0d;

        Levelled levelled = (Levelled) data;
        int level = levelled.getLevel();
        if (level >= 8) return 1.0d;
        return Math.max(0.0d, 1.0d - (level / 8.0d));
    }

    private boolean isFallingFluid(Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Levelled && ((Levelled) data).getLevel() >= 8;
    }

    private double horizontalFlowScale(Material fluidType) {
        return fluidType == Material.WATER
                ? getDouble(PhysConfig.FLUID_HORIZONTAL_FLOW_WATER)
                : getDouble(PhysConfig.FLUID_HORIZONTAL_FLOW_LAVA);
    }

    private double fallingFlowScale(Material fluidType) {
        return fluidType == Material.WATER
                ? getDouble(PhysConfig.FLUID_FALLING_FLOW_WATER)
                : getDouble(PhysConfig.FLUID_FALLING_FLOW_LAVA);
    }

    private float bubbleColumnVelocity(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof BubbleColumn && ((BubbleColumn) data).isDrag()) {
            return (float) -getDouble(PhysConfig.FLUID_BUBBLE_DOWN_SPEED);
        }
        return (float) getDouble(PhysConfig.FLUID_BUBBLE_UP_SPEED);
    }

    private float submergedFraction(Box box, double surfaceY) {
        double bottom = box.body.position.y - box.halfSize.y;
        double top = box.body.position.y + box.halfSize.y;
        if (surfaceY <= bottom) return 0.0f;
        if (surfaceY >= top) return 1.0f;
        return (float) ((surfaceY - bottom) / (top - bottom));
    }

    private float volume(Box box) {
        if (box.body instanceof PhysSphereDisplay) {
            double radius = ((PhysSphereDisplay) box.body).radius();
            return (float) (4.0d / 3.0d * Math.PI * radius * radius * radius);
        }
        return (float) (box.halfSize.x * 2.0d * box.halfSize.y * 2.0d * box.halfSize.z * 2.0d);
    }

    private double entityMass(Entity entity) {
        String key = entity instanceof Player
                ? PhysConfig.ENTITY_PLAYER_MASS_PREFIX + entity.getName()
                : PhysConfig.ENTITY_UUID_MASS_PREFIX + entity.getUniqueId();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        String typeKey = PhysConfig.ENTITY_TYPE_MASS_PREFIX + entity.getType().name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(typeKey)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(typeKey);
        }
        return entity instanceof Player
                ? getDouble(PhysConfig.ENTITY_DEFAULT_MASS_PLAYER)
                : getDouble(PhysConfig.ENTITY_DEFAULT_MASS_NON_PLAYER);
    }

    private float inverseMass(double mass) {
        return mass <= 0.0d ? 0.0f : (float) (1.0d / mass);
    }

    private double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private void clearStaticTerrain() {
        for (com.bulletphysics.dynamics.RigidBody body : staticBodies.values()) {
            dynamicsWorld.removeRigidBody(body);
        }
        staticBodies.clear();
    }

    private void addStaticBlockShape(String key, Location blockLocation, Material material, BoundingBox boundingBox) {
        Vector3d halfSize = new Vector3d(
                boundingBox.getWidthX() * 0.5d,
                boundingBox.getHeight() * 0.5d,
                boundingBox.getWidthZ() * 0.5d
        );
        if (halfSize.x <= 0 || halfSize.y <= 0 || halfSize.z <= 0) return;

        Vector3d center = new Vector3d(
                boundingBox.getCenterX(),
                boundingBox.getCenterY(),
                boundingBox.getCenterZ()
        );
        if (isBlockLocal(boundingBox)) {
            center.add(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
        }

        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set(toBullet(center));

        CollisionShape shape = new BoxShape(toBullet(halfSize));
        shape.setMargin((float) getDouble(PhysConfig.PHYSICS_STATIC_MARGIN));
        com.bulletphysics.dynamics.RigidBody body = new com.bulletphysics.dynamics.RigidBody(
                new RigidBodyConstructionInfo(0.0f, new DefaultMotionState(transform), shape, new Vector3f())
        );
        body.setFriction((float) Math.max(0.0d, materialStaticFriction(material)));
        body.setRestitution((float) clamp01(materialRestitution(material)));
        body.setUserPointer(BodyContactRef.block(blockLocation.getWorld(), blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ(), material));
        dynamicsWorld.addRigidBody(body);
        staticBodies.put(key, body);
    }

    private double materialStaticFriction(Material material) {
        String key = PhysConfig.MATERIAL_STATIC_FRICTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        key = PhysConfig.MATERIAL_FRICTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        return specialMaterialStaticFriction(material, getDouble(PhysConfig.PHYSICS_STATIC_FRICTION));
    }

    private double materialDynamicFriction(Material material) {
        String key = PhysConfig.MATERIAL_DYNAMIC_FRICTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        key = PhysConfig.MATERIAL_FRICTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return Math.min(0.95d, Math.max(0.0d, PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key) * 0.08d));
        }
        return specialMaterialDynamicFriction(material, getDouble(PhysConfig.PHYSICS_DYNAMIC_FRICTION));
    }

    private double materialRollingFriction(Material material) {
        String key = PhysConfig.MATERIAL_ROLLING_FRICTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        return specialMaterialRollingFriction(material, getDouble(PhysConfig.PHYSICS_ROLLING_FRICTION));
    }

    private double materialRestitution(Material material) {
        String key = PhysConfig.MATERIAL_RESTITUTION_PREFIX + material.name();
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(key)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(key);
        }
        return specialMaterialRestitution(material, getDouble(PhysConfig.PHYSICS_STATIC_RESTITUTION));
    }

    private double specialMaterialStaticFriction(Material material, double fallback) {
        switch (material) {
            case ICE:
                return 0.04d;
            case PACKED_ICE:
                return 0.025d;
            case BLUE_ICE:
                return 0.015d;
            case HONEY_BLOCK:
                return 1.8d;
            case SOUL_SAND:
            case SOUL_SOIL:
                return 1.25d;
            case SLIME_BLOCK:
                return 0.65d;
            default:
                return fallback;
        }
    }

    private double specialMaterialDynamicFriction(Material material, double fallback) {
        switch (material) {
            case ICE:
                return 0.006d;
            case PACKED_ICE:
                return 0.004d;
            case BLUE_ICE:
                return 0.002d;
            case HONEY_BLOCK:
                return 0.35d;
            case SOUL_SAND:
            case SOUL_SOIL:
                return 0.18d;
            case SLIME_BLOCK:
                return 0.035d;
            default:
                return fallback;
        }
    }

    private double specialMaterialRollingFriction(Material material, double fallback) {
        switch (material) {
            case ICE:
                return 0.008d;
            case PACKED_ICE:
                return 0.006d;
            case BLUE_ICE:
                return 0.004d;
            case HONEY_BLOCK:
                return 0.45d;
            case SOUL_SAND:
            case SOUL_SOIL:
                return 0.22d;
            case SLIME_BLOCK:
                return 0.02d;
            default:
                return fallback;
        }
    }

    private double specialMaterialRestitution(Material material, double fallback) {
        if (material == Material.SLIME_BLOCK) return 0.85d;
        if (material == Material.HONEY_BLOCK || material == Material.SOUL_SAND || material == Material.SOUL_SOIL) return 0.02d;
        return fallback;
    }

    private double clampMaterialDamping(double value) {
        return Math.max(0.0d, Math.min(0.95d, value));
    }

    private void syncDisplays(Set<Box> activeBoxes) {
        Transform transform = new Transform();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        Quat4f rotation = new Quat4f();

        Iterator<Map.Entry<Box, com.bulletphysics.dynamics.RigidBody>> iterator = dynamicBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry = iterator.next();
            Box box = entry.getKey();
            com.bulletphysics.dynamics.RigidBody rigidBody = entry.getValue();
            if (!activeBoxes.contains(box)) continue;
            rigidBody.getMotionState().getWorldTransform(transform);
            rigidBody.getLinearVelocity(linearVelocity);
            rigidBody.getAngularVelocity(angularVelocity);
            transform.getRotation(rotation);

            if (!isSafeDisplayState(transform.origin, linearVelocity, angularVelocity)) {
                removeConstraintsFor(rigidBody);
                dynamicsWorld.removeRigidBody(rigidBody);
                suspendedMotions.remove(box);
                iterator.remove();
                for (Display display : box.body.getAllDisplay()) {
                    if (display != null && display.isValid()) display.remove();
                }
                continue;
            }

            limitVector(linearVelocity, MAX_LINEAR_SPEED);
            limitVector(angularVelocity, MAX_ANGULAR_SPEED);
            rigidBody.setLinearVelocity(linearVelocity);
            rigidBody.setAngularVelocity(angularVelocity);

            box.body.position.set(transform.origin.x, transform.origin.y, transform.origin.z);
            box.body.velocity.set(linearVelocity.x, linearVelocity.y, linearVelocity.z);
            box.body.rotation.set(angularVelocity.x, angularVelocity.y, angularVelocity.z);
            box.body.orientation.set(rotation.x, rotation.y, rotation.z, rotation.w).normalize();
            box.body.tick();
        }
    }

    private boolean isSafeDisplayState(Vector3f position, Vector3f linearVelocity, Vector3f angularVelocity) {
        return isFinite(position.x) && isFinite(position.y) && isFinite(position.z)
                && isFinite(linearVelocity.x) && isFinite(linearVelocity.y) && isFinite(linearVelocity.z)
                && isFinite(angularVelocity.x) && isFinite(angularVelocity.y) && isFinite(angularVelocity.z)
                && Math.abs(position.x) <= MAX_DISPLAY_COORDINATE
                && Math.abs(position.z) <= MAX_DISPLAY_COORDINATE
                && position.y > -4096.0f
                && position.y < 4096.0f;
    }

    private boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private void syncEntities() {
        Transform transform = new Transform();
        Vector3f velocity = new Vector3f();
        double velocityFactor = clamp01(getDouble(PhysConfig.ENTITY_VELOCITY_SYNC_FACTOR));

        for (Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody> entry : entityBodies.entrySet()) {
            Entity entity = entry.getKey();
            if (!entity.isValid() || entityMass(entity) <= 0.0d) continue;

            com.bulletphysics.dynamics.RigidBody body = entry.getValue();
            body.getMotionState().getWorldTransform(transform);
            body.getLinearVelocity(velocity);

            org.bukkit.util.Vector currentVelocity = entity.getVelocity();
            org.bukkit.util.Vector reactionVelocity = new org.bukkit.util.Vector(
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
            reactionVelocity.setY(Math.max(reactionVelocity.getY(), currentVelocity.getY()));
            entity.setVelocity(currentVelocity.multiply(1.0d - velocityFactor).add(reactionVelocity.multiply(velocityFactor)));
            syncEntityCollisionBody(entity, body);
        }
    }

    private boolean isBlockLocal(BoundingBox boundingBox) {
        return boundingBox.getMinX() >= -0.0001d && boundingBox.getMaxX() <= 1.0001d &&
                boundingBox.getMinY() >= -0.0001d && boundingBox.getMaxY() <= 1.0001d &&
                boundingBox.getMinZ() >= -0.0001d && boundingBox.getMaxZ() <= 1.0001d;
    }

    private Vector3f toBullet(Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    private Vector3f toBullet(Location location) {
        return new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    private Location toLocation(World world, Vector3f vector) {
        return new Location(world, vector.x, vector.y, vector.z);
    }

    private Quat4f toBullet(Quaterniond quaternion) {
        return new Quat4f((float) quaternion.x, (float) quaternion.y, (float) quaternion.z, (float) quaternion.w);
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    private double square(double value) {
        return value * value;
    }

    private boolean shouldSyncTerrain() {
        int interval = Math.max(1, getInt(PhysConfig.PHYSICS_TERRAIN_SYNC_INTERVAL));
        terrainSyncTicker++;
        if (terrainSyncTicker >= interval) {
            terrainSyncTicker = 0;
            return true;
        }
        return staticBodies.isEmpty();
    }

    private boolean shouldSyncEntities() {
        int interval = Math.max(1, getInt(PhysConfig.ENTITY_SCAN_INTERVAL));
        entitySyncTicker++;
        if (entitySyncTicker >= interval) {
            entitySyncTicker = 0;
            return true;
        }
        return cachedRequiredEntities.isEmpty();
    }

    private String terrainKey(World world, int x, int y, int z, int shapeIndex) {
        return world.getUID() + ":" + x + ":" + y + ":" + z + ":" + shapeIndex;
    }

    private String dynamicSettingsSignature() {
        return getDouble(PhysConfig.PHYSICS_STATIC_FRICTION) + ":" +
                getDouble(PhysConfig.PHYSICS_DYNAMIC_FRICTION) + ":" +
                getDouble(PhysConfig.PHYSICS_ROLLING_FRICTION) + ":" +
                getDouble(PhysConfig.PHYSICS_RESTITUTION) + ":" +
                getDouble(PhysConfig.PHYSICS_LINEAR_DAMPING) + ":" +
                getDouble(PhysConfig.PHYSICS_ANGULAR_DAMPING) + ":" +
                getDouble(PhysConfig.PHYSICS_LINEAR_SLEEPING_THRESHOLD) + ":" +
                getDouble(PhysConfig.PHYSICS_ANGULAR_SLEEPING_THRESHOLD);
    }

    private String staticSettingsSignature() {
        StringBuilder signature = new StringBuilder()
                .append(getDouble(PhysConfig.PHYSICS_STATIC_FRICTION)).append(":")
                .append(getDouble(PhysConfig.PHYSICS_DYNAMIC_FRICTION)).append(":")
                .append(getDouble(PhysConfig.PHYSICS_ROLLING_FRICTION)).append(":")
                .append(getDouble(PhysConfig.PHYSICS_STATIC_RESTITUTION)).append(":")
                .append(getDouble(PhysConfig.PHYSICS_STATIC_MARGIN));
        for (String key : PhysMC.getPlugin(PhysMC.class).getConfig().getKeys(true)) {
            if (PhysConfig.isMaterialFrictionKey(key)
                    || PhysConfig.isMaterialStaticFrictionKey(key)
                    || PhysConfig.isMaterialDynamicFrictionKey(key)
                    || PhysConfig.isMaterialRollingFrictionKey(key)
                    || PhysConfig.isMaterialRestitutionKey(key)) {
                signature.append(":").append(key).append("=").append(PhysMC.getPlugin(PhysMC.class).getConfig().get(key));
            }
        }
        return signature.toString();
    }

    private double getDouble(String path) {
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(path)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(path);
        }
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && PhysMC.getPlugin(PhysMC.class).getConfig().contains(legacyPath)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(legacyPath);
        }
        return ((Number) PhysConfig.get(path).defaultValue).doubleValue();
    }

    private int getInt(String path) {
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(path)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getInt(path);
        }
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && PhysMC.getPlugin(PhysMC.class).getConfig().contains(legacyPath)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getInt(legacyPath);
        }
        return ((Number) PhysConfig.get(path).defaultValue).intValue();
    }

    private boolean getBoolean(String path) {
        if (PhysMC.getPlugin(PhysMC.class).getConfig().contains(path)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getBoolean(path);
        }
        String legacyPath = PhysConfig.LEGACY_KEYS.get(path);
        if (legacyPath != null && PhysMC.getPlugin(PhysMC.class).getConfig().contains(legacyPath)) {
            return PhysMC.getPlugin(PhysMC.class).getConfig().getBoolean(legacyPath);
        }
        return (Boolean) PhysConfig.get(path).defaultValue;
    }

    private Vector3d vec(Vector3f value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private Matrix3d mat(javax.vecmath.Matrix3f value) {
        return new Matrix3d(
                value.m00, value.m01, value.m02,
                value.m10, value.m11, value.m12,
                value.m20, value.m21, value.m22
        );
    }

    private static class BodyPartitionState {
        private static final int ACTIVE_PRIORITY = 0;
        private static final int THROTTLED_ACTIVE_PRIORITY = 1;
        private static final int THROTTLED_SLEEP_PRIORITY = 2;
        private static final int SLEEPING_PRIORITY = 3;
        private static final int PAUSED_PRIORITY = 4;

        private final boolean simulate;
        private final int priority;

        private BodyPartitionState(boolean simulate, int priority) {
            this.simulate = simulate;
            this.priority = priority;
        }

        private static BodyPartitionState active() {
            return new BodyPartitionState(true, ACTIVE_PRIORITY);
        }

        private static BodyPartitionState throttled(boolean simulate) {
            return new BodyPartitionState(simulate, simulate ? THROTTLED_ACTIVE_PRIORITY : THROTTLED_SLEEP_PRIORITY);
        }

        private static BodyPartitionState sleeping() {
            return new BodyPartitionState(false, SLEEPING_PRIORITY);
        }

        private static BodyPartitionState paused() {
            return new BodyPartitionState(false, PAUSED_PRIORITY);
        }

        private static BodyPartitionState moreActive(BodyPartitionState first, BodyPartitionState second) {
            return first.priority <= second.priority ? first : second;
        }
    }

    private static class SuspendedMotion {
        private final Vector3f linearVelocity;
        private final Vector3f angularVelocity;

        private SuspendedMotion(Vector3f linearVelocity, Vector3f angularVelocity) {
            this.linearVelocity = new Vector3f(linearVelocity);
            this.angularVelocity = new Vector3f(angularVelocity);
        }
    }

    private static class BodyContactRef {
        private final ContactRefType type;
        private final Box box;
        private final Entity entity;
        private final World world;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final Material material;

        private BodyContactRef(ContactRefType type, Box box, Entity entity, World world, int blockX, int blockY, int blockZ, Material material) {
            this.type = type;
            this.box = box;
            this.entity = entity;
            this.world = world;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.material = material;
        }

        private static BodyContactRef rigidBody(Box box) {
            return new BodyContactRef(ContactRefType.RIGID, box, null, null, 0, 0, 0, Material.AIR);
        }

        private static BodyContactRef entity(Entity entity) {
            return new BodyContactRef(ContactRefType.ENTITY, null, entity, null, 0, 0, 0, Material.AIR);
        }

        private static BodyContactRef block(World world, int x, int y, int z, Material material) {
            return new BodyContactRef(ContactRefType.BLOCK, null, null, world, x, y, z, material);
        }

        private boolean isRigidBody() {
            return type == ContactRefType.RIGID && box != null;
        }

        private Block block(Box source) {
            World selectedWorld = world == null ? source.body.getAllDisplay().get(0).getWorld() : world;
            return selectedWorld.getBlockAt(blockX, blockY, blockZ);
        }
    }

    private enum ContactRefType {
        RIGID,
        BLOCK,
        ENTITY
    }

    private static class FluidSample {
        private final double surfaceY;
        private final float density;
        private final float linearDrag;
        private final float angularDrag;
        private final Vector3f velocity;
        private final int samples;
        private final Material fluidType;

        private FluidSample(double surfaceY, float density, float linearDrag, float angularDrag, Vector3f velocity, int samples, Material fluidType) {
            this.surfaceY = surfaceY;
            this.density = density;
            this.linearDrag = linearDrag;
            this.angularDrag = angularDrag;
            this.velocity = velocity;
            this.samples = samples;
            this.fluidType = fluidType;
        }

        private static FluidSample empty() {
            return new FluidSample(0, 0, 0, 0, new Vector3f(), 0, Material.AIR);
        }
    }

    private static class FluidAccumulator {
        private int samples;
        private double surfaceY;
        private float density;
        private float linearDrag;
        private float angularDrag;
        private final Vector3f velocity = new Vector3f();

        private void add(FluidSample sample) {
            samples += sample.samples;
            surfaceY += sample.surfaceY * sample.samples;
            density += sample.density * sample.samples;
            linearDrag += sample.linearDrag * sample.samples;
            angularDrag += sample.angularDrag * sample.samples;
            Vector3f weightedVelocity = new Vector3f(sample.velocity);
            weightedVelocity.scale(sample.samples);
            velocity.add(weightedVelocity);
        }

        private FluidSample toSample() {
            Vector3f averageVelocity = new Vector3f(velocity);
            averageVelocity.scale(1.0f / samples);
            return new FluidSample(
                    surfaceY / samples,
                    density / samples,
                    linearDrag / samples,
                    angularDrag / samples,
                    averageVelocity,
                    samples,
                    density / samples > 1500.0f ? Material.LAVA : Material.WATER
            );
        }
    }

    private static class BodyAnchor {
        private final com.bulletphysics.dynamics.RigidBody body;
        private final Vector3f localPivot;

        private BodyAnchor(com.bulletphysics.dynamics.RigidBody body, Vector3f localPivot) {
            this.body = body;
            this.localPivot = localPivot;
        }
    }

    private static class ConstraintBinding {
        private final int id;
        private final String type;
        private final TypedConstraint constraint;
        private final com.bulletphysics.dynamics.RigidBody first;
        private final com.bulletphysics.dynamics.RigidBody second;
        private final Vector3f firstPivot;
        private final Vector3f secondPivot;
        private final boolean firstIsWorldPoint;
        private final boolean secondIsWorldPoint;
        private final boolean preserveCenterDistance;
        private final float centerDistance;
        private final World world;
        private final float lowerLinear;
        private final float upperLinear;
        private final float lowerAngular;
        private final float upperAngular;
        private final float breakImpulse;

        private ConstraintBinding(int id, String type, TypedConstraint constraint, com.bulletphysics.dynamics.RigidBody first, com.bulletphysics.dynamics.RigidBody second,
                                  Vector3f firstPivot, Vector3f secondPivot, boolean firstIsWorldPoint, boolean secondIsWorldPoint,
                                  boolean preserveCenterDistance, float centerDistance, World world,
                                  float lowerLinear, float upperLinear, float lowerAngular, float upperAngular, float breakImpulse) {
            this.id = id;
            this.type = type;
            this.constraint = constraint;
            this.first = first;
            this.second = second;
            this.firstPivot = new Vector3f(firstPivot);
            this.secondPivot = new Vector3f(secondPivot);
            this.firstIsWorldPoint = firstIsWorldPoint;
            this.secondIsWorldPoint = secondIsWorldPoint;
            this.preserveCenterDistance = preserveCenterDistance;
            this.centerDistance = centerDistance;
            this.world = world;
            this.lowerLinear = lowerLinear;
            this.upperLinear = upperLinear;
            this.lowerAngular = lowerAngular;
            this.upperAngular = upperAngular;
            this.breakImpulse = breakImpulse;
        }
    }

    private static class DistanceConstraintBinding {
        private final int id;
        private final com.bulletphysics.dynamics.RigidBody first;
        private final com.bulletphysics.dynamics.RigidBody second;
        private final Vector3f firstPivot;
        private final Vector3f secondPivot;
        private final boolean firstIsWorldPoint;
        private final boolean secondIsWorldPoint;
        private final float restLength;
        private final World world;

        private DistanceConstraintBinding(int id, com.bulletphysics.dynamics.RigidBody first, com.bulletphysics.dynamics.RigidBody second,
                                          Vector3f firstPivot, Vector3f secondPivot, boolean firstIsWorldPoint, boolean secondIsWorldPoint,
                                          float restLength, World world) {
            this.id = id;
            this.first = first;
            this.second = second;
            this.firstPivot = new Vector3f(firstPivot);
            this.secondPivot = new Vector3f(secondPivot);
            this.firstIsWorldPoint = firstIsWorldPoint;
            this.secondIsWorldPoint = secondIsWorldPoint;
            this.restLength = restLength;
            this.world = world;
        }
    }

    private static class PendingConnectionRequest {
        private final Display firstDisplay;
        private final Entity firstEntity;
        private final Location firstPoint;
        private final Display secondDisplay;
        private final Entity secondEntity;
        private final Location secondPoint;
        private final boolean preserveCenterDistance;
        private int attempts;

        private PendingConnectionRequest(Display firstDisplay, Entity firstEntity, Location firstPoint,
                                         Display secondDisplay, Entity secondEntity, Location secondPoint, boolean preserveCenterDistance) {
            this.firstDisplay = firstDisplay;
            this.firstEntity = firstEntity;
            this.firstPoint = firstPoint.clone();
            this.secondDisplay = secondDisplay;
            this.secondEntity = secondEntity;
            this.secondPoint = secondPoint.clone();
            this.preserveCenterDistance = preserveCenterDistance;
        }

        private boolean isStillValid() {
            return isValid(firstDisplay) && isValid(secondDisplay) && isValid(firstEntity) && isValid(secondEntity);
        }

        private boolean isValid(Display display) {
            return display == null || (!display.isDead() && display.isValid());
        }

        private boolean isValid(Entity entity) {
            return entity == null || entity.isValid();
        }
    }

    private static class PendingTypedConstraintRequest {
        private final String type;
        private final Display firstDisplay;
        private final Entity firstEntity;
        private final Location firstPoint;
        private final Display secondDisplay;
        private final Entity secondEntity;
        private final Location secondPoint;
        private final double lowerLinear;
        private final double upperLinear;
        private final double lowerAngular;
        private final double upperAngular;
        private final double breakImpulse;
        private int attempts;

        private PendingTypedConstraintRequest(String type, Display firstDisplay, Entity firstEntity, Location firstPoint,
                                              Display secondDisplay, Entity secondEntity, Location secondPoint,
                                              double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
            this.type = type;
            this.firstDisplay = firstDisplay;
            this.firstEntity = firstEntity;
            this.firstPoint = firstPoint.clone();
            this.secondDisplay = secondDisplay;
            this.secondEntity = secondEntity;
            this.secondPoint = secondPoint.clone();
            this.lowerLinear = lowerLinear;
            this.upperLinear = upperLinear;
            this.lowerAngular = lowerAngular;
            this.upperAngular = upperAngular;
            this.breakImpulse = breakImpulse;
        }

        private boolean isStillValid() {
            return isValid(firstDisplay) && isValid(secondDisplay) && isValid(firstEntity) && isValid(secondEntity);
        }

        private boolean isValid(Display display) {
            return display == null || (!display.isDead() && display.isValid());
        }

        private boolean isValid(Entity entity) {
            return entity == null || entity.isValid();
        }
    }

    private static class SpringBinding {
        private final int id;
        private final Display firstDisplay;
        private final Display secondDisplay;
        private final float restLength;
        private final float stiffness;
        private final float damping;

        private SpringBinding(int id, Display firstDisplay, Display secondDisplay, float restLength, float stiffness, float damping) {
            this.id = id;
            this.firstDisplay = firstDisplay;
            this.secondDisplay = secondDisplay;
            this.restLength = restLength;
            this.stiffness = stiffness;
            this.damping = damping;
        }
    }

    public static class ConstraintSnapshot {
        public final int id;
        public final String type;
        public final Location first;
        public final Location second;

        private ConstraintSnapshot(int id, String type, Location first, Location second) {
            this.id = id;
            this.type = type;
            this.first = first;
            this.second = second;
        }
    }

    public static class ConstraintDetail {
        public final int id;
        public final String type;
        public final Display firstDisplay;
        public final Display secondDisplay;
        public final Location first;
        public final Location second;
        public final double restLength;
        public final double stiffness;
        public final double damping;
        public final double lowerLinear;
        public final double upperLinear;
        public final double lowerAngular;
        public final double upperAngular;
        public final double breakImpulse;

        private ConstraintDetail(int id, String type, Display firstDisplay, Display secondDisplay, Location first, Location second,
                                 double restLength, double stiffness, double damping,
                                 double lowerLinear, double upperLinear, double lowerAngular, double upperAngular, double breakImpulse) {
            this.id = id;
            this.type = type;
            this.firstDisplay = firstDisplay;
            this.secondDisplay = secondDisplay;
            this.first = first;
            this.second = second;
            this.restLength = restLength;
            this.stiffness = stiffness;
            this.damping = damping;
            this.lowerLinear = lowerLinear;
            this.upperLinear = upperLinear;
            this.lowerAngular = lowerAngular;
            this.upperAngular = upperAngular;
            this.breakImpulse = breakImpulse;
        }
    }

    public static class BulletDebugSnapshot {
        public final Vector3d worldPosition;
        public final Vector3d centerOfMassPosition;
        public final Vector3d interpolationPosition;
        public final Vector3d centerPosition;
        public final Quaterniond orientation;
        public final Vector3d linearVelocity;
        public final Vector3d angularVelocity;
        public final Vector3d interpolationLinearVelocity;
        public final Vector3d interpolationAngularVelocity;
        public final Vector3d gravity;
        public final double inverseMass;
        public final double linearDamping;
        public final double angularDamping;
        public final double linearSleepingThreshold;
        public final double angularSleepingThreshold;
        public final double friction;
        public final double restitution;
        public final int activationState;
        public final boolean active;
        public final boolean wantsSleeping;
        public final boolean inWorld;
        public final double angularFactor;
        public final int constraintRefs;
        public final int collisionFlags;
        public final int islandTag;
        public final int companionId;
        public final double hitFraction;
        public final double ccdSweptSphereRadius;
        public final double ccdMotionThreshold;
        public final String shapeName;
        public final String shapeType;
        public final double shapeMargin;
        public final Vector3d localScaling;
        public final Vector3d boundingSphereCenter;
        public final double boundingSphereRadius;
        public final Vector3d aabbMin;
        public final Vector3d aabbMax;
        public final Matrix3d inverseInertiaTensorWorld;
        public final Vector3d inverseInertiaDiagLocal;

        private BulletDebugSnapshot(Vector3d worldPosition, Vector3d centerOfMassPosition, Vector3d interpolationPosition, Vector3d centerPosition,
                                    Quaterniond orientation, Vector3d linearVelocity, Vector3d angularVelocity,
                                    Vector3d interpolationLinearVelocity, Vector3d interpolationAngularVelocity, Vector3d gravity,
                                    double inverseMass, double linearDamping, double angularDamping,
                                    double linearSleepingThreshold, double angularSleepingThreshold,
                                    double friction, double restitution, int activationState, boolean active, boolean wantsSleeping, boolean inWorld,
                                    double angularFactor, int constraintRefs, int collisionFlags, int islandTag, int companionId,
                                    double hitFraction, double ccdSweptSphereRadius, double ccdMotionThreshold,
                                    String shapeName, String shapeType, double shapeMargin, Vector3d localScaling,
                                    Vector3d boundingSphereCenter, double boundingSphereRadius, Vector3d aabbMin, Vector3d aabbMax,
                                    Matrix3d inverseInertiaTensorWorld, Vector3d inverseInertiaDiagLocal) {
            this.worldPosition = worldPosition;
            this.centerOfMassPosition = centerOfMassPosition;
            this.interpolationPosition = interpolationPosition;
            this.centerPosition = centerPosition;
            this.orientation = orientation;
            this.linearVelocity = linearVelocity;
            this.angularVelocity = angularVelocity;
            this.interpolationLinearVelocity = interpolationLinearVelocity;
            this.interpolationAngularVelocity = interpolationAngularVelocity;
            this.gravity = gravity;
            this.inverseMass = inverseMass;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.linearSleepingThreshold = linearSleepingThreshold;
            this.angularSleepingThreshold = angularSleepingThreshold;
            this.friction = friction;
            this.restitution = restitution;
            this.activationState = activationState;
            this.active = active;
            this.wantsSleeping = wantsSleeping;
            this.inWorld = inWorld;
            this.angularFactor = angularFactor;
            this.constraintRefs = constraintRefs;
            this.collisionFlags = collisionFlags;
            this.islandTag = islandTag;
            this.companionId = companionId;
            this.hitFraction = hitFraction;
            this.ccdSweptSphereRadius = ccdSweptSphereRadius;
            this.ccdMotionThreshold = ccdMotionThreshold;
            this.shapeName = shapeName;
            this.shapeType = shapeType;
            this.shapeMargin = shapeMargin;
            this.localScaling = localScaling;
            this.boundingSphereCenter = boundingSphereCenter;
            this.boundingSphereRadius = boundingSphereRadius;
            this.aabbMin = aabbMin;
            this.aabbMax = aabbMax;
            this.inverseInertiaTensorWorld = inverseInertiaTensorWorld;
            this.inverseInertiaDiagLocal = inverseInertiaDiagLocal;
        }
    }
}
