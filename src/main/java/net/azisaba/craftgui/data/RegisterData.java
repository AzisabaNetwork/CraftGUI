package net.azisaba.craftgui.data;

public record RegisterData(int page, int slot, String recipeId, boolean returnToEdit, int editPage) {

    public RegisterData(int page, int slot, String recipeId) {
        this(page, slot, recipeId, false, 1);
    }

}
