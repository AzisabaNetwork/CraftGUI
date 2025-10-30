package net.azisaba.craftgui.data;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public class RecipeData {

    private final String id;
    private final boolean enabled;
    private final boolean craftable;
    private final ItemStack guiIcon;
    private final String loreKey;
    private final List<CraftingMaterial> resultItems;
    private final List<CraftingMaterial> requiredItems;

    public RecipeData(String id, boolean enabled, boolean craftable, ItemStack guiIcon, String loreKey, List<CraftingMaterial> resultItems, List<CraftingMaterial> requiredItems) {
        this.id = id;
        this.enabled = enabled;
        this.craftable = craftable;
        this.guiIcon = guiIcon;
        this.loreKey = loreKey;
        this.resultItems = resultItems;
        this.requiredItems = requiredItems;
    }

    public String getId() {
        return id;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public boolean isCraftable() {
        return craftable;
    }
    public ItemStack getGuiIcon() {
        return guiIcon;
    }
    public String getLoreKey() {
        return loreKey;
    }
    public List<CraftingMaterial> getResultItems() {
        return resultItems;
    }
    public List<CraftingMaterial> getRequiredItems() {
        return requiredItems;
    }
}
