package net.azisaba.craftgui.data;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class CraftingMaterial {

    private final boolean isMythicItem;
    private final String mmid;
    private final Material material;
    private final int amount;
    private final String displayName;
    private final List<String> lore;
    private final Map<String, Object> itemStackData;

    public CraftingMaterial(boolean isMythicItem, String mmid, Material material, int amount, String displayName, List<String> lore, Map<String, Object> itemStackData) {
        this.isMythicItem = isMythicItem;
        this.mmid = mmid;
        this.material = material;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore;
        this.itemStackData = itemStackData;
    }

    public boolean isMythicItem() { return isMythicItem; }
    public String getMmid() { return mmid; }
    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public Map<String, Object> getItemStackData() { return itemStackData; }
    public boolean hasItemStackData() { return itemStackData != null && !itemStackData.isEmpty(); }
}
