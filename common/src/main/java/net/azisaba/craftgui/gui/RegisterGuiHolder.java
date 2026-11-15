package net.azisaba.craftgui.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class RegisterGuiHolder implements InventoryHolder {

    private final int page;
    private final int slot;
    private final String recipeId;
    private Inventory inventory;

    public RegisterGuiHolder(int page, int slot, String recipeId) {
        this.page = page;
        this.slot = slot;
        this.recipeId = recipeId;
    }

    public int getPage() {
        return page;
    }

    public int getSlot() {
        return slot;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
