package com.bxwbb.physmc.api.internal;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.physmc.api.DistanceConstraintRequest;
import com.bxwbb.physmc.api.PhysBlockRequest;
import com.bxwbb.physmc.api.PhysBody;
import com.bxwbb.physmc.api.PhysWorld;
import com.bxwbb.physmc.api.PointConstraintRequest;
import com.bxwbb.physmc.api.TypedConstraintRequest;
import com.bxwbb.obj.Box;
import com.bxwbb.obj.PhysBlockDisplay;
import com.bxwbb.phy.World;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.ObjectUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 将公开 API 调用适配到当前插件内部实现。
 */
public final class PhysWorldAdapter implements PhysWorld {

    private final PhysMC plugin;
    private final World world = World.getInstance();
    private final PhysManager manager = PhysManager.getInstance();

    public PhysWorldAdapter(PhysMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<PhysBody> getBodies() {
        return new ArrayList<>(world.boxes);
    }

    @Override
    public Optional<PhysBody> findBody(String groupId) {
        if (groupId == null) {
            return Optional.empty();
        }
        for (Box box : world.boxes) {
            if (box.getGroupId().filter(groupId::equals).isPresent()) {
                return Optional.of(box);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<PhysBody> findBody(String groupId, String name) {
        if (groupId == null || name == null) {
            return Optional.empty();
        }
        for (Box box : world.boxes) {
            if (box.getGroupId().filter(groupId::equals).isPresent()
                    && box.getName().filter(name::equals).isPresent()) {
                return Optional.of(box);
            }
        }
        return Optional.empty();
    }

    @Override
    public PhysBody spawnBlock(PhysBlockRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        PhysBlockDisplay body = new PhysBlockDisplay(request.getLocation());
        body.getBlockDisplay().setBlock(request.getMaterial().createBlockData());

        Vector3d size = request.getSize();
        Transformation transformation = body.getBlockDisplay().getTransformation();
        transformation.getScale().set((float) size.x, (float) size.y, (float) size.z);
        body.getBlockDisplay().setTransformation(transformation);

        body.position.set(request.getLocation().getX(), request.getLocation().getY(), request.getLocation().getZ());
        body.velocity.set(request.getVelocity());
        body.rotation.set(request.getAngularVelocity());
        body.inverseMass = request.getMass() == 0.0d ? 0.0d : 1.0d / request.getMass();
        body.setAwake(true);
        body.tick();
        body.addWithName(request.getGroupId(), request.getName());

        Box box = new Box(body, new Vector3d(size).mul(0.5d));
        world.boxes.add(box);
        return box;
    }

    @Override
    public boolean removeGroup(String groupId) {
        return remove(groupId, null);
    }

    @Override
    public boolean remove(String groupId, String name) {
        if (groupId == null) {
            return false;
        }
        int before = trackedDisplayCount(groupId, name);
        ObjectUtil.removeDisplay(groupId, name);
        world.boxes.removeIf(box -> {
            for (Display display : box.body.getAllDisplay()) {
                if (!display.isDead()) {
                    return false;
                }
            }
            return true;
        });
        return before > 0;
    }

    @Override
    public boolean applyImpulse(Display display, Vector impulse, Location hitLocation) {
        return world.applyImpulse(display, impulse, hitLocation);
    }

    @Override
    public boolean hold(Display display, Player player, double distance) {
        return world.hold(display, player, distance);
    }

    @Override
    public boolean holdAt(Display display, Location target) {
        return world.holdAt(display, target);
    }

    @Override
    public void release(Display display) {
        world.release(display);
    }

    @Override
    public boolean connect(PointConstraintRequest request) {
        PhysWorld.Anchor first = request.getFirst();
        PhysWorld.Anchor second = request.getSecond();
        return world.connect(
                display(first),
                entity(first),
                point(first),
                display(second),
                entity(second),
                point(second),
                request.isPreserveCenterDistance()
        );
    }

    @Override
    public boolean addDistanceConstraint(DistanceConstraintRequest request) {
        PhysWorld.Anchor first = request.getFirst();
        PhysWorld.Anchor second = request.getSecond();
        return world.addDistanceConstraint(
                display(first),
                entity(first),
                point(first),
                display(second),
                entity(second),
                point(second),
                request.getRestLength()
        );
    }

    @Override
    public void addSpring(Display firstDisplay, Display secondDisplay, double restLength, double stiffness, double damping) {
        world.addSpring(firstDisplay, secondDisplay, restLength, stiffness, damping);
    }

    @Override
    public boolean addTypedConstraint(TypedConstraintRequest request) {
        PhysWorld.Anchor first = request.getFirst();
        PhysWorld.Anchor second = request.getSecond();
        return world.addTypedConstraint(
                request.getType(),
                display(first),
                entity(first),
                point(first),
                display(second),
                entity(second),
                point(second),
                request.getLowerLinear(),
                request.getUpperLinear(),
                request.getLowerAngular(),
                request.getUpperAngular(),
                request.getBreakImpulse()
        );
    }

    @Override
    public int savePersistentGroup(String groupId) {
        return plugin.persistentGroupStore().saveGroup(groupId);
    }

    @Override
    public boolean removePersistentGroup(String groupId) {
        return plugin.persistentGroupStore().removeGroup(groupId);
    }

    @Override
    public double getMaterialFriction(Material material) {
        if (material == null) {
            return plugin.getConfig().getDouble(PhysConfig.PHYSICS_FRICTION);
        }
        String key = PhysConfig.MATERIAL_FRICTION_PREFIX + material.name();
        return plugin.getConfig().contains(key)
                ? plugin.getConfig().getDouble(key)
                : plugin.getConfig().getDouble(PhysConfig.PHYSICS_FRICTION);
    }

    @Override
    public boolean isPaused() {
        return manager.isPaused();
    }

    @Override
    public void pause() {
        manager.pause();
    }

    @Override
    public void resume() {
        manager.resume();
    }

    @Override
    public int stepTicks(int ticks) {
        return manager.stepTicks(ticks);
    }

    @Override
    public int runFast(double seconds) {
        return manager.runFast(seconds);
    }

    private int trackedDisplayCount(String groupId, String name) {
        if (name == null) {
            List<Display> displays = ObjectUtil.displays.get(groupId);
            return displays == null ? 0 : displays.size();
        }
        List<Display> displays = ObjectUtil.namedDisplays(groupId, name);
        return displays == null ? 0 : displays.size();
    }

    private Display display(PhysWorld.Anchor anchor) {
        return anchor.getDisplay().orElse(null);
    }

    private Entity entity(PhysWorld.Anchor anchor) {
        return anchor.getEntity().orElse(null);
    }

    private Location point(PhysWorld.Anchor anchor) {
        return anchor.getPoint().orElse(null);
    }
}
