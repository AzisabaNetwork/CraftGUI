package net.azisaba.craftgui;

import de.tr7zw.changeme.nbtapi.NBT;
import net.azisaba.craftgui.command.CraftGuiCommand;
import net.azisaba.craftgui.data.PlayerDataManager;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeLoader;
import net.azisaba.craftgui.listener.PlayerJoinListener;
import net.azisaba.craftgui.listener.PlayerQuitListener;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.manager.EditGuiManager;
import net.azisaba.craftgui.manager.GuiManager;
import net.azisaba.craftgui.manager.RecipeConfigManager;
import net.azisaba.craftgui.manager.RegisterGuiManager;
import net.azisaba.craftgui.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

public final class CraftGUI extends JavaPlugin {

    private String prefix;
    private MapUtil mapUtil;
    private FileLogger fileLogger;
    private AssetDownloadUtil assetDownloadUtil;
    private ItemNameUtil itemNameUtil;
    private MythicItemUtil mythicItemUtil;
    private InventoryUtil inventoryUtil;
    private ConfigUtil configUtil;
    private RecipeConfigManager recipeConfigManager;
    private CraftGuiCommand commandHandler;
    private GuiManager guiManager;
    private RegisterGuiManager registerGuiManager;
    private EditGuiManager editGuiManager;
    private RecipeLoader recipeLoader;
    private Map<String, RecipeData> recipesById = new HashMap<>();
    private PlayerDataManager playerDataManager;

    public static final String CRAFT_GUI_TITLE_PREFIX = "CraftGUI - Page ";
    private BukkitTask autoReloadTask;

    @Override
    public void onEnable() {
        if (!NBT.preloadApi()) {
            getLogger().warning("NBT-APIの初期化に失敗したため，プラグインを無効化します．");
            getPluginLoader().disablePlugin(this);
            return;
        }
        startup();
        getLogger().info("CraftGUI has been enabled.");
    }

    @Override
    public void onDisable() {
        shutdown();
        getLogger().info("CraftGUI has been disabled.");
    }

    public void reload() {
        shutdown();
        startup();
    }

    private void startup() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();

        this.recipeConfigManager = new RecipeConfigManager(this);
        this.prefix = ChatColor.translateAlternateColorCodes('&', config.getString("prefix", "§7[§aCraftGUI§7] §r"));
        this.playerDataManager = new PlayerDataManager(this);
        this.configUtil = new ConfigUtil(this, recipeConfigManager);

        configUtil.checkAndUpdate();
        reloadConfig();
        config = getConfig();

        recipeConfigManager.reloadConfig();
        FileConfiguration recipesConfig = recipeConfigManager.getConfig();

        this.mapUtil = new MapUtil();
        this.assetDownloadUtil = new AssetDownloadUtil(this);
        List<String> languages = config.getStringList("languages");
        this.assetDownloadUtil.downloadAndLoadLanguages(languages);
        this.itemNameUtil = new ItemNameUtil(assetDownloadUtil);
        this.mythicItemUtil = new MythicItemUtil(this, itemNameUtil);
        this.inventoryUtil = new InventoryUtil(mythicItemUtil, mapUtil);
        this.fileLogger = new FileLogger(this, mythicItemUtil, inventoryUtil);

        this.recipeLoader = new RecipeLoader(this, mythicItemUtil);
        Map<Integer, Map<Integer, RecipeData>> loadedItems = recipeLoader.loadAllItems(recipesConfig);
        this.recipesById.clear();
        loadedItems.values().forEach(page -> page.values().forEach(recipe -> {
            if (recipe.isEnabled()) {
                recipesById.put(recipe.getId().toLowerCase(), recipe);
            }
        }));

        Map<String, List<String>> loadedLores = recipeLoader.loadLores(recipesConfig);
        GuiUtil guiUtil = new GuiUtil(inventoryUtil, mythicItemUtil, loadedLores, mapUtil);
        this.guiManager = new GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems, mythicItemUtil);
        this.registerGuiManager = new RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil);
        this.editGuiManager = new EditGuiManager(this, recipeConfigManager, registerGuiManager);

        setupCommands();

        CraftGuiCommand commandHandler = new CraftGuiCommand(this, recipeLoader, mapUtil, guiManager, inventoryUtil, mythicItemUtil, configUtil, fileLogger, registerGuiManager, recipeConfigManager, editGuiManager);
        this.getCommand("craftgui").setExecutor(commandHandler);
        this.getCommand("craftgui").setTabCompleter(commandHandler);

        this.getServer().getPluginManager().registerEvents(guiManager, this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, mapUtil), this);
        this.getServer().getPluginManager().registerEvents(new PlayerQuitListener(this, mapUtil), this);
        this.getServer().getPluginManager().registerEvents(registerGuiManager, this);
        this.getServer().getPluginManager().registerEvents(editGuiManager, this);

        logConfigSummary(loadedItems, recipeLoader.getErrorDetails(), assetDownloadUtil);
    }

    private void setupCommands() {
        this.commandHandler = new CraftGuiCommand(this, recipeLoader, mapUtil, guiManager, inventoryUtil, mythicItemUtil, configUtil, fileLogger, registerGuiManager, recipeConfigManager, editGuiManager);
        this.getCommand("craftgui").setExecutor(commandHandler);
        this.getCommand("craftgui").setTabCompleter(commandHandler);
    }

    private void shutdown() {
        if (this.autoReloadTask != null && !this.autoReloadTask.isCancelled()) {
            this.autoReloadTask.cancel();
            this.autoReloadTask = null;
        }
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
    }

    public void performSafeReload(CommandSender reloader) {
        String prefix = getPrefix();
        String startMessage = prefix + ChatColor.YELLOW + "レシピデータをリロードしています...";

        if (reloader != null) {
            reloader.sendMessage(startMessage);
        } else {
            Bukkit.broadcastMessage(startMessage);
            getLogger().info("CraftGUIの自動リロードを開始します...");
        }
        Map<UUID, Integer> playersToRestore = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTitle().startsWith(CRAFT_GUI_TITLE_PREFIX)) {
                playersToRestore.put(player.getUniqueId(), mapUtil.getPlayerPage(player.getUniqueId()));
                player.closeInventory();
            }
        }

        hotReload();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Integer> entry : playersToRestore.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        try {
                            guiManager.openCraftGUI(player, entry.getValue());
                        } catch (Exception e) {
                            getLogger().log(Level.SEVERE, "リロード後のGUI復元に失敗しました: " + player.getName(), e);
                        }
                    }
                }
                if (reloader != null) {
                    reloader.sendMessage(prefix + ChatColor.GREEN + "レシピデータのリロードが完了しました (Player)");
                    reloader.sendMessage(prefix + ChatColor.GRAY + playersToRestore.size() + "人のGUIを復元しました．");
                } else {
                    Bukkit.broadcastMessage(prefix + ChatColor.GREEN + "レシピデータのリロードが完了しました (Server)");
                    getLogger().info("CraftGUIの自動リロードが完了しました．");
                }
            }
        }.runTask(this);
    }

    public void hotReload() {
        reloadConfig();
        recipeConfigManager.reloadConfig();
        FileConfiguration recipesConfig = recipeConfigManager.getConfig();

        configUtil.checkAndUpdate();

        this.recipeLoader = new RecipeLoader(this, mythicItemUtil);
        Map<Integer, Map<Integer, RecipeData>> loadedItems = recipeLoader.loadAllItems(recipesConfig);

        this.recipesById.clear();
        loadedItems.values().forEach(page -> page.values().forEach(recipe -> {
            if (recipe.isEnabled()) {
                recipesById.put(recipe.getId().toLowerCase(), recipe);
            }
        }));

        Map<String, List<String>> loadedLores = recipeLoader.loadLores(recipesConfig);
        GuiUtil guiUtil = new GuiUtil(inventoryUtil, mythicItemUtil, loadedLores, mapUtil);

        if (this.guiManager != null) {
            HandlerList.unregisterAll(this.guiManager);
        }
        this.guiManager = new GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems, mythicItemUtil);
        this.getServer().getPluginManager().registerEvents(this.guiManager, this);

        if (this.registerGuiManager != null) {
            HandlerList.unregisterAll(this.registerGuiManager);
        }
        this.registerGuiManager = new RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil);
        this.getServer().getPluginManager().registerEvents(this.registerGuiManager, this);
        if (this.editGuiManager != null) {
            HandlerList.unregisterAll(this.editGuiManager);
        }
        this.editGuiManager = new EditGuiManager(this, recipeConfigManager, registerGuiManager);
        this.getServer().getPluginManager().registerEvents(this.editGuiManager, this);

        setupCommands();
        logConfigSummary(loadedItems, recipeLoader.getErrorDetails(), assetDownloadUtil);
    }

    public void hotUpdateRecipe(RecipeData recipeData, int page, int slot) {
        if (recipeData == null) {
            getLogger().warning("hotUpdateRecipeにnullのレシピが渡されました．");
            return;
        }

        recipesById.put(recipeData.getId().toLowerCase(), recipeData);

        if (guiManager != null) {
            guiManager.hotUpdateRecipe(recipeData, page, slot);
        }

        getLogger().info(recipeData.getId() + "のレシピをアップデートしました．");
    }

    private void logConfigSummary(Map<Integer, Map<Integer, RecipeData>> loadedItems, List<String> errorDetails, AssetDownloadUtil downloader) {
        int totalItems = 0;
        for (Map<Integer, RecipeData> page : loadedItems.values()) {
            totalItems += page.size();
        }
        int errorCount = errorDetails.size();

        this.getLogger().info("--- CraftGUI Loading Summary ---");

        this.getLogger().info("Asset Download Status:");
        List<String> successes = downloader.getSuccessfulLoads();
        List<String> failures = downloader.getFailedLoads();
        if (!successes.isEmpty()) {
            this.getLogger().info(ChatColor.GREEN + "  ✓ 成功: " + ChatColor.RESET + String.join(", ", successes));
        }
        if (!failures.isEmpty()) {
            this.getLogger().info(ChatColor.RED + "  ✘ 失敗: " + ChatColor.RESET + String.join(", ", failures));
        }
        if (successes.isEmpty() && failures.isEmpty()) {
            this.getLogger().info("  - ダウンロード対象の言語ファイルはありませんでした");
        }

        this.getLogger().info("");

        this.getLogger().info("Recipe Loading Status:");
        this.getLogger().info(ChatColor.GREEN + "  ✓ 成功: " + ChatColor.RESET + "正常に読み込まれたレシピ数: " + totalItems);
        if (errorCount > 0) {
            this.getLogger().info(ChatColor.RED + "  ✘ 失敗: " + ChatColor.RESET + "読み込みエラーがあったレシピ数: " + errorCount);
            this.getLogger().warning("  エラー詳細: ");
            for (String error : errorDetails) {
                this.getLogger().warning("    - " + error);
            }
        } else {
            this.getLogger().info("  - レシピの読み込みエラーはありませんでした");
        }
        this.getLogger().info("------------------------------------");
    }

    public void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(this.prefix + ChatColor.translateAlternateColorCodes('&', message));
    }
    public String getPrefix() {
        return prefix;
    }
    public RecipeData getRecipeById(String id) {
        return recipesById.get(id.toLowerCase());
    }
    public Set<String> getRecipeIds() {
        return recipesById.keySet();
    }
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    public RecipeLoader getRecipeLoader() {
        return recipeLoader;
    }
}