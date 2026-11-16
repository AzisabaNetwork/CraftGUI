package net.azisaba.craftgui.manager

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.data.RecipeData
import net.azisaba.craftgui.gui.CraftGuiHolder
import net.azisaba.craftgui.logging.FileLogger
import net.azisaba.craftgui.platform.PlatformAdapter
import net.azisaba.craftgui.util.GuiUtil
import net.azisaba.craftgui.util.InventoryUtil
import net.azisaba.craftgui.util.ItemUtil
import net.azisaba.craftgui.util.MapUtil
import net.azisaba.craftgui.util.MythicItemUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

typealias RecipePage = Map<Int, RecipeData>

class GuiManager(
    private val plugin: CraftGUI,
    private val mapUtil: MapUtil,
    private val guiUtil: GuiUtil,
    private val inventoryUtil: InventoryUtil,
    private val fileLogger: FileLogger,
    private val loadedItems: MutableMap<Int, MutableMap<Int, RecipeData>>,
    @Suppress("unused") private val mythicItemUtil: MythicItemUtil,
    private val platformAdapter: PlatformAdapter,
) : Listener {

    private val allEnabledRecipes = ArrayList<RecipeData>()

    init {
        rebuildEnabledRecipeList()
    }

    fun hotUpdateRecipe(recipeData: RecipeData, page: Int, slot: Int) {
        loadedItems.getOrPut(page) { LinkedHashMap() }[slot] = recipeData
        rebuildEnabledRecipeList()
        plugin.logger.info("GuiManager recipe list updated.")
    }

    fun openCraftGUI(player: Player, page: Int) {
        val snapshot = inventoryUtil.createSnapshot(player)
        val isCompactView = isCompactLayout(player)
        val visiblePages = getVisiblePages(player, isCompactView, snapshot)
        val resolvedPage = resolvePage(page, visiblePages.size)

        val title = "CraftGUI - " + if (isCompactView) "All Items (Page $resolvedPage)" else "Page $resolvedPage"
        val holder = CraftGuiHolder(resolvedPage)
        val playerGui = Bukkit.createInventory(holder, 54, title)
        holder.setInventory(playerGui)

        for ((slot, recipe) in visiblePages[resolvedPage - 1]) {
            val item = guiUtil.createBaseRecipeItem(recipe)
            guiUtil.updateLoreForPlayer(item, recipe, player, snapshot)
            playerGui.setItem(slot, item)
        }

        mapUtil.setPlayerPage(player.uniqueId, resolvedPage)
        setNavigationButtons(playerGui, resolvedPage, player, visiblePages.size)
        player.openInventory(playerGui)
    }

    @EventHandler
    fun onGuiClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val topInventory = platformAdapter.getTopInventory(event.view) ?: return
        val holder = topInventory.holder as? CraftGuiHolder ?: return
        val currentPage = holder.page

        event.isCancelled = true
        val slot = event.rawSlot
        if (slot < 0 || slot >= topInventory.size) return

        if (slot in 45 until 54) {
            handleNavigationAndSettings(player, event, currentPage)
            return
        }

        val visiblePages = getVisiblePages(player, isCompactLayout(player), null)
        val resolvedPage = resolvePage(currentPage, visiblePages.size)
        val clickedRecipe = visiblePages[resolvedPage - 1][slot] ?: return
        if (!clickedRecipe.isEnabled) return

        if (!clickedRecipe.isCraftable) {
            plugin.sendMessage(player, "&cこのアイテムはクラフトできません。")
            playSoundIfEnabled(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        attemptCraft(player, clickedRecipe, event.click)
    }

    private fun attemptCraft(player: Player, recipe: RecipeData, click: ClickType) {
        val maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.requiredBranches, recipe.resultItems)
        if (maxCraftable <= 0) {
            plugin.sendMessage(player, "&cクラフトに必要なアイテムが不足しているか、インベントリに空きがありません。")
            playSoundIfEnabled(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val requested = when {
            click.isLeftClick -> 1L
            click.isRightClick -> maxCraftable
            else -> 0L
        }
        if (requested <= 0) return
        val craftAmount = minOf(requested, maxCraftable).toInt()

        val bestBranch = inventoryUtil.getBestBranch(player, recipe.requiredBranches, craftAmount)
        bestBranch?.materials?.forEach { inventoryUtil.removeItems(player, it, it.amount * craftAmount) }
        inventoryUtil.giveResultItems(player, recipe.resultItems, craftAmount)
        fileLogger.logCraft(player, recipe, bestBranch, craftAmount)
        plugin.sendMessage(player, "${ChatColor.GREEN}${craftAmount}回変換しました。")
        playSoundIfEnabled(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        val currentPage = mapUtil.getPlayerPage(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable { openCraftGUI(player, currentPage) })
    }

    private fun handleNavigationAndSettings(player: Player, event: InventoryClickEvent, currentPage: Int) {
        val uuid = player.uniqueId
        val slot = event.rawSlot

        if (slot == 49) {
            player.closeInventory()
            return
        }

        if (slot == 47) {
            mapUtil.toggleSoundState(uuid)
            playSoundIfEnabled(player, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
            val maxPage = getVisiblePages(player, isCompactLayout(player), null).size
            setNavigationButtons(event.inventory, currentPage, player, maxPage)
            return
        }

        val newPage = when (slot) {
            45 -> if (currentPage > 1) currentPage - 1 else return
            53 -> {
                val maxPage = getVisiblePages(player, isCompactLayout(player), null).size
                if (currentPage < maxPage) currentPage + 1 else return
            }
            46 -> {
                mapUtil.toggleCraftableOnlyState(uuid)
                1
            }
            48 -> {
                mapUtil.toggleStashEnabled(uuid)
                val msg = if (mapUtil.isStashEnabled(uuid)) {
                    "アイテムをStashに送るように切り替えました。"
                } else {
                    "通常どおりインベントリに送るように切り替えました。"
                }
                plugin.sendMessage(player, "${ChatColor.GREEN}$msg")
                currentPage
            }
            50 -> {
                mapUtil.toggleCompactViewState(uuid)
                1
            }
            51 -> {
                mapUtil.toggleShowResultItems(uuid)
                currentPage
            }
            52 -> {
                mapUtil.toggleLoreState(uuid)
                currentPage
            }
            else -> return
        }

        mapUtil.setPlayerPage(uuid, newPage)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            playSoundIfEnabled(player, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
            openCraftGUI(player, newPage)
        })
    }

    private fun setNavigationButtons(gui: Inventory, currentPage: Int, player: Player, maxPage: Int) {
        val uuid = player.uniqueId

        if (currentPage > 1) {
            gui.setItem(45, createNavItem(Material.ARROW, "${ChatColor.YELLOW}前のページへ"))
        }
        if (currentPage < maxPage) {
            gui.setItem(53, createNavItem(Material.ARROW, "${ChatColor.GREEN}次のページへ"))
        }

        val craftableOnly = mapUtil.isCraftableOnlyEnabled(uuid)
        gui.setItem(
            46,
            createNavItem(
                if (craftableOnly) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE,
                "${ChatColor.GREEN}クラフト可能のみ表示",
                listOf(settingLine(craftableOnly)),
            ),
        )

        val soundOn = mapUtil.isSoundToggleOn(uuid)
        gui.setItem(
            47,
            createNavItem(
                if (soundOn) Material.JUKEBOX else Material.NOTE_BLOCK,
                "${ChatColor.GREEN}サウンド設定",
                listOf(settingLine(soundOn)),
            ),
        )

        val isStashEnabled = mapUtil.isStashEnabled(uuid)
        val stashStatus = if (isStashEnabled) "${ChatColor.AQUA}Stashへ送る" else "${ChatColor.GOLD}インベントリへ送る"
        gui.setItem(
            48,
            createNavItem(
                if (isStashEnabled) Material.ENDER_CHEST else Material.CHEST,
                "${ChatColor.GREEN}アイテム受け取り先",
                listOf("${ChatColor.GRAY}現在の設定: $stashStatus", "${ChatColor.GRAY}クリックで切り替え"),
            ),
        )

        gui.setItem(49, createNavItem(Material.BARRIER, "${ChatColor.RED}閉じる"))

        val isCompactView = mapUtil.isCompactViewEnabled(uuid)
        val modeName = if (isCompactView) "${ChatColor.AQUA}コンパクト" else "${ChatColor.GRAY}デフォルト"
        gui.setItem(
            50,
            createNavItem(
                if (isCompactView) Material.WATER_BUCKET else Material.BUCKET,
                "${ChatColor.GREEN}表示モード",
                listOf("${ChatColor.GRAY}現在のモード: $modeName"),
            ),
        )

        val showResult = mapUtil.isShowResultItems(uuid)
        val onBottleMat = ItemUtil.getMaterialSafe("HONEY_BOTTLE", Material.GLASS_BOTTLE)
        gui.setItem(
            51,
            createNavItem(
                if (showResult) onBottleMat else Material.GLASS_BOTTLE,
                "${ChatColor.GREEN}結果アイテム表示",
                listOf(settingLine(showResult)),
            ),
        )

        val loreOn = mapUtil.isLoreToggledOn(uuid)
        gui.setItem(
            52,
            createNavItem(
                if (loreOn) Material.LIME_DYE else Material.GRAY_DYE,
                "${ChatColor.GREEN}説明文表示",
                listOf(settingLine(loreOn)),
            ),
        )
    }

    private fun settingLine(enabled: Boolean): String =
        "${ChatColor.GRAY}現在の設定: " + if (enabled) "${ChatColor.AQUA}ON" else "${ChatColor.RED}OFF"

    private fun createNavItem(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        item.itemMeta?.let { meta ->
            meta.setDisplayName(name)
            meta.lore = lore
            item.itemMeta = meta
        }
        return item
    }

    private fun playSoundIfEnabled(player: Player, sound: Sound, volume: Float, pitch: Float) {
        if (mapUtil.isSoundToggleOn(player.uniqueId)) {
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun rebuildEnabledRecipeList() {
        allEnabledRecipes.clear()
        for (page in loadedItems.keys.sorted()) {
            val pageItems = loadedItems[page] ?: continue
            for (slot in pageItems.keys.sorted()) {
                val recipe = pageItems[slot] ?: continue
                if (recipe.isEnabled) allEnabledRecipes.add(recipe)
            }
        }
    }

    private fun getVisiblePages(
        player: Player,
        isCompactView: Boolean,
        snapshot: InventoryUtil.InventorySnapshot?,
    ): List<RecipePage> {
        val craftableOnly = mapUtil.isCraftableOnlyEnabled(player.uniqueId)
        val effectiveSnapshot = if (craftableOnly && snapshot == null) inventoryUtil.createSnapshot(player) else snapshot

        fun isVisible(recipe: RecipeData): Boolean =
            recipe.isEnabled && (!craftableOnly || isCurrentlyCraftable(recipe, effectiveSnapshot!!))

        if (!isCompactView) {
            return createDefaultPages(::isVisible)
        }
        return createCompactPages(allEnabledRecipes.filter(::isVisible))
    }

    private fun createCompactPages(visibleRecipes: List<RecipeData>): List<RecipePage> {
        val pages: List<RecipePage> = visibleRecipes.chunked(ITEMS_PER_PAGE).map { chunk ->
            chunk.withIndex().associateTo(LinkedHashMap()) { (index, recipe) -> index to recipe }
        }
        return pages.ifEmpty { listOf(LinkedHashMap()) }
    }

    private fun createDefaultPages(isVisible: (RecipeData) -> Boolean): List<RecipePage> {
        val pages = ArrayList<RecipePage>()
        for (page in loadedItems.keys.sorted()) {
            val pageItems = loadedItems[page] ?: continue
            val visiblePageItems = LinkedHashMap<Int, RecipeData>()
            for ((slot, recipe) in pageItems.entries.sortedBy { it.key }) {
                if (isVisible(recipe)) visiblePageItems[slot] = recipe
            }
            if (visiblePageItems.isNotEmpty()) pages.add(visiblePageItems)
        }
        return pages.ifEmpty { listOf(LinkedHashMap()) }
    }

    private fun isCurrentlyCraftable(recipe: RecipeData, snapshot: InventoryUtil.InventorySnapshot): Boolean =
        recipe.isCraftable &&
            inventoryUtil.calculateMaxCraftableAmount(snapshot, recipe.requiredBranches, recipe.resultItems) > 0

    private fun resolvePage(requestedPage: Int, maxPage: Int): Int = when {
        maxPage <= 0 || requestedPage < 1 -> 1
        else -> minOf(requestedPage, maxPage)
    }

    private fun isCompactLayout(player: Player): Boolean =
        mapUtil.isCompactViewEnabled(player.uniqueId) || mapUtil.isCraftableOnlyEnabled(player.uniqueId)

    companion object {
        private const val ITEMS_PER_PAGE = 45
    }
}
