package net.azisaba.craftgui.platform;

import org.bukkit.Bukkit;

public class PlatformAdapterProvider {

    public static PlatformAdapter createAdapter() {
        if (isModernVersion()) {
            try {
                Class<?> clazz = Class.forName("net.azisaba.craftgui.platform.modern.ModernPlatformAdapter");
                return (PlatformAdapter) clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                // fallback
            }
        }
        try {
            Class<?> clazz = Class.forName("net.azisaba.craftgui.platform.legacy.LegacyPlatformAdapter");
            return (PlatformAdapter) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to instantiate PlatformAdapter", t);
        }
    }

    private static boolean isModernVersion() {
        try {
            if (org.bukkit.inventory.InventoryView.class.isInterface()) {
                return true;
            }
            String version = Bukkit.getBukkitVersion();
            return isVersionAtLeast(version, 1, 20, 5);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isVersionAtLeast(String versionString, int targetMajor, int targetMinor, int targetPatch) {
        try {
            String clean = versionString.split("-")[0];
            String[] parts = clean.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            if (major > targetMajor) return true;
            if (major < targetMajor) return false;
            if (minor > targetMinor) return true;
            if (minor < targetMinor) return false;
            return patch >= targetPatch;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
