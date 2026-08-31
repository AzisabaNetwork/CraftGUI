package net.azisaba.craftgui.data;

public class RegisterData {

    public final int page;
    public final int slot;
    public final String recipeId;
    public final boolean returnToEdit;
    public final int editPage;

    public RegisterData(int page, int slot, String recipeId) {
        this(page, slot, recipeId, false, 1);
    }

    public RegisterData(int page, int slot, String recipeId, boolean returnToEdit, int editPage) {
        this.page = page;
        this.slot = slot;
        this.recipeId = recipeId;
        this.returnToEdit = returnToEdit;
        this.editPage = editPage;
    }
}
