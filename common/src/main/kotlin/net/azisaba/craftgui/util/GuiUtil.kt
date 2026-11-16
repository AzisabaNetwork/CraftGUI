package net.azisaba.craftgui.util

import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.data.RecipeData
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GuiUtil(
    private val inventoryUtil: InventoryUtil,
    private val mythicItemUtil: MythicItemUtil,
    private val loadedLores: Map<String, List<String>>,
    private val mapUtil: MapUtil,
) {

    fun createBaseRecipeItem(recipeData: RecipeData): ItemStack {
        val item = recipeData.guiIcon.clone()
        item.editMeta { ItemUtil.applyCleanFlags(it) }
        return item
    }

    fun updateLoreForPlayer(itemStack: ItemStack, recipeData: RecipeData, player: Player) {
        updateLoreForPlayer(itemStack, recipeData, player, inventoryUtil.createSnapshot(player))
    }

    fun updateLoreForPlayer(
        itemStack: ItemStack,
        recipeData: RecipeData,
        player: Player,
        snapshot: InventoryUtil.InventorySnapshot,
    ) {
        val lore = LoreBuilder()
        val loreOn = mapUtil.isLoreToggledOn(player.uniqueId)

        if (loreOn) {
            val baseLore = recipeData.guiIcon.itemMeta?.lore()
            if (!baseLore.isNullOrEmpty()) {
                lore.addAll(baseLore)
                lore.blank()
            }
        }

        loadedLores[recipeData.loreKey].orEmpty().forEach { lore.text(it) }
        lore.blank()

        val limitByMaterial = inventoryUtil.calculateMaxCraftableByMaterial(snapshot, recipeData.requiredBranches)
        val limitFinal: Long = if (limitByMaterial <= 0) {
            0L
        } else {
            minOf(limitByMaterial, inventoryUtil.calculateMaxCraftableByInventory(snapshot, recipeData.resultItems))
        }

        if (limitFinal > 0) {
            lore.text("&a✓ 変換可能です")
            lore.text("&7変換可能回数: &b${limitFinal}回")
        } else {
            lore.text("&c✘ 変換できません")
            if (limitByMaterial > 0) {
                lore.text("&e✘ インベントリに空きがありません")
            } else if (limitByMaterial == 0L) {
                lore.text("&c✘ 変換に必要なアイテムが不足しています")
            }
        }

        lore.blank()
        lore.text("&7変換に必要なアイテム: ")

        val branches = recipeData.requiredBranches
        if (branches.size == 1) {
            branches[0].materials.forEach { addRequiredMaterialToLore(lore, it, loreOn, snapshot) }
        } else {
            branches.forEachIndexed { i, branch ->
                lore.text("&e[パターン ${i + 1}]")
                branch.materials.forEach { addRequiredMaterialToLore(lore, it, loreOn, snapshot) }
            }
        }

        if (mapUtil.isShowResultItems(player.uniqueId)) {
            lore.blank()
            lore.text("&7変換後のアイテム: ")
            for (result in recipeData.resultItems) {
                lore.line(
                    Text.legacy("&a✓ "),
                    Text.whiteIfUncolored(mythicItemUtil.resolveDisplayName(result)),
                    Text.legacy("&7 x${result.amount}"),
                )
                if (loreOn && result.isMythicItem) {
                    addMythicLore(lore, result)
                }
            }
        }

        lore.blank()
        lore.text("&7レシピID: &9${recipeData.id}")

        itemStack.editMeta { it.lore(lore.build()) }
    }

    private fun createCountMessage(ownedAmount: Long, requiredAmount: Int): String = when {
        ownedAmount >= requiredAmount -> "&b (${ownedAmount}個所持)"
        ownedAmount > 0 -> "&e (${requiredAmount - ownedAmount}個不足)"
        else -> "&c (所持していません)"
    }

    private fun addRequiredMaterialToLore(
        lore: LoreBuilder,
        required: CraftingMaterial,
        loreOn: Boolean,
        snapshot: InventoryUtil.InventorySnapshot,
    ) {
        val ownedAmount = inventoryUtil.countItems(snapshot, required)
        val checkMark = if (ownedAmount >= required.amount) "&a✓ " else "&c✘ "

        lore.line(
            Text.legacy(checkMark),
            Text.whiteIfUncolored(mythicItemUtil.resolveDisplayName(required)),
            Text.legacy("&7 x${required.amount}" + createCountMessage(ownedAmount, required.amount)),
        )

        if (loreOn && required.isMythicItem) {
            addMythicLore(lore, required)
        }
    }

    private fun addMythicLore(lore: LoreBuilder, material: CraftingMaterial) {
        mythicItemUtil.getLoreFromMMID(material.mmid).forEach {
            lore.line(Text.legacy("&8  » "), it)
        }
    }

    /** 斜体を打ち消した Lore 行を組み立てる */
    private class LoreBuilder {
        private val lines = ArrayList<Component>()

        fun text(legacyText: String) {
            lines.add(Text.item(legacyText))
        }

        fun line(vararg parts: Component) {
            lines.add(Text.itemLine(*parts))
        }

        fun blank() {
            lines.add(Component.empty())
        }

        fun addAll(components: List<Component>) {
            lines.addAll(components)
        }

        fun build(): List<Component> = lines
    }
}
