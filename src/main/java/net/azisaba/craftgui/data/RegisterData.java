package net.azisaba.craftgui.data;

public class RegisterData {

    public final int page;
    public final int slot;
    public final String recipeId;

    public RegisterData(int page, int slot, String recipeId) {
        this.page = page;
        this.slot = slot;
        this.recipeId = recipeId;
    }
}
