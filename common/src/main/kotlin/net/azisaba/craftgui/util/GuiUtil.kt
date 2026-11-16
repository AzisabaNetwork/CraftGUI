package net.azisaba.craftgui.util

import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.data.RecipeData
import org.bukkit.ChatColor
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
        item.itemMeta?.let { meta ->
            ItemUtil.applyCleanFlags(meta)
            item.itemMeta = meta
        }
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
        val meta = itemStack.itemMeta ?: return
        val finalLore = ArrayList<String>()
        val loreOn = mapUtil.isLoreToggledOn(player.uniqueId)

        if (loreOn) {
            val baseIcon = recipeData.guiIcon
            val baseLore = if (baseIcon.hasItemMeta()) baseIcon.itemMeta?.takeIf { it.hasLore() }?.lore else null
            if (baseLore != null) {
                finalLore.addAll(baseLore)
                finalLore.add("")
            }
        }

        finalLore.addAll(loadedLores[recipeData.loreKey].orEmpty())
        finalLore.add("")

        val limitByMaterial = inventoryUtil.calculateMaxCraftableByMaterial(snapshot, recipeData.requiredBranches)
        val limitFinal: Long = if (limitByMaterial <= 0) {
            0L
        } else {
            minOf(limitByMaterial, inventoryUtil.calculateMaxCraftableByInventory(snapshot, recipeData.resultItems))
        }

        if (limitFinal > 0) {
            finalLore.add("${ChatColor.GREEN}✓ 変換可能です")
            finalLore.add("${ChatColor.GRAY}変換可能回数: ${ChatColor.AQUA}${limitFinal}回")
        } else {
            finalLore.add("${ChatColor.RED}✘ 変換できません")
            if (limitByMaterial > 0) {
                finalLore.add("${ChatColor.YELLOW}✘ インベントリに空きがありません")
            } else if (limitByMaterial == 0L) {
                finalLore.add("${ChatColor.RED}✘ 変換に必要なアイテムが不足しています")
            }
        }

        finalLore.add("")
        finalLore.add("${ChatColor.GRAY}変換に必要なアイテム: ")

        val branches = recipeData.requiredBranches
        if (branches.size == 1) {
            branches[0].materials.forEach { addRequiredMaterialToLore(finalLore, it, player, loreOn, snapshot) }
        } else {
            branches.forEachIndexed { i, branch ->
                finalLore.add("${ChatColor.YELLOW}[パターン ${i + 1}]")
                branch.materials.forEach { addRequiredMaterialToLore(finalLore, it, player, loreOn, snapshot) }
            }
        }

        if (mapUtil.isShowResultItems(player.uniqueId)) {
            finalLore.add("")
            finalLore.add("${ChatColor.GRAY}変換後のアイテム: ")
            for (result in recipeData.resultItems) {
                val displayName = mythicItemUtil.resolveDisplayName(result, player)
                finalLore.add("${ChatColor.GREEN}✓ ${ChatColor.WHITE}$displayName${ChatColor.GRAY} x${result.amount}")
                if (loreOn && result.isMythicItem) {
                    addMythicLore(finalLore, result)
                }
            }
        }

        finalLore.add("")
        finalLore.add("${ChatColor.GRAY}レシピID: ${ChatColor.BLUE}${recipeData.id}")

        meta.lore = finalLore
        itemStack.itemMeta = meta
    }

    private fun createCountMessage(ownedAmount: Long, requiredAmount: Int): String = when {
        ownedAmount >= requiredAmount -> "${ChatColor.AQUA} (${ownedAmount}個所持)"
        ownedAmount > 0 -> "${ChatColor.YELLOW} (${requiredAmount - ownedAmount}個不足)"
        else -> "${ChatColor.RED} (所持していません)"
    }

    private fun addRequiredMaterialToLore(
        finalLore: MutableList<String>,
        required: CraftingMaterial,
        player: Player,
        loreOn: Boolean,
        snapshot: InventoryUtil.InventorySnapshot,
    ) {
        val ownedAmount = inventoryUtil.countItems(snapshot, required)
        val checkMark = if (ownedAmount >= required.amount) "${ChatColor.GREEN}✓ " else "${ChatColor.RED}✘ "
        val displayName = mythicItemUtil.resolveDisplayName(required, player)
        val countMessage = createCountMessage(ownedAmount, required.amount)

        finalLore.add("$checkMark${ChatColor.WHITE}$displayName${ChatColor.GRAY} x${required.amount}$countMessage")

        if (loreOn && required.isMythicItem) {
            addMythicLore(finalLore, required)
        }
    }

    private fun addMythicLore(finalLore: MutableList<String>, material: CraftingMaterial) {
        mythicItemUtil.getLoreFromMMID(material.mmid).forEach {
            finalLore.add("${ChatColor.DARK_GRAY}  » ${ChatColor.RESET}$it")
        }
    }
}
