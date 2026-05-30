package com.bxwbb.physmc.api;

/**
 * PhysMC 对外 API 的静态入口。
 *
 * <p>外部插件可以在 PhysMC 启用后调用 {@link #world()} 获取物理世界接口。
 * PhysMC 插件本体负责在启用和禁用时注册或注销实现。</p>
 */
public final class PhysMCApi {

    private static volatile PhysWorld world;

    private PhysMCApi() {
    }

    /**
     * 返回当前 PhysMC 物理世界接口。
     *
     * @return 物理世界接口
     * @throws IllegalStateException 当 PhysMC 未安装或未启用时抛出
     */
    public static PhysWorld world() {
        PhysWorld current = world;
        if (current == null) {
            throw new IllegalStateException("PhysMC 未安装或未启用");
        }
        return current;
    }

    /**
     * 注册 PhysMC API 实现。
     *
     * <p>该方法由 PhysMC 插件本体调用，外部插件不应主动调用。</p>
     *
     * @param provider 物理世界实现
     */
    public static void register(PhysWorld provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        world = provider;
    }

    /**
     * 注销当前 PhysMC API 实现。
     *
     * <p>该方法由 PhysMC 插件本体调用，外部插件不应主动调用。</p>
     */
    public static void unregister() {
        world = null;
    }
}
