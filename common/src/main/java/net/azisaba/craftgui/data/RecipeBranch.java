package net.azisaba.craftgui.data;

import java.util.List;

public class RecipeBranch {

    private final List<CraftingMaterial> materials;

    public RecipeBranch(List<CraftingMaterial> materials) {
        this.materials = materials;
    }

    public List<CraftingMaterial> getMaterials() {
        return materials;
    }
}
