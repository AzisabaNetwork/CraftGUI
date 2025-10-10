package net.azisaba.craftgui;

import de.tr7zw.changeme.nbtapi.NBT;
import net.azisaba.craftgui.command.CraftGuiCommand;
import net.azisaba.craftgui.data.PlayerDataManager;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeLoader;
import net.azisaba.craftgui.listener.PlayerJoinListener;
import net.azisaba.craftgui.listener.PlayerQuitListener;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.manager.GuiManager;
import net.azisaba.craftgui.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftGUI extends JavaPlugin {

    private String prefix;
    private MapUtil mapUtil;
    private FileLogger fileLogger;
    private AssetDownloadUtil assetDownloadUtil;
    private ItemNameUtil itemNameUtil;
    private MythicItemUtil mythicItemUtil;
    private InventoryUtil inventoryUtil;
    private ConfigUtil configUtil;
    private GuiManager guiManager;
    private RecipeLoader recipeLoader;
    private Map<String, RecipeData> recipesById = new HashMap<>();
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        if (!NBT.preloadApi()) {
            getLogger().warning("NBT-APIの初期化に失敗しました。プラグインを無効化します。");
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

        this.prefix = ChatColor.translateAlternateColorCodes('&', config.getString("prefix", "&8[&aCraftGUI&8] &r"));
        this.playerDataManager = new PlayerDataManager(this);
        this.configUtil = new ConfigUtil(this);
        this.mapUtil = new MapUtil();
        this.assetDownloadUtil = new AssetDownloadUtil(this);
        List<String> languages = config.getStringList("languages");
        this.assetDownloadUtil.downloadAndLoadLanguages(languages);
        this.itemNameUtil = new ItemNameUtil(assetDownloadUtil);
        this.mythicItemUtil = new MythicItemUtil(itemNameUtil);
        this.inventoryUtil = new InventoryUtil(mythicItemUtil, mapUtil);
        this.fileLogger = new FileLogger(this, mythicItemUtil, inventoryUtil);

        configUtil.checkAndUpdate();

        this.recipeLoader = new RecipeLoader(this, mythicItemUtil);
        Map<Integer, Map<Integer, RecipeData>> loadedItems = recipeLoader.loadAllItems(config);
        this.recipesById.clear();
        loadedItems.values().forEach(page -> page.values().forEach(recipe -> {
            if (recipe.isEnabled()) {
                recipesById.put(recipe.getId().toLowerCase(), recipe);
            }
        }));
        Map<String, List<String>> loadedLores = recipeLoader.loadLores(config);
        GuiUtil guiUtil = new GuiUtil(inventoryUtil, mythicItemUtil, loadedLores, mapUtil);
        this.guiManager = new GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems, mythicItemUtil);

        CraftGuiCommand commandHandler = new CraftGuiCommand(this, recipeLoader, mapUtil, guiManager, inventoryUtil, mythicItemUtil, fileLogger);
        this.getCommand("craftgui").setExecutor(commandHandler);
        this.getCommand("craftgui").setTabCompleter(commandHandler);

        this.getServer().getPluginManager().registerEvents(guiManager, this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, mapUtil), this);
        this.getServer().getPluginManager().registerEvents(new PlayerQuitListener(this, mapUtil), this);

        logConfigSummary(loadedItems, recipeLoader.getErrorDetails(), assetDownloadUtil);
    }

    private void shutdown() {
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
    }

    public void reloadConfigFromUrl(String url) {
        this.getLogger().info("外部URLからconfig.ymlを上書きしています...");
        configUtil.updateConfigFromUrl(url);
        reload();
        this.getLogger().info(ChatColor.GREEN + "外部URLからconfig.ymlを上書きしました");
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
        sender.sendMessage(this.prefix + message);
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
}