package net.azisaba.craftgui.mythic;

import org.bukkit.Bukkit;

public class MythicAdapterProvider {

    public static MythicAdapter createAdapter() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return new DummyMythicAdapter();
        }

        // Check for MythicMobs v5
        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Class<?> v5Clazz = Class.forName("net.azisaba.craftgui.mythic.v5.MythicMobsV5Adapter");
            return (MythicAdapter) v5Clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
        }

        // Check for MythicMobs v4
        try {
            Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
            Class<?> v4Clazz = Class.forName("net.azisaba.craftgui.mythic.v4.MythicMobsV4Adapter");
            return (MythicAdapter) v4Clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
        }

        return new DummyMythicAdapter();
    }
}
