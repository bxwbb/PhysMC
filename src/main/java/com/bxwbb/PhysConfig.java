package com.bxwbb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PhysConfig {

    public static final String PHYSICS_GRAVITY_X = "物理.重力_X";
    public static final String PHYSICS_GRAVITY_Y = "物理.重力_Y";
    public static final String PHYSICS_GRAVITY_Z = "物理.重力_Z";
    public static final String PHYSICS_FIXED_STEP = "物理.固定步长";
    public static final String PHYSICS_MAX_SUB_STEPS = "物理.最大子步数";
    public static final String PHYSICS_TERRAIN_SCAN_MARGIN = "物理.地形扫描边距";
    public static final String PHYSICS_TERRAIN_SYNC_INTERVAL = "物理.地形同步间隔";
    public static final String PHYSICS_SCAN_SLEEPING_BODIES = "物理.扫描休眠刚体";
    public static final String PERFORMANCE_PARTITION_ENABLED = "性能.启用分区";
    public static final String PERFORMANCE_ACTIVE_RADIUS = "性能.完整模拟半径";
    public static final String PERFORMANCE_THROTTLE_RADIUS = "性能.降频模拟半径";
    public static final String PERFORMANCE_SLEEP_RADIUS = "性能.休眠半径";
    public static final String PERFORMANCE_THROTTLE_INTERVAL = "性能.降频模拟间隔";
    public static final String PERFORMANCE_CELL_SIZE = "性能.分区单元大小";
    public static final String PERFORMANCE_PAUSE_UNLOADED_CHUNKS = "性能.暂停未加载区块";
    public static final String PERFORMANCE_SELECTION_BUILD_BLOCKS_PER_TICK = "性能.选区构建每刻方块数";
    public static final String PERFORMANCE_SELECTION_BUILD_CULL_INTERNAL = "性能.选区构建剔除内部方块";
    public static final String PHYSICS_DYNAMIC_MARGIN = "物理.动态碰撞边距";
    public static final String PHYSICS_STATIC_MARGIN = "物理.静态碰撞边距";
    public static final String PHYSICS_FRICTION = "物理.摩擦系数";
    public static final String PHYSICS_RESTITUTION = "物理.反弹系数";
    public static final String PHYSICS_STATIC_RESTITUTION = "物理.静态反弹系数";
    public static final String PHYSICS_STATIC_FRICTION = "物理.静摩擦系数";
    public static final String PHYSICS_DYNAMIC_FRICTION = "物理.动摩擦系数";
    public static final String PHYSICS_ROLLING_FRICTION = "物理.滚动摩擦系数";
    public static final String PHYSICS_LINEAR_DAMPING = "物理.线性阻尼";
    public static final String PHYSICS_ANGULAR_DAMPING = "物理.角阻尼";
    public static final String PHYSICS_LINEAR_SLEEPING_THRESHOLD = "物理.线性休眠阈值";
    public static final String PHYSICS_ANGULAR_SLEEPING_THRESHOLD = "物理.角休眠阈值";
    public static final String PHYSICS_FIXED_CONSTRAINT_BREAK_IMPULSE = "物理.固定约束断裂冲量";
    public static final String DISPLAY_INTERPOLATION_DELAY = "显示.插值延迟";
    public static final String DISPLAY_INTERPOLATION_DURATION = "显示.插值时长";
    public static final String DISPLAY_TELEPORT_DURATION = "显示.传送时长";
    public static final String DISPLAY_SPHERE_DETAIL = "显示.球体精细度";
    public static final String FLUID_BUOYANCY_ENABLED = "流体.启用浮力";
    public static final String FLUID_DENSITY_WATER = "流体.密度_水";
    public static final String FLUID_DENSITY_LAVA = "流体.密度_岩浆";
    public static final String FLUID_LINEAR_DRAG_WATER = "流体.线性阻力_水";
    public static final String FLUID_LINEAR_DRAG_LAVA = "流体.线性阻力_岩浆";
    public static final String FLUID_ANGULAR_DRAG_WATER = "流体.角阻力_水";
    public static final String FLUID_ANGULAR_DRAG_LAVA = "流体.角阻力_岩浆";
    public static final String FLUID_HORIZONTAL_FLOW_WATER = "流体.水平流速_水";
    public static final String FLUID_HORIZONTAL_FLOW_LAVA = "流体.水平流速_岩浆";
    public static final String FLUID_FALLING_FLOW_WATER = "流体.下落流速_水";
    public static final String FLUID_FALLING_FLOW_LAVA = "流体.下落流速_岩浆";
    public static final String FLUID_BUBBLE_UP_SPEED = "流体.气泡柱上升速度";
    public static final String FLUID_BUBBLE_DOWN_SPEED = "流体.气泡柱下降速度";
    public static final String FLUID_SAMPLE_POINTS = "流体.采样点数";
    public static final String ENTITY_COLLISION_ENABLED = "实体.启用碰撞";
    public static final String ENTITY_SCAN_RADIUS = "实体.扫描半径";
    public static final String ENTITY_SCAN_INTERVAL = "实体.扫描间隔";
    public static final String ENTITY_DEFAULT_MASS_PLAYER = "实体.默认质量_玩家";
    public static final String ENTITY_DEFAULT_MASS_NON_PLAYER = "实体.默认质量_非玩家";
    public static final String ENTITY_POSITION_SYNC_FACTOR = "实体.位置同步系数";
    public static final String ENTITY_VELOCITY_SYNC_FACTOR = "实体.速度同步系数";
    public static final String ENTITY_PLAYER_MASS_PREFIX = "实体.质量_";
    public static final String ENTITY_UUID_MASS_PREFIX = "实体.质量_";
    public static final String ENTITY_TYPE_MASS_PREFIX = "实体.默认质量_类型_";
    public static final String MATERIAL_FRICTION_PREFIX = "材质.摩擦系数_";
    public static final String MATERIAL_STATIC_FRICTION_PREFIX = "材质.静摩擦系数_";
    public static final String MATERIAL_DYNAMIC_FRICTION_PREFIX = "材质.动摩擦系数_";
    public static final String MATERIAL_ROLLING_FRICTION_PREFIX = "材质.滚动摩擦系数_";
    public static final String MATERIAL_RESTITUTION_PREFIX = "材质.反弹系数_";
    public static final String GAME_FORCE_ATTACK_IMPULSE = "游戏作用.攻击冲量";

    public static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    public static final Map<String, String> LEGACY_KEYS = new LinkedHashMap<>();
    public static final List<String> KEYS;

    static {
        add(PHYSICS_GRAVITY_X, "X 轴重力加速度。", "通常保持 0；需要横向风场效果时再调整。", 0.0d);
        add(PHYSICS_GRAVITY_Y, "Y 轴重力加速度。", "默认 -10 接近 Minecraft 观感；更重可用 -15 到 -25。", -10.0d);
        add(PHYSICS_GRAVITY_Z, "Z 轴重力加速度。", "通常保持 0；需要固定方向外力时再调整。", 0.0d);
        add(PHYSICS_FIXED_STEP, "Bullet 固定子步长，单位秒。", "建议 0.016666668；更小更稳定但更耗性能。", 0.016666668d);
        add(PHYSICS_MAX_SUB_STEPS, "每 tick 最多执行的 Bullet 子步数。", "建议 3-5；高速物体穿模时调高，卡顿时调低。", 5);
        add(PHYSICS_TERRAIN_SCAN_MARGIN, "动态物体周围扫描静态地形的额外半径，单位方块。", "建议 1-2；高速或大物体可调到 3。", 2);
        add(PHYSICS_TERRAIN_SYNC_INTERVAL, "每隔多少 tick 刷新附近地形碰撞体。", "建议 3-10；实体多时调大，快速移动时调小。", 5);
        add(PHYSICS_SCAN_SLEEPING_BODIES, "是否为休眠刚体继续扫描地形。", "建议 false；如果地形会在静止物体附近频繁改变再设 true。", false);
        add(PERFORMANCE_PARTITION_ENABLED, "是否启用空间分区和激活范围。", "建议 true；刚体数量很多时必须开启。", true);
        add(PERFORMANCE_ACTIVE_RADIUS, "玩家周围完整物理模拟半径，单位方块。", "建议 32-64；核心互动区域保持完整模拟。", 48.0d);
        add(PERFORMANCE_THROTTLE_RADIUS, "玩家周围降频物理模拟半径，单位方块。", "建议 64-128；该范围内按间隔模拟。", 96.0d);
        add(PERFORMANCE_SLEEP_RADIUS, "玩家周围保持可唤醒的最大半径，单位方块。", "建议 128-192；超过该范围强制休眠。", 160.0d);
        add(PERFORMANCE_THROTTLE_INTERVAL, "降频区域每隔多少 tick 模拟一次。", "建议 2-5；越大越省性能但远处越不连续。", 3);
        add(PERFORMANCE_CELL_SIZE, "空间分区单元大小，单位方块。", "建议 16-32；通常与区块大小接近。", 16.0d);
        add(PERFORMANCE_PAUSE_UNLOADED_CHUNKS, "是否暂停未加载区块内刚体模拟。", "建议 true；避免物理把实体同步到未加载区块。", true);
        add(PERFORMANCE_SELECTION_BUILD_BLOCKS_PER_TICK, "选区构建任务每 tick 最多处理的方块数。", "建议 100-300；越大构建越快但越容易卡顿。", 200);
        add(PERFORMANCE_SELECTION_BUILD_CULL_INTERNAL, "选区构建时是否剔除完全藏在内部的方块。", "建议 true；可显著减少 Display、刚体和约束数量。", true);
        add(PHYSICS_DYNAMIC_MARGIN, "动态盒碰撞体的 Bullet margin。", "建议 0-0.02；视觉贴合优先用 0，稳定性优先用 0.01。", 0.0d);
        add(PHYSICS_STATIC_MARGIN, "静态地形碰撞体的 Bullet margin。", "建议 0-0.02；浮空明显时保持 0。", 0.0d);
        add(PHYSICS_FRICTION, "接触摩擦系数。", "建议 0.5-1.0；太低会很滑，太高堆叠更稳但可能更耗解算。", 0.9d);
        add(PHYSICS_STATIC_FRICTION, "静摩擦系数，用于地形碰撞体的 Bullet friction。", "建议 0.6-1.2；冰面调低，蜂蜜块调高。", 0.9d);
        add(PHYSICS_DYNAMIC_FRICTION, "动摩擦系数，用于接触地形时的速度阻尼近似。", "建议 0.01-0.2；越大滑行越短。", 0.05d);
        add(PHYSICS_ROLLING_FRICTION, "滚动摩擦系数，用于接触地形时的角速度阻尼近似。", "建议 0.01-0.2；越大越快停止滚动。", 0.04d);
        add(PHYSICS_RESTITUTION, "动态物体反弹系数。", "建议 0-0.3；飞弹明显时降低到 0-0.05。", 0.15d);
        add(PHYSICS_STATIC_RESTITUTION, "静态地形反弹系数。", "建议 0-0.2；落地弹跳过强时降低。", 0.1d);
        add(PHYSICS_LINEAR_DAMPING, "线速度阻尼。", "建议 0-0.05；想让物体更快停下可调高。", 0.01d);
        add(PHYSICS_ANGULAR_DAMPING, "角速度阻尼。", "建议 0.03-0.15；旋转太久不停时调高。", 0.05d);
        add(PHYSICS_LINEAR_SLEEPING_THRESHOLD, "线速度低于该阈值时更容易休眠。", "建议 0.01-0.05；实体多时调高可省性能。", 0.02d);
        add(PHYSICS_ANGULAR_SLEEPING_THRESHOLD, "角速度低于该阈值时更容易休眠。", "建议 0.01-0.05；实体多时调高可省性能。", 0.02d);
        add(PHYSICS_FIXED_CONSTRAINT_BREAK_IMPULSE, "fixed 固定约束承受的最大求解冲量，超过后自动断开。", "建议 150-800；越小越容易碎裂，设为 0 或负数表示永不断裂。", 350.0d);
        add(DISPLAY_INTERPOLATION_DELAY, "Display Entity 插值延迟 tick。", "建议 0；网络抖动明显可试 1。", 0);
        add(DISPLAY_INTERPOLATION_DURATION, "Display Entity 变换插值时长 tick。", "建议 2-3；越大越顺滑但视觉延迟越高。", 2);
        add(DISPLAY_TELEPORT_DURATION, "Display Entity 位置传送插值时长 tick。", "建议与 interpolation-duration 一致。", 2);
        add(DISPLAY_SPHERE_DETAIL, "球体显示精细度。0 表示只用一个外包正方体显示，数值越大 Display 方块越多。", "建议 0-4；默认 3，过高会增加实体数量和渲染开销。", 3);
        add(FLUID_BUOYANCY_ENABLED, "是否启用水和岩浆浮力。", "建议 true；如果实体很多且液体区域复杂，可设 false 关闭。", true);
        add(FLUID_DENSITY_WATER, "水的流体密度。当前以 Minecraft 视觉调校为主，不是 SI 单位。", "建议 800-1200；物体太容易沉就调高。", 1000.0d);
        add(FLUID_DENSITY_LAVA, "岩浆的流体密度。通常应高于水。", "建议 2000-3500。", 3000.0d);
        add(FLUID_LINEAR_DRAG_WATER, "水的线性阻力系数。", "建议 0.5-3.0；水里滑太快就调高。", 1.5d);
        add(FLUID_LINEAR_DRAG_LAVA, "岩浆的线性阻力系数。", "建议 3.0-8.0；岩浆应明显更粘。", 5.0d);
        add(FLUID_ANGULAR_DRAG_WATER, "水的角阻力系数。", "建议 0.2-2.0；水里旋转太久就调高。", 0.8d);
        add(FLUID_ANGULAR_DRAG_LAVA, "岩浆的角阻力系数。", "建议 2.0-6.0。", 3.0d);
        add(FLUID_HORIZONTAL_FLOW_WATER, "水平水流速度倍率。", "建议 0.4-1.5；想让水流推力更强就调高。", 0.9d);
        add(FLUID_HORIZONTAL_FLOW_LAVA, "水平岩浆流速倍率。", "建议 0.2-0.8；岩浆通常比水慢。", 0.45d);
        add(FLUID_FALLING_FLOW_WATER, "下落水流向下速度。", "建议 0.5-2.0。", 1.2d);
        add(FLUID_FALLING_FLOW_LAVA, "下落岩浆向下速度。", "建议 0.2-1.0。", 0.5d);
        add(FLUID_BUBBLE_UP_SPEED, "上升气泡柱向上速度。", "建议 1.0-4.0。", 2.5d);
        add(FLUID_BUBBLE_DOWN_SPEED, "下降气泡柱向下速度。", "建议 1.0-4.0。", 2.5d);
        add(FLUID_SAMPLE_POINTS, "流体采样点数。7 表示中心和六个面，27 表示更精细的三轴网格。", "建议 7；只有小物体在复杂水面表现不够好时再设 27。", 7);
        add(ENTITY_COLLISION_ENABLED, "是否启用刚体与游戏实体碰撞，包括玩家。", "建议 true；实体很多时可设 false。", true);
        add(ENTITY_SCAN_RADIUS, "动态物体周围扫描游戏实体的半径，单位方块。", "建议 3-8；实体多时调小。", 6.0d);
        add(ENTITY_SCAN_INTERVAL, "每隔多少 tick 刷新附近游戏实体碰撞体。", "建议 2-5；实体很多时调大，快速运动实体多时调小。", 3);
        add(ENTITY_DEFAULT_MASS_PLAYER, "没有单独质量配置时，玩家默认质量。", "建议默认 0；玩家只作为静态碰撞体，不被物理反作用力推动。需要被推动时设为 60-100。", 0.0d);
        add(ENTITY_DEFAULT_MASS_NON_PLAYER, "没有单独质量配置时，非玩家实体默认质量。", "建议 10-80；设为 0 时实体只作为静态碰撞体。具体类型可用 实体.默认质量_类型_<EntityType> 覆盖。", 30.0d);
        add(ENTITY_POSITION_SYNC_FACTOR, "动态实体从物理世界同步回 Minecraft 时的位置插值系数。", "建议 0.4-1.0；越大越紧跟物理，但实体移动更突然。", 0.7d);
        add(ENTITY_VELOCITY_SYNC_FACTOR, "动态实体从物理世界同步回 Minecraft 时的速度同步系数。", "建议 0.5-1.0。", 0.8d);
        add(GAME_FORCE_ATTACK_IMPULSE, "玩家左键击打刚体时施加的冲量。", "建议 30-120；物体质量越大需要越高，太大容易击飞。", 60.0d);
        KEYS = List.copyOf(ENTRIES.keySet());

        legacy(PHYSICS_GRAVITY_X, "physics.gravity-x");
        legacy(PHYSICS_GRAVITY_Y, "physics.gravity-y");
        legacy(PHYSICS_GRAVITY_Z, "physics.gravity-z");
        legacy(PHYSICS_FIXED_STEP, "physics.fixed-step");
        legacy(PHYSICS_MAX_SUB_STEPS, "physics.max-sub-steps");
        legacy(PHYSICS_TERRAIN_SCAN_MARGIN, "physics.terrain-scan-margin");
        legacy(PHYSICS_TERRAIN_SYNC_INTERVAL, "physics.terrain-sync-interval");
        legacy(PHYSICS_SCAN_SLEEPING_BODIES, "physics.scan-sleeping-bodies");
        legacy(PERFORMANCE_PARTITION_ENABLED, "performance.partition-enabled");
        legacy(PERFORMANCE_ACTIVE_RADIUS, "performance.active-radius");
        legacy(PERFORMANCE_THROTTLE_RADIUS, "performance.throttle-radius");
        legacy(PERFORMANCE_SLEEP_RADIUS, "performance.sleep-radius");
        legacy(PERFORMANCE_THROTTLE_INTERVAL, "performance.throttle-interval");
        legacy(PERFORMANCE_CELL_SIZE, "performance.cell-size");
        legacy(PERFORMANCE_PAUSE_UNLOADED_CHUNKS, "performance.pause-unloaded-chunks");
        legacy(PERFORMANCE_SELECTION_BUILD_BLOCKS_PER_TICK, "performance.selection-build-blocks-per-tick");
        legacy(PERFORMANCE_SELECTION_BUILD_CULL_INTERNAL, "performance.selection-build-cull-internal");
        legacy(PHYSICS_DYNAMIC_MARGIN, "physics.dynamic-margin");
        legacy(PHYSICS_STATIC_MARGIN, "physics.static-margin");
        legacy(PHYSICS_FRICTION, "physics.friction");
        legacy(PHYSICS_STATIC_FRICTION, "physics.static-friction");
        legacy(PHYSICS_DYNAMIC_FRICTION, "physics.dynamic-friction");
        legacy(PHYSICS_ROLLING_FRICTION, "physics.rolling-friction");
        legacy(PHYSICS_RESTITUTION, "physics.restitution");
        legacy(PHYSICS_STATIC_RESTITUTION, "physics.static-restitution");
        legacy(PHYSICS_LINEAR_DAMPING, "physics.linear-damping");
        legacy(PHYSICS_ANGULAR_DAMPING, "physics.angular-damping");
        legacy(PHYSICS_LINEAR_SLEEPING_THRESHOLD, "physics.linear-sleeping-threshold");
        legacy(PHYSICS_ANGULAR_SLEEPING_THRESHOLD, "physics.angular-sleeping-threshold");
        legacy(PHYSICS_FIXED_CONSTRAINT_BREAK_IMPULSE, "physics.fixed-constraint-break-impulse");
        legacy(DISPLAY_INTERPOLATION_DELAY, "display.interpolation-delay");
        legacy(DISPLAY_INTERPOLATION_DURATION, "display.interpolation-duration");
        legacy(DISPLAY_TELEPORT_DURATION, "display.teleport-duration");
        legacy(DISPLAY_SPHERE_DETAIL, "display.sphere-detail");
    }

    private PhysConfig() {
    }

    private static void add(String key, String description, String suggestion, Object defaultValue) {
        ENTRIES.put(key, new Entry(description, suggestion, defaultValue));
    }

    private static void legacy(String key, String legacyKey) {
        LEGACY_KEYS.put(key, legacyKey);
    }

    public static Entry get(String key) {
        return ENTRIES.get(key);
    }

    public static boolean isEntityMassKey(String key) {
        return key != null && key.startsWith(ENTITY_PLAYER_MASS_PREFIX) && key.length() > ENTITY_PLAYER_MASS_PREFIX.length();
    }

    public static boolean isEntityTypeMassKey(String key) {
        return key != null && key.startsWith(ENTITY_TYPE_MASS_PREFIX) && key.length() > ENTITY_TYPE_MASS_PREFIX.length();
    }

    public static boolean isMaterialFrictionKey(String key) {
        return key != null && key.startsWith(MATERIAL_FRICTION_PREFIX) && key.length() > MATERIAL_FRICTION_PREFIX.length();
    }

    public static boolean isMaterialStaticFrictionKey(String key) {
        return key != null && key.startsWith(MATERIAL_STATIC_FRICTION_PREFIX) && key.length() > MATERIAL_STATIC_FRICTION_PREFIX.length();
    }

    public static boolean isMaterialDynamicFrictionKey(String key) {
        return key != null && key.startsWith(MATERIAL_DYNAMIC_FRICTION_PREFIX) && key.length() > MATERIAL_DYNAMIC_FRICTION_PREFIX.length();
    }

    public static boolean isMaterialRollingFrictionKey(String key) {
        return key != null && key.startsWith(MATERIAL_ROLLING_FRICTION_PREFIX) && key.length() > MATERIAL_ROLLING_FRICTION_PREFIX.length();
    }

    public static boolean isMaterialRestitutionKey(String key) {
        return key != null && key.startsWith(MATERIAL_RESTITUTION_PREFIX) && key.length() > MATERIAL_RESTITUTION_PREFIX.length();
    }

    public static final class Entry {
        public final String description;
        public final String suggestion;
        public final Object defaultValue;

        private Entry(String description, String suggestion, Object defaultValue) {
            this.description = description;
            this.suggestion = suggestion;
            this.defaultValue = defaultValue;
        }
    }
}
