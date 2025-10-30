package net.azisaba.craftgui.data;

public class RegisterData {

    public final int page;
    public final int slot;
    public final String recipeId;
    public final String guiTitle;

    public RegisterData(int page, int slot, String recipeId, String guiTitle) {
        this.page = page;
        this.slot = slot;
        this.recipeId = recipeId;
        this.guiTitle = guiTitle;
    }
}
