package com.bxwbb.event;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.phy.World;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DisplayHitListener implements Listener {

    public static final List<BlockDisplay> BLOCK_DISPLAYS = new ArrayList<>();
    private static final float MAX_REACH = 6.0F;
    private static final long ATTACK_DEDUPLICATE_MILLIS = 150L;
    private final Map<UUID, Long> lastSuccessfulAttack = new HashMap<>();

    @EventHandler
    public void onAttack(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        if (wasRecentlySuccessful(event.getPlayer())) return;

        applyPlayerAttack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) return;

        Player player = event.getPlayer();
        HitResult hit = raycastAttack(player);
        if (hit == null) return;

        if (wasRecentlySuccessful(player)) {
            event.setCancelled(true);
            return;
        }
        if (applyPlayerAttack(player, hit)) {
            event.setCancelled(true);
        }
    }

    private boolean applyPlayerAttack(Player player) {
        HitResult hit = raycastAttack(player);
        return hit != null && applyPlayerAttack(player, hit);
    }

    private boolean applyPlayerAttack(Player player, HitResult hit) {
        double impulse = getDouble(PhysConfig.GAME_FORCE_ATTACK_IMPULSE) * knockbackMultiplier(player);
        Vector impulseVector = attackDirection(player).multiply(impulse);
        if (World.getInstance().applyImpulse(hit.display, impulseVector, hit.location)) {
            lastSuccessfulAttack.put(player.getUniqueId(), System.currentTimeMillis());
            hit.location.getWorld().spawnParticle(Particle.CRIT, hit.location, 12, 0.12, 0.12, 0.12, 0.02);
            return true;
        }
        return false;
    }

    private HitResult raycastAttack(Player player) {
        return raycast(player.getEyeLocation(), player.getEyeLocation().getDirection(), MAX_REACH);
    }

    private Vector attackDirection(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        if (direction.getY() < -0.1d) {
            direction.setY(-0.1d);
            direction.normalize();
        }
        return direction;
    }

    private double knockbackMultiplier(Player player) {
        int level = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
        return level > 0 ? level : 1.0d;
    }

    private boolean wasRecentlySuccessful(Player player) {
        Long lastAttack = lastSuccessfulAttack.get(player.getUniqueId());
        return lastAttack != null && System.currentTimeMillis() - lastAttack < ATTACK_DEDUPLICATE_MILLIS;
    }

    private HitResult raycast(Location start, Vector direction, double maxDistance) {
        Vector3f rayStart = new Vector3f((float) start.getX(), (float) start.getY(), (float) start.getZ());
        Vector normalized = direction.clone().normalize();
        Vector3f rayDir = new Vector3f((float) normalized.getX(), (float) normalized.getY(), (float) normalized.getZ()).normalize();

        BlockDisplay hitDisplay = null;
        float closestDistance = (float) maxDistance;
        float[] distance = new float[1];

        for (BlockDisplay display : BLOCK_DISPLAYS) {
            if (display.isDead()) continue;
            if (!display.getWorld().equals(start.getWorld())) continue;
            if (rayOBBIntersect(rayStart, rayDir, getOBBWorldPoints(display), (float) maxDistance, distance) && distance[0] < closestDistance) {
                closestDistance = distance[0];
                hitDisplay = display;
            }
        }

        if (hitDisplay == null) return null;
        Vector hitPos = start.toVector().add(normalized.multiply(closestDistance));
        return new HitResult(hitDisplay, new Location(hitDisplay.getWorld(), hitPos.getX(), hitPos.getY(), hitPos.getZ()));
    }

    private static Vector3f[] getOBBWorldPoints(BlockDisplay display) {
        Transformation transformation = display.getTransformation();
        Matrix4f matrix = new Matrix4f()
                .translate((float) display.getX(), (float) display.getY(), (float) display.getZ())
                .translate(transformation.getTranslation())
                .rotate(transformation.getLeftRotation())
                .scale(transformation.getScale())
                .rotate(transformation.getRightRotation());

        Vector3f[] points = new Vector3f[8];
        points[0] = matrix.transformPosition(new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f());
        points[1] = matrix.transformPosition(new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f());
        points[2] = matrix.transformPosition(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());
        points[3] = matrix.transformPosition(new Vector3f(1.0f, 1.0f, 0.0f), new Vector3f());
        points[4] = matrix.transformPosition(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f());
        points[5] = matrix.transformPosition(new Vector3f(1.0f, 0.0f, 1.0f), new Vector3f());
        points[6] = matrix.transformPosition(new Vector3f(0.0f, 1.0f, 1.0f), new Vector3f());
        points[7] = matrix.transformPosition(new Vector3f(1.0f, 1.0f, 1.0f), new Vector3f());
        return points;
    }

    private static boolean rayOBBIntersect(Vector3f rayStart, Vector3f rayDir, Vector3f[] points, float maxDistance, float[] outDistance) {
        Vector3f center = new Vector3f();
        for (Vector3f point : points) center.add(point);
        center.mul(1.0f / 8.0f);

        Vector3f[] axes = {
                points[1].sub(points[0], new Vector3f()).normalize(),
                points[2].sub(points[0], new Vector3f()).normalize(),
                points[4].sub(points[0], new Vector3f()).normalize()
        };
        float[] halfSizes = {
                points[0].distance(points[1]) * 0.5f,
                points[0].distance(points[2]) * 0.5f,
                points[0].distance(points[4]) * 0.5f
        };

        Vector3f p = center.sub(rayStart, new Vector3f());
        float tMin = 0.0f;
        float tMax = maxDistance;

        for (int i = 0; i < 3; i++) {
            float e = axes[i].dot(p);
            float f = axes[i].dot(rayDir);
            if (Math.abs(f) < 1.0e-6f) {
                if (Math.abs(e) > halfSizes[i]) return false;
                continue;
            }

            float t1 = (e - halfSizes[i]) / f;
            float t2 = (e + halfSizes[i]) / f;
            if (t1 > t2) {
                float temp = t1;
                t1 = t2;
                t2 = temp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        outDistance[0] = tMin;
        return tMin >= 0.0f && tMin <= maxDistance;
    }

    private double getDouble(String path) {
        return PhysMC.getPlugin(PhysMC.class).getConfig().getDouble(path, ((Number) PhysConfig.get(path).defaultValue).doubleValue());
    }

    private static class HitResult {
        private final BlockDisplay display;
        private final Location location;

        private HitResult(BlockDisplay display, Location location) {
            this.display = display;
            this.location = location;
        }
    }
}
