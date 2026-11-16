package net.azisaba.craftgui.data

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class CraftingMaterial(
    val isMythicItem: Boolean,
    val mmid: String?,
    val material: Material?,
    val amount: Int,
    val displayName: String?,
    val lore: List<String>?,
    val itemStackData: Map<String, Any?>?,
) {
    fun hasItemStackData(): Boolean = !itemStackData.isNullOrEmpty()
}

class RecipeBranch(val materials: List<CraftingMaterial>)

class RecipeData(
    val id: String,
    val isEnabled: Boolean,
    val isCraftable: Boolean,
    val guiIcon: ItemStack,
    val loreKey: String,
    val resultItems: List<CraftingMaterial>,
    val requiredBranches: List<RecipeBranch>,
)

class RecipeInfo(
    val id: String,
    val page: Int,
    val slot: Int,
    val isEnabled: Boolean,
    val isCraftable: Boolean,
)

class RegisterData(
    val page: Int,
    val slot: Int,
    val recipeId: String,
    val returnToEdit: Boolean = false,
    val editPage: Int = 1,
)

/** Gson でシリアライズされるプレイヤー設定。フィールド名は既存の JSON と互換。 */
class PlayerData {
    var soundOn: Boolean = true
    var vanillaItemsToStash: Boolean = true
    var showResultItems: Boolean = true
    var craftableOnly: Boolean = false
}
