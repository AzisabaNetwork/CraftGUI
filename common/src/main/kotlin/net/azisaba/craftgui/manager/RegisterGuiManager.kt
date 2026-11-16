package net.azisaba.craftgui.manager

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.data.RecipeBranch
import net.azisaba.craftgui.data.RecipeData
import net.azisaba.craftgui.data.RegisterData
import net.azisaba.craftgui.gui.RegisterGuiHolder
import net.azisaba.craftgui.platform.PlatformAdapter
import net.azisaba.craftgui.util.InventoryUtil
import net.azisaba.craftgui.util.ItemUtil
import net.azisaba.craftgui.util.MythicItemUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class RegisterGuiManager(
    private val plugin: CraftGUI,
    private val mythicItemUtil: MythicItemUtil,
    private val recipeConfigManager: RecipeConfigManager,
    private val inventoryUtil: InventoryUtil,
    private val platformAdapter: PlatformAdapter,
) : Listener {

    private val openRegisterGuis = HashMap<UUID, RegisterData>()
    private val processing: MutableSet<UUID> = Collections.synchronizedSet(HashSet())

    fun createAndOpenGui(player: Player, page: Int, slot: Int, recipeId: String, existingRecipe: RecipeData?) {
        createAndOpenGui(player, page, slot, recipeId, existingRecipe, false, 1)
    }

    fun createAndOpenGuiFromEdit(player: Player, page: Int, slot: Int, recipeId: String, existingRecipe: RecipeData?, editPage: Int) {
        createAndOpenGui(player, page, slot, recipeId, existingRecipe, true, editPage)
    }

    private fun createAndOpenGui(
        player: Player,
        page: Int,
        slot: Int,
        recipeId: String,
        existingRecipe: RecipeData?,
        returnToEdit: Boolean,
        editPage: Int,
    ) {
        val holder = RegisterGuiHolder(page, slot, recipeId)
        val gui = Bukkit.createInventory(holder, 54, GUI_TITLE_PREFIX + recipeId)
        holder.setInventory(gui)

        val saveButton = createGuiItem(
            Material.EMERALD_BLOCK,
            "${ChatColor.GREEN}レシピを保存",
            listOf(
                "${ChatColor.GRAY}ページ: $page",
                "${ChatColor.GRAY}スロット: $slot",
                "${ChatColor.GRAY}ID: $recipeId",
                "",
                "${ChatColor.YELLOW}クリックしてレシピを保存",
            ),
        )
        gui.setItem(SAVE_BUTTON_SLOT, saveButton)

        if (existingRecipe != null) {
            placeBranchesInGrid(gui, existingRecipe.requiredBranches)
            placeItemsInGrid(gui, existingRecipe.resultItems, RESULT_SLOTS)
        }

        player.openInventory(gui)
        openRegisterGuis[player.uniqueId] = RegisterData(page, slot, recipeId, returnToEdit, editPage)
    }

    private fun placeBranchesInGrid(gui: Inventory, branches: List<RecipeBranch>) {
        branches.take(5).forEachIndexed { row, branch ->
            placeItemsInGrid(gui, branch.materials, rowSlots(row))
        }
    }

    private fun placeItemsInGrid(gui: Inventory, materials: List<CraftingMaterial>, slots: IntArray) {
        if (materials.isEmpty()) return

        var slotIndex = 0
        try {
            for (material in materials) {
                val item = inventoryUtil.getItemStackFromMaterial(material)
                if (item == null || ItemUtil.isAir(item)) continue

                var amountToPlace = material.amount
                val maxStackSize = item.type.maxStackSize
                while (amountToPlace > 0 && slotIndex < slots.size) {
                    val currentStackSize = minOf(amountToPlace, maxStackSize)
                    item.amount = currentStackSize
                    gui.setItem(slots[slotIndex], item.clone())
                    amountToPlace -= currentStackSize
                    slotIndex++
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "GUIへの既存アイテム配置中にエラーが発生しました。", e)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val uuid = player.uniqueId
        val data = openRegisterGuis[uuid] ?: return

        val topInventory = platformAdapter.getTopInventory(event.view) ?: return
        val holder = topInventory.holder as? RegisterGuiHolder ?: return
        if (holder.page != data.page || holder.slot != data.slot || holder.recipeId != data.recipeId) {
            return
        }

        event.isCancelled = false
        if (event.rawSlot == SAVE_BUTTON_SLOT) {
            event.isCancelled = true
            if (uuid in processing) return
            handleAsyncSave(player, event.inventory, data)
            player.closeInventory()
        }
    }

    private fun handleAsyncSave(player: Player, inv: Inventory, data: RegisterData) {
        val uuid = player.uniqueId
        processing.add(uuid)
        player.sendMessage("${ChatColor.YELLOW}レシピを解析しています.. (非同期)")

        CompletableFuture.supplyAsync {
            val reqConfig = ArrayList<Map<String, Any>>()
            for (row in 0 until 5) {
                val rowItems = getSnapshots(inv, rowSlots(row))
                if (rowItems.isNotEmpty()) {
                    reqConfig.add(linkedMapOf("branch" to analyzeItems(rowItems)))
                }
            }

            val finalReqConfig: Any = when (reqConfig.size) {
                0 -> ArrayList<Any>()
                1 -> reqConfig[0].getValue("branch")
                else -> reqConfig
            }
            finalReqConfig to analyzeItems(getSnapshots(inv, RESULT_SLOTS))
        }.thenAccept { (req, res) ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    saveToConfig(player, data, req, res)
                } finally {
                    processing.remove(uuid)
                    openRegisterGuis.remove(uuid)
                }
            })
        }.exceptionally { ex ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("${ChatColor.RED}保存中にエラーが発生しました。")
                ex.printStackTrace()
                processing.remove(uuid)
            })
            null
        }
    }

    private fun analyzeItems(snapshots: List<ItemStack>): List<Map<String, Any?>> {
        val counts = LinkedHashMap<String, Int>()
        val serializedEntries = ArrayList<SerializedItemEntry>()
        for (item in snapshots) {
            val mmid = mythicItemUtil.findMMIDAsync(item).join()
            when {
                mmid != null -> counts.merge("mmid:$mmid", item.amount) { a, b -> a + b }
                isSimpleVanillaItem(item) -> counts.merge("material:${item.type.name}", item.amount) { a, b -> a + b }
                else -> mergeSerializedItem(serializedEntries, item)
            }
        }

        val resultList = ArrayList<Map<String, Any?>>()
        for ((key, amount) in counts) {
            val itemMap = HashMap<String, Any?>()
            if (key.startsWith("mmid:")) {
                itemMap["mmid"] = key.removePrefix("mmid:")
            } else {
                itemMap["material"] = key.removePrefix("material:")
            }
            itemMap["amount"] = amount
            resultList.add(itemMap)
        }

        for (entry in serializedEntries) {
            resultList.add(hashMapOf("itemStack" to entry.serializedItem, "amount" to entry.amount))
        }
        return resultList
    }

    private fun mergeSerializedItem(serializedEntries: MutableList<SerializedItemEntry>, item: ItemStack) {
        val normalizedItem = item.clone().apply { amount = 1 }
        val existing = serializedEntries.firstOrNull { inventoryUtil.isSimilar(it.sampleItem, normalizedItem) }
        if (existing != null) {
            existing.amount += item.amount
            return
        }
        serializedEntries.add(SerializedItemEntry(normalizedItem, platformAdapter.serializeItemStack(normalizedItem), item.amount))
    }

    private fun isSimpleVanillaItem(item: ItemStack): Boolean {
        if (ItemUtil.isAir(item)) return false
        if (!item.hasItemMeta()) return true
        val meta = item.itemMeta ?: return true
        if (item.type == Material.PLAYER_HEAD) return false
        return !meta.hasDisplayName() &&
            !meta.hasLore() &&
            platformAdapter.getCustomModelData(meta) == null &&
            !meta.hasEnchants() &&
            !meta.isUnbreakable &&
            meta.itemFlags.isEmpty()
    }

    private class SerializedItemEntry(
        val sampleItem: ItemStack,
        val serializedItem: Map<String, Any?>,
        var amount: Int,
    )

    private fun getSnapshots(inv: Inventory, slots: IntArray): List<ItemStack> =
        slots.asList().mapNotNull { inv.getItem(it) }
            .filterNot { ItemUtil.isAir(it) }
            .map { it.clone() }

    private fun saveToConfig(player: Player, data: RegisterData, req: Any, res: List<Map<String, Any?>>) {
        val config = recipeConfigManager.config
        val path = "Items.page${data.page}.${data.slot}"
        config.set("$path.id", data.recipeId)
        config.set("$path.enabled", true)
        config.set("$path.craftable", true)
        config.set("$path.requiredItems", req)
        config.set("$path.resultItems", res)
        recipeConfigManager.saveConfig()
        plugin.sendMessage(player, "&aレシピを保存しました。Hot Updateを適用しました。")
        plugin.performSafeReload(null)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId
        val topInventory = platformAdapter.getTopInventory(event.view) ?: return
        if (uuid !in openRegisterGuis || topInventory.holder !is RegisterGuiHolder) return

        val data = openRegisterGuis.remove(uuid)
        if (data != null && data.returnToEdit && uuid !in processing) {
            Bukkit.getScheduler().runTask(plugin, Runnable { plugin.editGuiManager.openEditGui(player, data.editPage) })
        }
    }

    private fun createGuiItem(material: Material, name: String, lore: List<String>?): ItemStack {
        val item = ItemStack(material)
        item.itemMeta?.let { meta ->
            meta.setDisplayName(name)
            if (lore != null) meta.lore = lore
            item.itemMeta = meta
        }
        return item
    }

    companion object {
        private val RESULT_SLOTS = (45 until 53).toList().toIntArray()
        private const val SAVE_BUTTON_SLOT = 53
        private const val GUI_TITLE_PREFIX = "CraftGUI Register: "

        private fun rowSlots(row: Int): IntArray = (row * 9 until row * 9 + 9).toList().toIntArray()
    }
}
