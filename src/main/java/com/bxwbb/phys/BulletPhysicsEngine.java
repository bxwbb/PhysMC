package com.bxwbb.phys;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.obj.Box;
import com.bxwbb.obj.PhysBlockDisplay;
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
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;

public class BulletPhysicsEngine {

    private final DiscreteDynamicsWorld dynamicsWorld;
    private final Map<Box, com.bulletphysics.dynamics.RigidBody> dynamicBodies = new HashMap<>();
    private final Map<Entity, com.bulletphysics.dynamics.RigidBody> entityBodies = new HashMap<>();
    private final Map<String, com.bulletphysics.dynamics.RigidBody> staticBodies = new HashMap<>();
    private final Map<String, FluidSample> fluidSampleCache = new HashMap<>();
    private Set<Entity> cachedRequiredEntities = new HashSet<>();
    private String dynamicSettingsSignature = "";
    private String staticSettingsSignature = "";
    private int terrainSyncTicker = 0;
    private int entitySyncTicker = 0;

    public BulletPhysicsEngine() {
        DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
        CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
        DbvtBroadphase broadphase = new DbvtBroadphase();
        SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();
        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        applyGravity();
    }

    public void step(List<Box> boxes, double duration) {
        applyGravity();
        fluidSampleCache.clear();
        syncDynamicBodies(boxes);
        syncEntityBodies(boxes);
        applyDynamicSettingsIfNeeded();
        if (shouldSyncTerrain()) {
            syncStaticTerrain(boxes);
        }
        applyFluidForces(boxes);
        dynamicsWorld.stepSimulation(
                (float) duration,
                getInt(PhysConfig.PHYSICS_MAX_SUB_STEPS),
                (float) getDouble(PhysConfig.PHYSICS_FIXED_STEP)
        );
        syncDisplays();
        syncEntities();
    }

    public boolean applyImpulse(Display display, org.bukkit.util.Vector impulse, Location hitLocation) {
        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            Box box = entry.getKey();
            if (!box.body.getAllDisplay().contains(display)) continue;

            com.bulletphysics.dynamics.RigidBody body = entry.getValue();
            if (body.getInvMass() <= 0.0f) return false;

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

    private void wakeBody(com.bulletphysics.dynamics.RigidBody body) {
        body.forceActivationState(CollisionObject.ACTIVE_TAG);
        body.setDeactivationTime(0.0f);
        body.activate(true);
        dynamicsWorld.updateSingleAabb(body);
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
                dynamicsWorld.removeRigidBody(entry.getValue());
                iterator.remove();
            }
        }

        for (Box box : boxes) {
            if (!dynamicBodies.containsKey(box)) {
                com.bulletphysics.dynamics.RigidBody body = createDynamicBody(box);
                dynamicBodies.put(box, body);
                dynamicsWorld.addRigidBody(body);
            }
        }
    }

    private com.bulletphysics.dynamics.RigidBody createDynamicBody(Box box) {
        float mass = box.body.hasFiniteMass() ? (float) box.body.getMass() : 0.0f;
        CollisionShape shape = new BoxShape(toBullet(box.halfSize));
        shape.setMargin((float) getDouble(PhysConfig.PHYSICS_DYNAMIC_MARGIN));
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
        constructionInfo.friction = (float) getDouble(PhysConfig.PHYSICS_FRICTION);
        constructionInfo.restitution = (float) getDouble(PhysConfig.PHYSICS_RESTITUTION);
        constructionInfo.linearDamping = (float) getDouble(PhysConfig.PHYSICS_LINEAR_DAMPING);
        constructionInfo.angularDamping = (float) getDouble(PhysConfig.PHYSICS_ANGULAR_DAMPING);

        com.bulletphysics.dynamics.RigidBody rigidBody = new com.bulletphysics.dynamics.RigidBody(constructionInfo);
        rigidBody.setLinearVelocity(toBullet(box.body.velocity));
        rigidBody.setAngularVelocity(toBullet(box.body.rotation));
        applyDynamicSettings(rigidBody);
        return rigidBody;
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
        body.setFriction((float) getDouble(PhysConfig.PHYSICS_FRICTION));
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

    private void syncEntityBodies(List<Box> boxes) {
        Set<Entity> requiredEntities = shouldSyncEntities() ? collectNearbyEntities(boxes) : cachedRequiredEntities;
        cachedRequiredEntities = requiredEntities;
        Iterator<Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody>> iterator = entityBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody> entry = iterator.next();
            if (!entry.getKey().isValid() || !requiredEntities.contains(entry.getKey())) {
                dynamicsWorld.removeRigidBody(entry.getValue());
                iterator.remove();
            }
        }

        for (Entity entity : requiredEntities) {
            double mass = entityMass(entity);
            com.bulletphysics.dynamics.RigidBody existing = entityBodies.get(entity);
            if (existing != null && Math.abs(existing.getInvMass() - inverseMass(mass)) > 0.0001f) {
                dynamicsWorld.removeRigidBody(existing);
                entityBodies.remove(entity);
                existing = null;
            }

            if (existing == null) {
                com.bulletphysics.dynamics.RigidBody body = createEntityBody(entity, mass);
                entityBodies.put(entity, body);
                dynamicsWorld.addRigidBody(body);
            } else if (mass <= 0.0d) {
                syncStaticEntityBody(entity, existing);
            }
        }
    }

    private Set<Entity> collectNearbyEntities(List<Box> boxes) {
        Set<Entity> entities = new HashSet<>();
        if (!getBoolean(PhysConfig.ENTITY_COLLISION_ENABLED)) return entities;

        for (Box box : boxes) {
            if (!(box.body instanceof PhysBlockDisplay)) continue;

            Location location = ((PhysBlockDisplay) box.body).getBlockDisplay().getLocation();
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
        body.setFriction((float) getDouble(PhysConfig.PHYSICS_FRICTION));
        body.setRestitution((float) getDouble(PhysConfig.PHYSICS_RESTITUTION));
        body.setLinearVelocity(new Vector3f(
                (float) entity.getVelocity().getX(),
                (float) entity.getVelocity().getY(),
                (float) entity.getVelocity().getZ()
        ));
        return body;
    }

    private void syncStaticEntityBody(Entity entity, com.bulletphysics.dynamics.RigidBody body) {
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
        dynamicsWorld.updateSingleAabb(body);
        body.activate();
    }

    private void syncStaticTerrain(List<Box> boxes) {
        String settingsSignature = staticSettingsSignature();
        if (!settingsSignature.equals(staticSettingsSignature)) {
            clearStaticTerrain();
            staticSettingsSignature = settingsSignature;
        }

        Set<String> requiredShapes = new HashSet<>();
        for (Box box : boxes) {
            if (!(box.body instanceof PhysBlockDisplay)) continue;
            com.bulletphysics.dynamics.RigidBody rigidBody = dynamicBodies.get(box);
            if (!getBoolean(PhysConfig.PHYSICS_SCAN_SLEEPING_BODIES) && rigidBody != null && !rigidBody.isActive()) {
                continue;
            }

            Location origin = ((PhysBlockDisplay) box.body).getBlockDisplay().getLocation();
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
                                addStaticBlockShape(key, block.getLocation(), boundingBox);
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

    private void applyFluidForces(List<Box> boxes) {
        if (!getBoolean(PhysConfig.FLUID_BUOYANCY_ENABLED)) return;

        for (Box box : boxes) {
            com.bulletphysics.dynamics.RigidBody rigidBody = dynamicBodies.get(box);
            if (rigidBody == null || rigidBody.getInvMass() <= 0.0f) continue;
            if (!(box.body instanceof PhysBlockDisplay)) continue;

            FluidSample sample = sampleFluid(box);
            if (sample == null) continue;

            float submerged = submergedFraction(box, sample.surfaceY);
            if (submerged <= 0.0f) continue;

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

    private FluidSample sampleFluid(Box box) {
        World world = ((PhysBlockDisplay) box.body).getBlockDisplay().getWorld();
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

    private void addStaticBlockShape(String key, Location blockLocation, BoundingBox boundingBox) {
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
        body.setFriction((float) getDouble(PhysConfig.PHYSICS_FRICTION));
        body.setRestitution((float) getDouble(PhysConfig.PHYSICS_STATIC_RESTITUTION));
        dynamicsWorld.addRigidBody(body);
        staticBodies.put(key, body);
    }

    private void syncDisplays() {
        Transform transform = new Transform();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        Quat4f rotation = new Quat4f();

        for (Map.Entry<Box, com.bulletphysics.dynamics.RigidBody> entry : dynamicBodies.entrySet()) {
            Box box = entry.getKey();
            com.bulletphysics.dynamics.RigidBody rigidBody = entry.getValue();
            rigidBody.getMotionState().getWorldTransform(transform);
            rigidBody.getLinearVelocity(linearVelocity);
            rigidBody.getAngularVelocity(angularVelocity);
            transform.getRotation(rotation);

            box.body.position.set(transform.origin.x, transform.origin.y, transform.origin.z);
            box.body.velocity.set(linearVelocity.x, linearVelocity.y, linearVelocity.z);
            box.body.rotation.set(angularVelocity.x, angularVelocity.y, angularVelocity.z);
            box.body.orientation.set(rotation.x, rotation.y, rotation.z, rotation.w).normalize();
            box.body.tick();
        }
    }

    private void syncEntities() {
        Transform transform = new Transform();
        Vector3f velocity = new Vector3f();
        double positionFactor = clamp01(getDouble(PhysConfig.ENTITY_POSITION_SYNC_FACTOR));
        double velocityFactor = clamp01(getDouble(PhysConfig.ENTITY_VELOCITY_SYNC_FACTOR));

        for (Map.Entry<Entity, com.bulletphysics.dynamics.RigidBody> entry : entityBodies.entrySet()) {
            Entity entity = entry.getKey();
            if (!entity.isValid() || entityMass(entity) <= 0.0d) continue;

            com.bulletphysics.dynamics.RigidBody body = entry.getValue();
            body.getMotionState().getWorldTransform(transform);
            body.getLinearVelocity(velocity);

            BoundingBox box = entity.getBoundingBox();
            Location current = entity.getLocation();
            Location target = current.clone();
            target.setX(lerp(current.getX(), transform.origin.x, positionFactor));
            target.setY(lerp(current.getY(), transform.origin.y - box.getHeight() * 0.5d, positionFactor));
            target.setZ(lerp(current.getZ(), transform.origin.z, positionFactor));
            entity.teleport(target);
            entity.setVelocity(new org.bukkit.util.Vector(
                    velocity.x * velocityFactor,
                    velocity.y * velocityFactor,
                    velocity.z * velocityFactor
            ));
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

    private Quat4f toBullet(Quaterniond quaternion) {
        return new Quat4f((float) quaternion.x, (float) quaternion.y, (float) quaternion.z, (float) quaternion.w);
    }

    private int floor(double value) {
        return (int) Math.floor(value);
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
        return getDouble(PhysConfig.PHYSICS_FRICTION) + ":" +
                getDouble(PhysConfig.PHYSICS_RESTITUTION) + ":" +
                getDouble(PhysConfig.PHYSICS_LINEAR_DAMPING) + ":" +
                getDouble(PhysConfig.PHYSICS_ANGULAR_DAMPING) + ":" +
                getDouble(PhysConfig.PHYSICS_LINEAR_SLEEPING_THRESHOLD) + ":" +
                getDouble(PhysConfig.PHYSICS_ANGULAR_SLEEPING_THRESHOLD);
    }

    private String staticSettingsSignature() {
        return getDouble(PhysConfig.PHYSICS_FRICTION) + ":" +
                getDouble(PhysConfig.PHYSICS_STATIC_RESTITUTION) + ":" +
                getDouble(PhysConfig.PHYSICS_STATIC_MARGIN);
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
}
