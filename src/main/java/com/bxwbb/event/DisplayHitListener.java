package com.bxwbb.event;

import com.bxwbb.PhysConfig;
import com.bxwbb.PhysMC;
import com.bxwbb.constraint.ConstraintSelectionStore;
import com.bxwbb.obj.Box;
import com.bxwbb.phy.World;
import com.bxwbb.phys.BulletPhysicsEngine.ConstraintSnapshot;
import com.bxwbb.util.ObjectUtil;
import com.bxwbb.util.debug.LineDisplay;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DisplayHitListener implements Listener {

    public static final List<BlockDisplay> BLOCK_DISPLAYS = new ArrayList<>();
    private static final String PERMISSION_TOOL_GRAVITY = "physmc.tool.gravity";
    private static final String PERMISSION_TOOL_SELECTION = "physmc.tool.selection";
    private static final String PERMISSION_TOOL_CONNECTION = "physmc.tool.connection";
    private static final String PERMISSION_TOOL_CONSTRAINT = "physmc.tool.constraint";
    private static final String PERMISSION_TOOL_CUT = "physmc.tool.cut";
    private static final String PERMISSION_TOOL_ATTACK = "physmc.tool.attack";
    private static DisplayHitListener instance;
    private static boolean selectionToolEnabled = true;
    private static final float MAX_REACH = 6.0F;
    private static final long ATTACK_DEDUPLICATE_MILLIS = 150L;
    private static final long HOLD_DEDUPLICATE_MILLIS = 150L;
    private static final long CONNECTION_DEDUPLICATE_MILLIS = 150L;
    private static final long CUT_DEDUPLICATE_MILLIS = 150L;
    private final Map<UUID, Long> lastSuccessfulAttack = new HashMap<>();
    private final Map<UUID, Long> lastSuccessfulHoldClick = new HashMap<>();
    private final Map<UUID, Long> lastConnectionClick = new HashMap<>();
    private final Map<UUID, Long> lastCutClick = new HashMap<>();
    private final Map<UUID, HeldGroup> heldDisplays = new HashMap<>();
    private final Map<UUID, BlockDisplay> sightMarkers = new HashMap<>();
    private final Map<UUID, PendingConnection> pendingConnections = new HashMap<>();
    private final Map<UUID, PendingConnection> pendingConstraintSelections = new HashMap<>();
    private final Map<UUID, Map<Integer, ConstraintVisual>> constraintVisuals = new HashMap<>();
    private final Map<UUID, SelectionBox> selections = new HashMap<>();

    public DisplayHitListener(PhysMC plugin) {
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeldDisplays, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSightMarkers, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickConstraintVisuals, 1L, 1L);
    }

    public static boolean isSelectionToolEnabled() {
        return selectionToolEnabled;
    }

    public static void setSelectionToolEnabled(boolean enabled) {
        selectionToolEnabled = enabled;
        if (!enabled && instance != null) {
            instance.clearAllSelections();
        }
    }

    public static boolean clearSelection(Player player) {
        if (instance == null) return false;
        return instance.clearSelection(player.getUniqueId());
    }

    public static SelectionBounds selectionBounds(Player player) {
        if (instance == null) return null;
        SelectionBox selection = instance.selections.get(player.getUniqueId());
        if (selection == null || selection.first == null || selection.second == null) return null;
        if (!selection.first.getWorld().equals(selection.second.getWorld())) return null;
        return new SelectionBounds(selection.first, selection.second);
    }

    public static boolean holdDisplays(Player player, List<Display> displays) {
        if (instance == null || player == null || displays == null || displays.isEmpty()) return false;
        return instance.startHolding(player, displays);
    }

    @EventHandler
    public void onAttack(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        if (isSelectionInput(event.getPlayer())) {
            return;
        }
        if (isSightMarkerInput(event.getPlayer())) {
            handleConnectionClick(event.getPlayer());
            return;
        }
        if (isConstraintSelectionInput(event.getPlayer())) {
            handleConstraintSelectionClick(event.getPlayer());
            return;
        }
        if (isConstraintCutInput(event.getPlayer())) {
            handleConstraintCutClick(event.getPlayer());
            return;
        }
        if (isGravityGunInput(event.getPlayer())) {
            handleHoldInteractDeduplicated(event.getPlayer());
            return;
        }
        if (heldDisplays.containsKey(event.getPlayer().getUniqueId())) return;
        if (wasRecentlySuccessful(event.getPlayer())) return;
        if (!event.getPlayer().hasPermission(PERMISSION_TOOL_ATTACK)) return;

        applyPlayerAttack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (isSelectionInput(event.getPlayer())) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                handleSelectionClick(event);
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }
        if (isSightMarkerInput(event.getPlayer())) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                handleConnectionClick(event.getPlayer());
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }
        if (isConstraintSelectionInput(event.getPlayer())) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                handleConstraintSelectionClick(event.getPlayer());
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }
        if (isConstraintCutInput(event.getPlayer())) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                handleConstraintCutClick(event.getPlayer());
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }
        if ((event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && isGravityGunInput(event.getPlayer())
                && handleHoldInteractDeduplicated(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION_TOOL_ATTACK)) return;
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

    private boolean handleHoldInteractDeduplicated(Player player) {
        Long lastClick = lastSuccessfulHoldClick.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (lastClick != null && now - lastClick < HOLD_DEDUPLICATE_MILLIS) {
            return true;
        }
        if (!handleHoldInteract(player)) return false;
        lastSuccessfulHoldClick.put(player.getUniqueId(), now);
        return true;
    }

    private boolean handleHoldInteract(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) return false;

        HeldGroup held = heldDisplays.remove(player.getUniqueId());
        if (held != null) {
            releaseHeld(held);
            return true;
        }

        HitResult hit = raycastAttack(player);
        if (hit == null) return false;
        List<Display> group = player.isSneaking() ? groupDisplaysFor(hit.display) : ObjectUtil.groupFor(hit.display);
        HeldGroup heldGroup = HeldGroup.create(hit, group, player);
        if (!holdGroup(heldGroup, player)) return false;

        heldGroup.setGlowing(true);
        heldDisplays.put(player.getUniqueId(), heldGroup);
        return true;
    }

    private boolean startHolding(Player player, List<Display> displays) {
        HeldGroup previous = heldDisplays.remove(player.getUniqueId());
        if (previous != null) {
            releaseHeld(previous);
        }

        Display anchor = firstLiveDisplay(displays);
        if (!(anchor instanceof BlockDisplay)) return false;

        HeldGroup heldGroup = HeldGroup.create((BlockDisplay) anchor, displays, player);

        heldGroup.setGlowing(true);
        heldDisplays.put(player.getUniqueId(), heldGroup);
        lastSuccessfulHoldClick.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private Display firstLiveDisplay(List<Display> displays) {
        for (Display display : displays) {
            if (display != null && display.isValid() && !display.isDead()) {
                return display;
            }
        }
        return null;
    }

    private List<Display> groupDisplaysFor(Display hitDisplay) {
        Box hitBox = boxFor(hitDisplay);
        if (hitBox == null) return ObjectUtil.groupFor(hitDisplay);

        Optional<String> groupId = hitBox.getGroupId();
        if (groupId.isEmpty()) return ObjectUtil.groupFor(hitDisplay);

        List<Display> displays = new ArrayList<>();
        for (Box box : World.getInstance().boxes) {
            if (box.getGroupId().filter(groupId.get()::equals).isEmpty()) continue;
            Display display = primaryDisplay(box);
            if (display != null && !displays.contains(display)) {
                displays.add(display);
            }
        }
        return displays.isEmpty() ? ObjectUtil.groupFor(hitDisplay) : displays;
    }

    private Box boxFor(Display display) {
        for (Box box : World.getInstance().boxes) {
            if (box.body.getAllDisplay().contains(display)) {
                return box;
            }
        }
        return null;
    }

    private Display primaryDisplay(Box box) {
        for (Display display : box.body.getAllDisplay()) {
            if (display != null && display.isValid() && !display.isDead()) {
                return display;
            }
        }
        return null;
    }

    private boolean isGravityGunInput(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission(PERMISSION_TOOL_GRAVITY)
                && player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD;
    }

    private boolean isSightMarkerInput(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission(PERMISSION_TOOL_CONNECTION)
                && player.getInventory().getItemInMainHand().getType() == Material.CHAIN;
    }

    private boolean isConstraintSelectionInput(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission(PERMISSION_TOOL_CONSTRAINT)
                && player.getInventory().getItemInMainHand().getType() == Material.STICK;
    }

    private boolean isConstraintCutInput(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission(PERMISSION_TOOL_CUT)
                && player.getInventory().getItemInMainHand().getType() == Material.SHEARS;
    }

    private boolean isSelectionInput(Player player) {
        return selectionToolEnabled
                && player.hasPermission(PERMISSION_TOOL_SELECTION)
                && player.getInventory().getItemInMainHand().getType() == Material.NETHER_STAR;
    }

    private void handleSelectionClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        SelectionBox selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new SelectionBox());
        Location point = block.getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = point;
            player.sendMessage("已设置选区第一点: " + formatBlockPoint(point));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.second = point;
            player.sendMessage("已设置选区第二点: " + formatBlockPoint(point));
        }
        selection.update(player);
    }

    private String formatBlockPoint(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private void tickHeldDisplays() {
        heldDisplays.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            HeldGroup held = entry.getValue();
            if (player == null || !player.isOnline() || player.getGameMode() != GameMode.CREATIVE || held.isDead()) {
                releaseHeld(held);
                return true;
            }
            if (!holdGroup(held, player)) {
                releaseHeld(held);
                return true;
            }
            return false;
        });
    }

    private boolean holdGroup(HeldGroup held, Player player) {
        Location hitTarget = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(held.distance));
        Location anchorTarget = hitTarget.clone().subtract(held.hitOffset);
        boolean movedAny = false;
        for (HeldMember member : held.members) {
            Location target = anchorTarget.clone().add(member.offset);
            if (World.getInstance().holdAt(member.display, target)) {
                movedAny = true;
            }
        }
        return movedAny;
    }

    private void releaseHeld(HeldGroup held) {
        held.setGlowing(false);
        for (HeldMember member : held.members) {
            World.getInstance().release(member.display);
        }
    }

    private void tickSightMarkers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isSightMarkerInput(player) && !isConstraintSelectionInput(player)) {
                removeSightMarker(player.getUniqueId());
                clearPendingConnection(player.getUniqueId());
                clearPendingConstraintSelection(player.getUniqueId());
                continue;
            }
            clearConstraintVisuals(player.getUniqueId());

            SurfaceHit hit = raycastAnySurface(player.getEyeLocation(), player.getEyeLocation().getDirection(), MAX_REACH);
            if (hit == null) {
                removeSightMarker(player.getUniqueId());
                updatePreviewLine(player.getUniqueId(), null);
                continue;
            }

            BlockDisplay marker = sightMarkers.computeIfAbsent(player.getUniqueId(), ignored -> createSightMarker(player.getLocation()));
            marker.teleport(hit.location);
            updatePreviewLine(player.getUniqueId(), hit.location);
            updateConstraintSelectionPreviewLine(player.getUniqueId(), hit.location);
        }
    }

    private BlockDisplay createSightMarker(Location location) {
        BlockDisplay marker = location.getWorld().spawn(location, BlockDisplay.class);
        BlockData blockData = Bukkit.createBlockData(Material.RED_CONCRETE);
        float scale = 0.16f;
        marker.setBlock(blockData);
        marker.setTransformation(new Transformation(
                new Vector3f(-scale * 0.5f, -scale * 0.5f, -scale * 0.5f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
        marker.setShadowRadius(0.0f);
        marker.setShadowStrength(0.0f);
        marker.setBrightness(new Display.Brightness(15, 15));
        marker.setPersistent(false);
        return marker;
    }

    private void removeSightMarker(UUID playerId) {
        BlockDisplay marker = sightMarkers.remove(playerId);
        if (marker != null) {
            marker.remove();
        }
    }

    private void tickConstraintVisuals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isConstraintCutInput(player)) {
                clearConstraintVisuals(player.getUniqueId());
                continue;
            }
            removeSightMarker(player.getUniqueId());
            clearPendingConnection(player.getUniqueId());
            clearPendingConstraintSelection(player.getUniqueId());
            updateConstraintVisuals(player);
        }
    }

    private boolean clearSelection(UUID playerId) {
        SelectionBox selection = selections.remove(playerId);
        if (selection != null) {
            selection.remove();
            return true;
        }
        return false;
    }

    private void clearAllSelections() {
        for (SelectionBox selection : selections.values()) {
            selection.remove();
        }
        selections.clear();
    }

    private void updateConstraintVisuals(Player player) {
        UUID playerId = player.getUniqueId();
        Map<Integer, ConstraintVisual> visuals = constraintVisuals.computeIfAbsent(playerId, ignored -> new HashMap<>());
        List<Integer> liveIds = new ArrayList<>();
        for (ConstraintSnapshot constraint : World.getInstance().pointConstraints()) {
            if (!constraint.first.getWorld().equals(player.getWorld())) continue;
            liveIds.add(constraint.id);

            ConstraintVisual visual = visuals.get(constraint.id);
            if (visual == null || visual.isDead()) {
                visual = new ConstraintVisual(
                        constraint.id,
                        createConstraintPoint(constraint.first, constraint.type),
                        createConstraintPoint(constraint.second, constraint.type)
                );
                visuals.put(constraint.id, visual);
            }
            visual.update(constraint.first, constraint.second);
        }
        visuals.entrySet().removeIf(entry -> {
            if (liveIds.contains(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
    }

    private BlockDisplay createConstraintPoint(Location location, String type) {
        BlockDisplay point = location.getWorld().spawn(location, BlockDisplay.class);
        float scale = 0.18f;
        point.setBlock(Bukkit.createBlockData(constraintPointMaterial(type)));
        point.setTransformation(new Transformation(
                new Vector3f(-scale * 0.5f, -scale * 0.5f, -scale * 0.5f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
        point.setShadowRadius(0.0f);
        point.setShadowStrength(0.0f);
        point.setBrightness(new Display.Brightness(15, 15));
        point.setPersistent(false);
        return point;
    }

    private Material constraintPointMaterial(String type) {
        if ("distance".equals(type)) return Material.LIME_CONCRETE;
        if ("spring".equals(type)) return Material.PURPLE_CONCRETE;
        return Material.MAGENTA_CONCRETE;
    }

    private void clearConstraintVisuals(UUID playerId) {
        Map<Integer, ConstraintVisual> visuals = constraintVisuals.remove(playerId);
        if (visuals == null) return;
        for (ConstraintVisual visual : visuals.values()) {
            visual.remove();
        }
    }

    private boolean handleConstraintCutClick(Player player) {
        long now = System.currentTimeMillis();
        Long lastClick = lastCutClick.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < CUT_DEDUPLICATE_MILLIS) return true;

        ConstraintVisual hit = raycastConstraintVisual(player);
        if (hit == null) return false;
        lastCutClick.put(player.getUniqueId(), now);

        if (World.getInstance().removeConstraint(hit.constraintId)) {
            player.sendMessage("已删除约束。");
            clearConstraintVisuals(player.getUniqueId());
            updateConstraintVisuals(player);
        }
        return true;
    }

    private ConstraintVisual raycastConstraintVisual(Player player) {
        Map<Integer, ConstraintVisual> visuals = constraintVisuals.get(player.getUniqueId());
        if (visuals == null || visuals.isEmpty()) return null;

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        Vector3f rayStart = new Vector3f((float) start.getX(), (float) start.getY(), (float) start.getZ());
        Vector3f rayDir = new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).normalize();
        float closestDistance = MAX_REACH;
        float[] distance = new float[1];
        ConstraintVisual closest = null;

        for (ConstraintVisual visual : visuals.values()) {
            for (BlockDisplay display : visual.displays()) {
                if (display == null || display.isDead() || !display.getWorld().equals(player.getWorld())) continue;
                if (rayOBBIntersect(rayStart, rayDir, getOBBWorldPoints(display), MAX_REACH, distance) && distance[0] < closestDistance) {
                    closestDistance = distance[0];
                    closest = visual;
                }
            }
        }
        return closest;
    }

    private boolean handleConnectionClick(Player player) {
        long now = System.currentTimeMillis();
        Long lastClick = lastConnectionClick.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < CONNECTION_DEDUPLICATE_MILLIS) return true;
        SurfaceHit hit = raycastAnySurface(player.getEyeLocation(), player.getEyeLocation().getDirection(), MAX_REACH);
        if (hit == null) return false;
        lastConnectionClick.put(player.getUniqueId(), now);

        UUID playerId = player.getUniqueId();
        PendingConnection pending = pendingConnections.remove(playerId);
        if (pending == null) {
            pendingConnections.put(playerId, new PendingConnection(hit));
            return true;
        }

        clearPreviewLine(pending);
        removeSightMarker(playerId);
        if (!pending.hit.hasPhysicalTarget() && !hit.hasPhysicalTarget()) {
            player.sendMessage("两个点都不在物理物体上，无法连接。");
            return true;
        }
        if (!World.getInstance().connect(pending.hit.display, pending.hit.entity, pending.hit.location, hit.display, hit.entity, hit.location)) {
            player.sendMessage("连接失败：至少一个点必须位于物理刚体上。");
        }
        return true;
    }

    private boolean handleConstraintSelectionClick(Player player) {
        long now = System.currentTimeMillis();
        Long lastClick = lastConnectionClick.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < CONNECTION_DEDUPLICATE_MILLIS) return true;
        SurfaceHit hit = raycastAnySurface(player.getEyeLocation(), player.getEyeLocation().getDirection(), MAX_REACH);
        if (hit == null) return false;
        lastConnectionClick.put(player.getUniqueId(), now);

        UUID playerId = player.getUniqueId();
        PendingConnection pending = pendingConstraintSelections.remove(playerId);
        if (pending == null) {
            pendingConstraintSelections.put(playerId, new PendingConnection(hit));
            player.sendMessage("已选择第一个约束点。");
            return true;
        }

        clearPreviewLine(pending);
        removeSightMarker(playerId);
        if (!pending.hit.hasPhysicalTarget() && !hit.hasPhysicalTarget()) {
            player.sendMessage("两个点都不在物理物体上，无法作为约束选择。");
            return true;
        }
        ConstraintSelectionStore.set(
                player,
                new ConstraintSelectionStore.Anchor(pending.hit.display, pending.hit.entity, pending.hit.location),
                new ConstraintSelectionStore.Anchor(hit.display, hit.entity, hit.location)
        );
        player.sendMessage("已保存约束选择。使用 /physmc constraint create <point|distance|spring> 创建约束。");
        return true;
    }

    private void updatePreviewLine(UUID playerId, Location current) {
        PendingConnection pending = pendingConnections.get(playerId);
        if (pending == null) return;
        if (current == null || !current.getWorld().equals(pending.hit.location.getWorld())) {
            clearPreviewLine(pending);
            return;
        }
        if (pending.previewLine != null && !pending.previewLine.isDead()) {
            if (SpawnUtil.updateSegment(pending.previewLine, pending.hit.location, current, 0.04f)) {
                return;
            }
            pending.previewLine.remove();
            pending.previewLine = null;
        }
        pending.previewLine = SpawnUtil.spawnSegment(pending.hit.location, current, 0.04f);
        if (pending.previewLine != null) {
            pending.previewLine.setBlock(Bukkit.createBlockData(Material.RED_CONCRETE));
        }
    }

    private void clearPendingConnection(UUID playerId) {
        PendingConnection pending = pendingConnections.remove(playerId);
        if (pending != null) {
            clearPreviewLine(pending);
        }
    }

    private void updateConstraintSelectionPreviewLine(UUID playerId, Location current) {
        PendingConnection pending = pendingConstraintSelections.get(playerId);
        if (pending == null) return;
        if (current == null || !current.getWorld().equals(pending.hit.location.getWorld())) {
            clearPreviewLine(pending);
            return;
        }
        if (pending.previewLine != null && !pending.previewLine.isDead()) {
            if (SpawnUtil.updateSegment(pending.previewLine, pending.hit.location, current, 0.04f)) {
                return;
            }
            pending.previewLine.remove();
            pending.previewLine = null;
        }
        pending.previewLine = SpawnUtil.spawnSegment(pending.hit.location, current, 0.04f);
        if (pending.previewLine != null) {
            pending.previewLine.setBlock(Bukkit.createBlockData(Material.YELLOW_CONCRETE));
        }
    }

    private void clearPendingConstraintSelection(UUID playerId) {
        PendingConnection pending = pendingConstraintSelections.remove(playerId);
        if (pending != null) {
            clearPreviewLine(pending);
        }
    }

    private void clearPreviewLine(PendingConnection pending) {
        if (pending.previewLine != null && !pending.previewLine.isDead()) {
            pending.previewLine.remove();
        }
        pending.previewLine = null;
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

    private SurfaceHit raycastAnySurface(Location start, Vector direction, double maxDistance) {
        Vector normalized = direction.clone().normalize();
        SurfaceHit closest = null;
        double closestDistance = maxDistance;

        RayTraceResult blockHit = start.getWorld().rayTraceBlocks(start, normalized, maxDistance, FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition() != null) {
            Location location = blockHit.getHitPosition().toLocation(start.getWorld());
                closest = new SurfaceHit(location, null, null);
            closestDistance = location.distance(start);
        }

        SurfaceHit entityHit = raycastEntities(start, normalized, closestDistance);
        if (entityHit != null) {
            closest = entityHit;
            closestDistance = closest.location.distance(start);
        }

        HitResult displayHit = raycastDisplays(start, normalized, closestDistance);
        if (displayHit != null) {
            closest = new SurfaceHit(displayHit.location, displayHit.display, null);
        }

        return closest;
    }

    private SurfaceHit raycastEntities(Location start, Vector direction, double maxDistance) {
        SurfaceHit closest = null;
        double closestDistance = maxDistance;
        for (Entity entity : start.getWorld().getNearbyEntities(start, maxDistance, maxDistance, maxDistance)) {
            if (entity instanceof Player || entity instanceof Display) continue;

            BoundingBox box = entity.getBoundingBox();
            RayTraceResult result = box.rayTrace(start.toVector(), direction, maxDistance);
            if (result == null || result.getHitPosition() == null) continue;

            Location hit = result.getHitPosition().toLocation(start.getWorld());
            double distance = hit.distance(start);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = new SurfaceHit(hit, null, entity);
            }
        }
        return closest;
    }

    private HitResult raycastDisplays(Location start, Vector direction, double maxDistance) {
        Vector3f rayStart = new Vector3f((float) start.getX(), (float) start.getY(), (float) start.getZ());
        Vector3f rayDir = new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).normalize();

        BlockDisplay hitDisplay = null;
        float closestDistance = (float) maxDistance;
        float[] distance = new float[1];

        for (Display display : SpawnUtil.displays) {
            if (!(display instanceof BlockDisplay)) continue;
            BlockDisplay blockDisplay = (BlockDisplay) display;
            if (isSightMarker(blockDisplay)) continue;
            if (blockDisplay.isDead()) continue;
            if (!blockDisplay.getWorld().equals(start.getWorld())) continue;
            if (rayOBBIntersect(rayStart, rayDir, getOBBWorldPoints(blockDisplay), (float) maxDistance, distance) && distance[0] < closestDistance) {
                closestDistance = distance[0];
                hitDisplay = blockDisplay;
            }
        }

        for (BlockDisplay display : BLOCK_DISPLAYS) {
            if (isSightMarker(display)) continue;
            if (display.isDead()) continue;
            if (!display.getWorld().equals(start.getWorld())) continue;
            if (rayOBBIntersect(rayStart, rayDir, getOBBWorldPoints(display), (float) maxDistance, distance) && distance[0] < closestDistance) {
                closestDistance = distance[0];
                hitDisplay = display;
            }
        }

        if (hitDisplay == null) return null;
        Vector hitPos = start.toVector().add(direction.clone().multiply(closestDistance));
        return new HitResult(hitDisplay, new Location(hitDisplay.getWorld(), hitPos.getX(), hitPos.getY(), hitPos.getZ()));
    }

    private boolean isSightMarker(BlockDisplay display) {
        return sightMarkers.containsValue(display);
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

    private static class HeldGroup {
        private final List<HeldMember> members;
        private final double distance;
        private final Vector hitOffset;

        private HeldGroup(List<HeldMember> members, double distance, Vector hitOffset) {
            this.members = members;
            this.distance = distance;
            this.hitOffset = hitOffset;
        }

        private static HeldGroup create(BlockDisplay anchor, List<Display> displays, Player player) {
            return create(new HitResult(anchor, anchor.getLocation()), displays, player);
        }

        private static HeldGroup create(HitResult hit, List<Display> displays, Player player) {
            List<HeldMember> members = new ArrayList<>();
            BlockDisplay anchor = hit.display;
            Box anchorBox = boxForDisplay(anchor);
            Location anchorLocation = anchorBox == null ? anchor.getLocation() : bodyLocation(anchorBox, anchor.getWorld());
            if (anchorBox != null) {
                for (Box box : World.getInstance().boxes) {
                    if (!containsAnyDisplay(displays, box)) continue;
                    BlockDisplay primary = primaryBlockDisplay(box);
                    if (primary == null || primary.isDead() || !primary.getWorld().equals(anchor.getWorld())) continue;
                    members.add(new HeldMember(primary, bodyLocation(box, anchor.getWorld()).toVector().subtract(anchorLocation.toVector())));
                }
            } else {
                for (Display display : displays) {
                    if (!(display instanceof BlockDisplay) || display.isDead() || !display.getWorld().equals(anchor.getWorld())) continue;
                    members.add(new HeldMember((BlockDisplay) display, display.getLocation().toVector().subtract(anchorLocation.toVector())));
                }
            }
            if (members.isEmpty()) {
                members.add(new HeldMember(anchor, new Vector()));
            }
            double distance = Math.max(0.1d, player.getEyeLocation().distance(hit.location));
            Vector hitOffset = hit.location.toVector().subtract(anchorLocation.toVector());
            return new HeldGroup(members, distance, hitOffset);
        }

        private static boolean containsAnyDisplay(List<Display> displays, Box box) {
            for (Display display : box.body.getAllDisplay()) {
                if (displays.contains(display)) return true;
            }
            return false;
        }

        private static Box boxForDisplay(Display display) {
            for (Box box : World.getInstance().boxes) {
                if (box.body.getAllDisplay().contains(display)) return box;
            }
            return null;
        }

        private static BlockDisplay primaryBlockDisplay(Box box) {
            for (Display display : box.body.getAllDisplay()) {
                if (display instanceof BlockDisplay && display.isValid() && !display.isDead()) {
                    return (BlockDisplay) display;
                }
            }
            return null;
        }

        private static Location bodyLocation(Box box, org.bukkit.World world) {
            return new Location(world, box.body.position.x, box.body.position.y, box.body.position.z);
        }

        private boolean isDead() {
            for (HeldMember member : members) {
                if (!member.display.isDead()) return false;
            }
            return true;
        }

        private void setGlowing(boolean glowing) {
            for (HeldMember member : members) {
                if (!member.display.isDead()) {
                    member.display.setGlowing(glowing);
                }
            }
        }
    }

    private static class HeldMember {
        private final BlockDisplay display;
        private final Vector offset;

        private HeldMember(BlockDisplay display, Vector offset) {
            this.display = display;
            this.offset = offset;
        }
    }

    private static class SurfaceHit {
        private final Location location;
        private final BlockDisplay display;
        private final Entity entity;

        private SurfaceHit(Location location, BlockDisplay display, Entity entity) {
            this.location = location;
            this.display = display;
            this.entity = entity;
        }

        private boolean hasPhysicalTarget() {
            return display != null || entity != null;
        }
    }

    private static class PendingConnection {
        private final SurfaceHit hit;
        private BlockDisplay previewLine;

        private PendingConnection(SurfaceHit hit) {
            this.hit = hit;
        }
    }

    private static class SelectionBox {
        private Location first;
        private Location second;
        private final List<LineDisplay> lines = new ArrayList<>();

        private void update(Player player) {
            if (first == null || second == null) return;
            if (!first.getWorld().equals(second.getWorld())) {
                remove();
                player.sendMessage("两个选区点必须在同一世界。");
                return;
            }

            Location min = new Location(
                    first.getWorld(),
                    Math.min(first.getBlockX(), second.getBlockX()),
                    Math.min(first.getBlockY(), second.getBlockY()),
                    Math.min(first.getBlockZ(), second.getBlockZ())
            );
            Location max = new Location(
                    first.getWorld(),
                    Math.max(first.getBlockX(), second.getBlockX()) + 1.0d,
                    Math.max(first.getBlockY(), second.getBlockY()) + 1.0d,
                    Math.max(first.getBlockZ(), second.getBlockZ()) + 1.0d
            );

            Location[] corners = corners(min, max);
            int[][] edges = {
                    {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                    {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
            };
            if (lines.isEmpty()) {
                for (int[] edge : edges) {
                    lines.add(new LineDisplay(corners[edge[0]].clone(), corners[edge[1]].clone(), Material.LIME_CONCRETE));
                }
                return;
            }
            for (int i = 0; i < edges.length; i++) {
                int[] edge = edges[i];
                lines.get(i).setSegment(corners[edge[0]].clone(), corners[edge[1]].clone());
            }
        }

        private void remove() {
            for (LineDisplay line : lines) {
                for (Display display : line.getAllDisplay()) {
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                }
            }
            lines.clear();
        }

        private static Location[] corners(Location min, Location max) {
            org.bukkit.World world = min.getWorld();
            return new Location[]{
                    new Location(world, min.getX(), min.getY(), min.getZ()),
                    new Location(world, max.getX(), min.getY(), min.getZ()),
                    new Location(world, min.getX(), max.getY(), min.getZ()),
                    new Location(world, max.getX(), max.getY(), min.getZ()),
                    new Location(world, min.getX(), min.getY(), max.getZ()),
                    new Location(world, max.getX(), min.getY(), max.getZ()),
                    new Location(world, min.getX(), max.getY(), max.getZ()),
                    new Location(world, max.getX(), max.getY(), max.getZ())
            };
        }
    }

    public static class SelectionBounds {
        public final org.bukkit.World world;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;

        private SelectionBounds(Location first, Location second) {
            this.world = first.getWorld();
            this.minX = Math.min(first.getBlockX(), second.getBlockX());
            this.minY = Math.min(first.getBlockY(), second.getBlockY());
            this.minZ = Math.min(first.getBlockZ(), second.getBlockZ());
            this.maxX = Math.max(first.getBlockX(), second.getBlockX());
            this.maxY = Math.max(first.getBlockY(), second.getBlockY());
            this.maxZ = Math.max(first.getBlockZ(), second.getBlockZ());
        }

        public int volume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private static class ConstraintVisual {
        private final int constraintId;
        private final BlockDisplay firstPoint;
        private final BlockDisplay secondPoint;

        private ConstraintVisual(int constraintId, BlockDisplay firstPoint, BlockDisplay secondPoint) {
            this.constraintId = constraintId;
            this.firstPoint = firstPoint;
            this.secondPoint = secondPoint;
        }

        private void update(Location first, Location second) {
            firstPoint.teleport(first);
            secondPoint.teleport(second);
        }

        private boolean isDead() {
            return firstPoint.isDead() || secondPoint.isDead();
        }

        private List<BlockDisplay> displays() {
            List<BlockDisplay> displays = new ArrayList<>();
            displays.add(firstPoint);
            displays.add(secondPoint);
            return displays;
        }

        private void remove() {
            for (BlockDisplay display : displays()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
    }
}
