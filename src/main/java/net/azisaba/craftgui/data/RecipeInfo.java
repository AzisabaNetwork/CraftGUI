package net.azisaba.craftgui.data;

public class RecipeInfo {

    public final String id;
    public final int page;
    public final int slot;
    public final boolean isEnabled;
    public final boolean isCraftable;

    public RecipeInfo(String id, int page, int slot, boolean isEnabled, boolean isCraftable) {
        this.id = id;
        this.page = page;
        this.slot = slot;
        this.isEnabled = isEnabled;
        this.isCraftable = isCraftable;
    }
}