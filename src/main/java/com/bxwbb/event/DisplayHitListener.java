package com.bxwbb.event;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class DisplayHitListener implements Listener {
    public static final List<BlockDisplay> BLOCK_DISPLAYS = new ArrayList<>();
    private static final float MAX_REACH = 6.0F;

    private static Vector3f[] getOBBWorldPoints(BlockDisplay display) {
        Transformation tf = display.getTransformation();
        Vector3f t = tf.getTranslation();
        org.joml.Quaternionf l = tf.getLeftRotation();
        org.joml.Quaternionf r = tf.getRightRotation();
        Vector3f s = tf.getScale();

        Matrix4f mat = new Matrix4f()
                .translate((float) display.getX(), (float) display.getY(), (float) display.getZ())
                .translate(t)
                .rotate(l)
                .scale(s)
                .rotate(r);

        Vector3f[] pts = new Vector3f[8];
        pts[0] = mat.transformPosition(new Vector3f(-0.5f, -0.5f, -0.5f), new Vector3f());
        pts[1] = mat.transformPosition(new Vector3f(0.5f, -0.5f, -0.5f), new Vector3f());
        pts[2] = mat.transformPosition(new Vector3f(-0.5f, 0.5f, -0.5f), new Vector3f());
        pts[3] = mat.transformPosition(new Vector3f(0.5f, 0.5f, -0.5f), new Vector3f());
        pts[4] = mat.transformPosition(new Vector3f(-0.5f, -0.5f, 0.5f), new Vector3f());
        pts[5] = mat.transformPosition(new Vector3f(0.5f, -0.5f, 0.5f), new Vector3f());
        pts[6] = mat.transformPosition(new Vector3f(-0.5f, 0.5f, 0.5f), new Vector3f());
        pts[7] = mat.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f());
        return pts;
    }

    // 射线OBB相交检测
    private static boolean rayOBBIntersect(Vector3f rayStart, Vector3f rayDir, Vector3f[] pts, float[] outT) {
        Vector3f center = new Vector3f();
        for (Vector3f v : pts) center.add(v);
        center.mul(1.0f / 8.0f);

        Vector3f u1 = pts[1].sub(pts[0], new Vector3f()).normalize();
        Vector3f u2 = pts[2].sub(pts[0], new Vector3f()).normalize();
        Vector3f u3 = pts[4].sub(pts[0], new Vector3f()).normalize();

        float hx = pts[0].distance(pts[1]) * 0.5f;
        float hy = pts[0].distance(pts[2]) * 0.5f;
        float hz = pts[0].distance(pts[4]) * 0.5f;

        Vector3f p = center.sub(rayStart, new Vector3f());
        float tMin = 0.0f;
        float tMax = MAX_REACH;

        for (int i = 0; i < 3; i++) {
            Vector3f axis;
            float h;
            if (i == 0) { axis = u1; h = hx; }
            else if (i == 1) { axis = u2; h = hy; }
            else { axis = u3; h = hz; }

            float e = axis.dot(p);
            float f = axis.dot(rayDir);

            if (Math.abs(f) < 1e-6f) continue;

            float t1 = (e - h) / f;
            float t2 = (e + h) / f;

            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);

            if (tMin > tMax) return false;
        }

        outT[0] = tMin;
        return tMin <= tMax && tMin >= 0.0f && tMin <= MAX_REACH;
    }

    @EventHandler
    public void onAttack(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = e.getPlayer();

        Location eyeLoc = player.getEyeLocation();
        Vector3f rayStart = new Vector3f((float) eyeLoc.getX(), (float) eyeLoc.getY(), (float) eyeLoc.getZ());
        Vector dir = eyeLoc.getDirection().normalize();
        Vector3f rayDir = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()).normalize();

        BlockDisplay hitDisplay = null;
        float closestDist = MAX_REACH;
        float[] tOut = new float[1];

        for (BlockDisplay display : BLOCK_DISPLAYS) {
            if (display.isDead()) continue;
            Vector3f[] points = getOBBWorldPoints(display);

            // 已删除drawOBB画线所有代码，不再生成任何线框

            if (rayOBBIntersect(rayStart, rayDir, points, tOut)) {
                if (tOut[0] < closestDist) {
                    closestDist = tOut[0];
                    hitDisplay = display;
                }
            }
        }

        if (hitDisplay != null) {
            Vector hitPos = eyeLoc.toVector().add(dir.clone().multiply(closestDist));
            Location hitLoc = new Location(hitDisplay.getWorld(), hitPos.getX(), hitPos.getY(), hitPos.getZ());
            hitLoc.getWorld().spawnParticle(Particle.CRIT, hitLoc, 20, 0.15, 0.15, 0.15, 0.02);
            player.sendMessage("§a✅ 成功击中展示实体");
        }
    }
}