package net.azisaba.craftgui.data;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record CraftingMaterial(boolean isMythicItem, String mmid, Material material, int amount, String displayName,
                               List<String> lore, Map<String, Object> itemStackData) {

    public boolean hasItemStackData() {
        return itemStackData != null && !itemStackData.isEmpty();
    }
}