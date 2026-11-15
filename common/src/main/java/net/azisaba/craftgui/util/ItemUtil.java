package net.azisaba.craftgui.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemUtil {

    public static boolean isAir(ItemStack item) {
        if (item == null) return true;
        return isAir(item.getType());
    }

    public static boolean isAir(Material material) {
        if (material == null) return true;
        return material == Material.AIR
                || "CAVE_AIR".equals(material.name())
                || "VOID_AIR".equals(material.name())
                || "LEGACY_AIR".equals(material.name());
    }

    public static Material getMaterialSafe(String name, Material fallback) {
        if (name == null) return fallback;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
    }
}
