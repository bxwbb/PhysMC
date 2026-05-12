package com.bxwbb;

import com.bxwbb.event.DisplayHitListener;
import com.bxwbb.phys.PhysManager;
import com.bxwbb.util.SpawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PhysMC extends JavaPlugin {

    @Override
    public void onEnable() {
        super.onEnable();

        new PhysCommand(this);
        getServer().getPluginManager().registerEvents(new DisplayHitListener(), this);

        Bukkit.getScheduler().runTaskTimer(this, PhysManager.getInstance()::tick, 0L, 1L);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        SpawnUtil.removeAll();
    }
}
