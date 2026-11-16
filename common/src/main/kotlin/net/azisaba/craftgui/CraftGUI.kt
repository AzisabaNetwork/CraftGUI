package net.azisaba.craftgui

import net.azisaba.craftgui.api.CraftGUIAPI
import net.azisaba.craftgui.api.CraftGUIAPIImpl
import net.azisaba.craftgui.command.CraftGuiCommand
import net.azisaba.craftgui.data.PlayerDataManager
import net.azisaba.craftgui.data.RecipeData
import net.azisaba.craftgui.data.RecipeLoader
import net.azisaba.craftgui.gui.CraftGuiHolder
import net.azisaba.craftgui.listener.PlayerJoinListener
import net.azisaba.craftgui.listener.PlayerQuitListener
import net.azisaba.craftgui.logging.FileLogger
import net.azisaba.craftgui.manager.EditGuiManager
import net.azisaba.craftgui.manager.GuiManager
import net.azisaba.craftgui.manager.RecipeConfigManager
import net.azisaba.craftgui.manager.RegisterGuiManager
import net.azisaba.craftgui.mythic.MythicAdapter
import net.azisaba.craftgui.mythic.MythicAdapterProvider
import net.azisaba.craftgui.util.AssetDownloadUtil
import net.azisaba.craftgui.util.ConfigUtil
import net.azisaba.craftgui.util.GuiUtil
import net.azisaba.craftgui.util.InventoryUtil
import net.azisaba.craftgui.util.ItemNameUtil
import net.azisaba.craftgui.util.MapUtil
import net.azisaba.craftgui.util.MythicItemUtil
import net.azisaba.craftgui.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level

class CraftGUI : JavaPlugin() {

    lateinit var prefix: Component
        private set
    lateinit var mythicAdapter: MythicAdapter
        private set
    lateinit var playerDataManager: PlayerDataManager
        private set
    lateinit var recipeLoader: RecipeLoader
        private set
    lateinit var editGuiManager: EditGuiManager
        private set

    @get:JvmName("getAPI")
    lateinit var api: CraftGUIAPI
        private set

    private lateinit var mapUtil: MapUtil
    private lateinit var fileLogger: FileLogger
    private lateinit var assetDownloadUtil: AssetDownloadUtil
    private lateinit var itemNameUtil: ItemNameUtil
    private lateinit var mythicItemUtil: MythicItemUtil
    private lateinit var inventoryUtil: InventoryUtil
    private lateinit var configUtil: ConfigUtil
    private lateinit var recipeConfigManager: RecipeConfigManager
    private lateinit var commandHandler: CraftGuiCommand
    private lateinit var guiManager: GuiManager
    private lateinit var registerGuiManager: RegisterGuiManager
    private val recipesById = HashMap<String, RecipeData>()

    private var autoReloadTask: BukkitTask? = null

    override fun onEnable() {
        instance = this
        startup()
        logger.info("CraftGUI has been enabled.")
    }

    override fun onDisable() {
        shutdown()
        logger.info("CraftGUI has been disabled.")
    }

    fun reload() {
        shutdown()
        startup()
    }

    private fun startup() {
        saveDefaultConfig()
        reloadConfig()

        mythicAdapter = MythicAdapterProvider.createAdapter()
        logger.info("MythicMobs adapter loaded: ${mythicAdapter.javaClass.simpleName}")

        recipeConfigManager = RecipeConfigManager(this)
        prefix = Text.legacy(config.getString("prefix", DEFAULT_PREFIX) ?: DEFAULT_PREFIX)
        playerDataManager = PlayerDataManager(this)
        configUtil = ConfigUtil(this, recipeConfigManager)

        configUtil.checkAndUpdate()
        reloadConfig()

        recipeConfigManager.reloadConfig()
        val recipesConfig = recipeConfigManager.config

        mapUtil = MapUtil()
        assetDownloadUtil = AssetDownloadUtil(this)
        assetDownloadUtil.downloadAndLoadLanguages(config.getStringList("languages"))
        itemNameUtil = ItemNameUtil(assetDownloadUtil)
        mythicItemUtil = MythicItemUtil(this, itemNameUtil, mythicAdapter)
        mythicItemUtil.rebuildCache()
        inventoryUtil = InventoryUtil(mythicItemUtil, mapUtil)
        fileLogger = FileLogger(this, mythicItemUtil, inventoryUtil)

        val loadedItems = loadRecipes(recipesConfig)
        val guiUtil = GuiUtil(inventoryUtil, mythicItemUtil, recipeLoader.loadLores(recipesConfig), mapUtil)
        guiManager = GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems)
        registerGuiManager = RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil)
        editGuiManager = EditGuiManager(this, recipeConfigManager, registerGuiManager)
        api = CraftGUIAPIImpl(mapUtil)

        setupCommands()

        mythicAdapter.registerReloadListener(this, Runnable { mythicItemUtil.rebuildCache() })

        registerListeners(
            guiManager,
            PlayerJoinListener(this, mapUtil),
            PlayerQuitListener(this, mapUtil),
            registerGuiManager,
            editGuiManager,
        )

        logConfigSummary(loadedItems, recipeLoader.errorDetails, assetDownloadUtil)
    }

    /** レシピを読み込み，ID 索引を作り直す */
    private fun loadRecipes(recipesConfig: FileConfiguration): MutableMap<Int, MutableMap<Int, RecipeData>> {
        recipeLoader = RecipeLoader(this, mythicItemUtil)
        val loadedItems = recipeLoader.loadAllItems(recipesConfig)
        recipesById.clear()
        loadedItems.values.forEach { page ->
            page.values.filter { it.isEnabled }.forEach { recipesById[it.id.lowercase()] = it }
        }
        return loadedItems
    }

    private fun registerListeners(vararg listeners: Listener) {
        listeners.forEach { server.pluginManager.registerEvents(it, this) }
    }

    private fun setupCommands() {
        commandHandler = CraftGuiCommand(
            this, recipeLoader, mapUtil, guiManager, inventoryUtil, mythicItemUtil,
            configUtil, fileLogger, registerGuiManager, recipeConfigManager, editGuiManager,
        )
        getCommand("craftgui")?.let {
            it.setExecutor(commandHandler)
            it.tabCompleter = commandHandler
        }
    }

    private fun shutdown() {
        autoReloadTask?.let { if (!it.isCancelled) it.cancel() }
        autoReloadTask = null
        Bukkit.getScheduler().cancelTasks(this)
        HandlerList.unregisterAll(this)
    }

    fun performSafeReload(reloader: CommandSender?) {
        val startMessage = prefixed("&eレシピデータをリロードしています...")
        if (reloader != null) {
            reloader.sendMessage(startMessage)
        } else {
            Bukkit.broadcast(startMessage)
            logger.info("CraftGUIの自動リロードを開始します...")
        }

        val playersToRestore = HashMap<UUID, Int>()
        for (player in Bukkit.getOnlinePlayers()) {
            val topInventory = player.openInventory.topInventory
            if (topInventory.holder is CraftGuiHolder) {
                playersToRestore[player.uniqueId] = mapUtil.getPlayerPage(player.uniqueId)
                player.closeInventory()
            }
        }

        hotReload()

        object : BukkitRunnable() {
            override fun run() {
                for ((uuid, page) in playersToRestore) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isOnline) continue
                    try {
                        guiManager.openCraftGUI(player, page)
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "リロード後のGUI復元に失敗しました: ${player.name}", e)
                    }
                }
                if (reloader != null) {
                    reloader.sendMessage(prefixed("&aレシピデータのリロードが完了しました (Player)"))
                    reloader.sendMessage(prefixed("&7${playersToRestore.size}人のGUIを復元しました．"))
                } else {
                    Bukkit.broadcast(prefixed("&aレシピデータのリロードが完了しました (Server)"))
                    logger.info("CraftGUIの自動リロードが完了しました．")
                }
            }
        }.runTask(this)
    }

    fun hotReload() {
        reloadConfig()
        recipeConfigManager.reloadConfig()
        val recipesConfig = recipeConfigManager.config

        configUtil.checkAndUpdate()

        val loadedItems = loadRecipes(recipesConfig)
        val guiUtil = GuiUtil(inventoryUtil, mythicItemUtil, recipeLoader.loadLores(recipesConfig), mapUtil)

        mythicItemUtil.rebuildCache()

        HandlerList.unregisterAll(guiManager)
        guiManager = GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems)

        HandlerList.unregisterAll(registerGuiManager)
        registerGuiManager = RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil)

        HandlerList.unregisterAll(editGuiManager)
        editGuiManager = EditGuiManager(this, recipeConfigManager, registerGuiManager)

        registerListeners(guiManager, registerGuiManager, editGuiManager)

        setupCommands()
        logConfigSummary(loadedItems, recipeLoader.errorDetails, assetDownloadUtil)
    }

    fun hotUpdateRecipe(recipeData: RecipeData?, page: Int, slot: Int) {
        if (recipeData == null) {
            logger.warning("hotUpdateRecipeにnullのレシピが渡されました．")
            return
        }
        recipesById[recipeData.id.lowercase()] = recipeData
        guiManager.hotUpdateRecipe(recipeData, page, slot)
        logger.info("${recipeData.id}のレシピをアップデートしました．")
    }

    private fun logConfigSummary(
        loadedItems: Map<Int, Map<Int, RecipeData>>,
        errorDetails: List<String>,
        downloader: AssetDownloadUtil,
    ) {
        val totalItems = loadedItems.values.sumOf { it.size }

        logger.info("--- CraftGUI Loading Summary ---")
        logger.info("Asset Download Status:")
        val successes = downloader.successfulLoads
        val failures = downloader.failedLoads
        if (successes.isNotEmpty()) {
            logger.info("  ✓ 成功: ${successes.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            logger.info("  ✘ 失敗: ${failures.joinToString(", ")}")
        }
        if (successes.isEmpty() && failures.isEmpty()) {
            logger.info("  - ダウンロード対象の言語ファイルはありませんでした")
        }

        logger.info("")
        logger.info("Recipe Loading Status:")
        logger.info("  ✓ 成功: 正常に読み込まれたレシピ数: $totalItems")
        if (errorDetails.isNotEmpty()) {
            logger.info("  ✘ 失敗: 読み込みエラーがあったレシピ数: ${errorDetails.size}")
            logger.warning("  エラー詳細: ")
            errorDetails.forEach { logger.warning("    - $it") }
        } else {
            logger.info("  - レシピの読み込みエラーはありませんでした")
        }
        logger.info("------------------------------------")
    }

    fun sendMessage(sender: CommandSender, message: String?) {
        if (message.isNullOrEmpty()) return
        sender.sendMessage(prefixed(message))
    }

    private fun prefixed(message: String): Component = prefix.append(Text.legacy(message))

    fun getRecipeById(id: String): RecipeData? = recipesById[id.lowercase()]

    fun getRecipeIds(): Set<String> = recipesById.keys

    companion object {
        private const val DEFAULT_PREFIX = "§7[§aCraftGUI§7] §r"

        @JvmStatic
        lateinit var instance: CraftGUI
            private set
    }
}
