package net.azisaba.craftgui.manager

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.data.RecipeInfo
import net.azisaba.craftgui.gui.EditGuiHolder
import net.azisaba.craftgui.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.ceil

class EditGuiManager(
    private val plugin: CraftGUI,
    private val recipeConfigManager: RecipeConfigManager,
    private val registerGuiManager: RegisterGuiManager,
) : Listener {

    private val openEditGuis = HashMap<UUID, Int>()
    private val playerEditList = HashMap<UUID, List<RecipeInfo>>()
    private val isNavigating = HashSet<UUID>()

    fun openEditGui(player: Player, page: Int) {
        val allRecipes = loadAllRecipeInfo()
        if (allRecipes.isEmpty()) {
            plugin.sendMessage(player, "&c編集可能なレシピがrecipes.ymlに見つかりません。")
            return
        }
        playerEditList[player.uniqueId] = allRecipes
        openEditGuiInternal(player, page, allRecipes)
    }

    private fun maxPageOf(allRecipes: List<RecipeInfo>): Int =
        ceil(allRecipes.size.toDouble() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)

    private fun openEditGuiInternal(player: Player, page: Int, allRecipes: List<RecipeInfo>) {
        val startIndex = (page - 1) * ITEMS_PER_PAGE
        val maxPage = maxPageOf(allRecipes)
        if (startIndex >= allRecipes.size && page > 1) {
            openEditGuiInternal(player, maxPage, allRecipes)
            return
        }

        val holder = EditGuiHolder(page)
        val gui = Bukkit.createInventory(holder, 54, Text.legacy("CraftGUI Edit - Page $page"))
        holder.setInventory(gui)

        for (i in 0 until ITEMS_PER_PAGE) {
            val info = allRecipes.getOrNull(startIndex + i) ?: break
            gui.setItem(i, createRecipeIcon(info))
        }

        if (page > 1) {
            gui.setItem(45, createNavItem(Material.ARROW, "&e前のページへ"))
        }
        if (page < maxPage) {
            gui.setItem(53, createNavItem(Material.ARROW, "&a次のページへ"))
        }
        gui.setItem(49, createNavItem(Material.BARRIER, "&c閉じる"))

        val uuid = player.uniqueId
        isNavigating.add(uuid)
        player.openInventory(gui)
        openEditGuis[uuid] = page
        playerEditList[uuid] = allRecipes
        Bukkit.getScheduler().runTask(plugin, Runnable { isNavigating.remove(uuid) })
    }

    private fun createRecipeIcon(info: RecipeInfo): ItemStack {
        val guiIcon = plugin.getRecipeById(info.id)?.guiIcon
        val item = guiIcon?.clone() ?: createNavItem(Material.BARRIER, "&c[ロードエラー or 無効]")

        item.editMeta { meta ->
            val currentName = meta.displayName() ?: Component.text(info.id)
            meta.displayName(Text.itemLine(Text.legacy("&e[編集] "), currentName))

            val lore = meta.lore().orEmpty().toMutableList()
            lore.add(Component.empty())
            lore.add(Text.item("&7ID: ${info.id}"))
            lore.add(Text.item("&7場所: page${info.page}.${info.slot}"))
            if (!info.isEnabled) lore.add(Text.item("&cenabled: false"))
            if (!info.isCraftable) lore.add(Text.item("&ccraftable: false"))
            lore.add(Text.item("&aクリックしてこのレシピを編集"))
            meta.lore(lore)
        }
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        if (topInventory.holder !is EditGuiHolder) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val uuid = player.uniqueId

        if (uuid in isNavigating) return
        val currentPage = openEditGuis[uuid] ?: return

        val allRecipes = playerEditList[uuid]
        if (allRecipes == null) {
            player.closeInventory()
            plugin.sendMessage(player, "&cセッションが切れました。もう一度/craftgui editを実行してください。")
            return
        }

        when (val slot = event.rawSlot) {
            45 -> if (currentPage > 1) openEditGuiInternal(player, currentPage - 1, allRecipes)
            53 -> if (currentPage < maxPageOf(allRecipes)) openEditGuiInternal(player, currentPage + 1, allRecipes)
            49 -> player.closeInventory()
            in 0 until ITEMS_PER_PAGE -> {
                val info = allRecipes.getOrNull((currentPage - 1) * ITEMS_PER_PAGE + slot) ?: return
                val recipeData = plugin.getRecipeById(info.id)
                if (recipeData == null) {
                    plugin.sendMessage(player, "&cエラー: このレシピは現在メモリにロードされていません。")
                    plugin.sendMessage(player, "&7編集するには、一度正常な状態に戻してから再度試してください。")
                    return
                }
                player.closeInventory()
                registerGuiManager.createAndOpenGuiFromEdit(player, info.page, info.slot, info.id, recipeData, currentPage)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val topInventory = event.view.topInventory
        if (topInventory.holder !is EditGuiHolder) return

        val uuid = event.player.uniqueId
        if (uuid in isNavigating) return
        openEditGuis.remove(uuid)
        playerEditList.remove(uuid)
    }

    private fun loadAllRecipeInfo(): List<RecipeInfo> {
        val list = ArrayList<RecipeInfo>()
        val itemsSection = recipeConfigManager.config.getConfigurationSection("Items") ?: return list

        for (pageKey in itemsSection.getKeys(false)) {
            val page = pageKey.drop(4).toIntOrNull() ?: continue
            if (page <= 0) continue
            val pageSection = itemsSection.getConfigurationSection(pageKey) ?: continue

            for (slotKey in pageSection.getKeys(false)) {
                val slot = slotKey.toIntOrNull() ?: continue
                if (slot < 0) continue

                val id = pageSection.getString("$slotKey.id")
                if (!id.isNullOrEmpty()) {
                    val enabled = pageSection.getBoolean("$slotKey.enabled", true)
                    val craftable = pageSection.getBoolean("$slotKey.craftable", true)
                    list.add(RecipeInfo(id, page, slot, enabled, craftable))
                }
            }
        }
        list.sortBy { it.id }
        return list
    }

    private fun createNavItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        item.itemMeta?.let { meta ->
            meta.displayName(Text.item(name))
            item.itemMeta = meta
        }
        return item
    }

    companion object {
        private const val ITEMS_PER_PAGE = 45
    }
}
