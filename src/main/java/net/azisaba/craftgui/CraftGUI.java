package net.azisaba.craftgui;

import net.azisaba.craftgui.command.CraftGuiCommand;
import net.azisaba.craftgui.data.PlayerDataManager;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeLoader;
import net.azisaba.craftgui.gui.CraftGuiHolder;
import net.azisaba.craftgui.listener.PlayerJoinListener;
import net.azisaba.craftgui.listener.PlayerQuitListener;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.manager.EditGuiManager;
import net.azisaba.craftgui.manager.GuiManager;
import net.azisaba.craftgui.manager.RecipeConfigManager;
import net.azisaba.craftgui.manager.RegisterGuiManager;
import net.azisaba.craftgui.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

public final class CraftGUI extends JavaPlugin {

    private Component prefix;
    private MapUtil mapUtil;
    private FileLogger fileLogger;
    private ItemNameUtil itemNameUtil;
    private MythicItemUtil mythicItemUtil;
    private InventoryUtil inventoryUtil;
    private ConfigUtil configUtil;
    private RecipeConfigManager recipeConfigManager;
    private GuiManager guiManager;
    private RegisterGuiManager registerGuiManager;
    private EditGuiManager editGuiManager;
    private RecipeLoader recipeLoader;
    private final Map<String, RecipeData> recipesById = new HashMap<>();
    private PlayerDataManager playerDataManager;

    private BukkitTask autoReloadTask;

    @Override
    public void onEnable() {
        startup();
        getComponentLogger().info(Component.text("CraftGUI has been enabled.", NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        shutdown();
        getComponentLogger().info(Component.text("CraftGUI has been disabled.", NamedTextColor.RED));
    }

    private void startup() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();

        this.recipeConfigManager = new RecipeConfigManager(this);

        String rawPrefix = config.getString("prefix", "&7[&aCraftGUI&7] &r");
        this.prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix);

        this.playerDataManager = new PlayerDataManager(this);
        this.configUtil = new ConfigUtil(this, recipeConfigManager);

        configUtil.checkAndUpdate();
        reloadConfig();
        config = getConfig();

        recipeConfigManager.reloadConfig();
        FileConfiguration recipesConfig = recipeConfigManager.getConfig();

        this.mapUtil = new MapUtil();

        this.itemNameUtil = new ItemNameUtil();
        this.mythicItemUtil = new MythicItemUtil(this, itemNameUtil);
        this.mythicItemUtil.rebuildCache();
        this.inventoryUtil = new InventoryUtil(mythicItemUtil, mapUtil);
        this.fileLogger = new FileLogger(this, mythicItemUtil, inventoryUtil);

        this.recipeLoader = new RecipeLoader(this, mythicItemUtil);
        Map<Integer, Map<Integer, RecipeData>> loadedItems = recipeLoader.loadAllItems(recipesConfig);
        updateRecipesById(loadedItems);

        Map<String, List<String>> loadedLores = recipeLoader.loadLores(recipesConfig);
        GuiUtil guiUtil = new GuiUtil(inventoryUtil, mythicItemUtil, loadedLores, mapUtil);

        this.guiManager = new GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems, mythicItemUtil);
        this.registerGuiManager = new RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil);
        this.editGuiManager = new EditGuiManager(this, recipeConfigManager, registerGuiManager);

        setupCommands();
        registerEvents();

        logConfigSummary(loadedItems, recipeLoader.getErrorDetails());

        startAutoReloadTask();
    }

    private void startAutoReloadTask() {
        if (autoReloadTask != null) {
            autoReloadTask.cancel();
        }

        int interval = getConfig().getInt("auto-reload-interval-minutes", 0);
        if (interval > 0) {
            long ticks = interval * 60L * 20L;
            autoReloadTask = new BukkitRunnable() {
                @Override
                public void run() {
                    performSafeReload(null);
                }
            }.runTaskTimer(this, ticks, ticks);
            getComponentLogger().info(Component.text("自動リロードが有効です: " + interval + " 分間隔", NamedTextColor.AQUA));
        }
    }

    private void updateRecipesById(Map<Integer, Map<Integer, RecipeData>> loadedItems) {
        this.recipesById.clear();
        loadedItems.values().forEach(page -> page.values().forEach(recipe -> {
            if (recipe.isEnabled()) {
                recipesById.put(recipe.getId().toLowerCase(), recipe);
            }
        }));
    }

    private void registerEvents() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(guiManager, this);
        pm.registerEvents(mythicItemUtil, this);
        pm.registerEvents(new PlayerJoinListener(this, mapUtil), this);
        pm.registerEvents(new PlayerQuitListener(this, mapUtil), this);
        pm.registerEvents(registerGuiManager, this);
        pm.registerEvents(editGuiManager, this);
    }

    private void setupCommands() {
        CraftGuiCommand commandHandler = new CraftGuiCommand(this, recipeLoader, mapUtil, guiManager, inventoryUtil, mythicItemUtil, configUtil, fileLogger, registerGuiManager, recipeConfigManager, editGuiManager);
        var cmd = getCommand("craftgui");
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }
    }

    private void shutdown() {
        if (this.autoReloadTask != null) {
            this.autoReloadTask.cancel();
        }
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
    }

    public void performSafeReload(CommandSender reloader) {
        Component startMessage = prefix.append(Component.text("レシピデータをリロードしています...", NamedTextColor.YELLOW));

        if (reloader != null) {
            reloader.sendMessage(startMessage);
        } else {
            getComponentLogger().info(Component.text("自動リロードを開始します...", NamedTextColor.YELLOW));
        }

        Map<UUID, Integer> playersToRestore = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof CraftGuiHolder) {
                playersToRestore.put(player.getUniqueId(), mapUtil.getPlayerPage(player.getUniqueId()));
                player.closeInventory();
            }
        }

        hotReload();

        new BukkitRunnable() {
            @Override
            public void run() {
                playersToRestore.forEach((uuid, page) -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        try {
                            guiManager.openCraftGUI(player, page);
                        } catch (Exception e) {
                            getLogger().log(Level.SEVERE, "リロード後のGUI復元に失敗しました", e);
                        }
                    }
                });

                Component completeMessage = prefix.append(Component.text("リロードが完了しました。", NamedTextColor.GREEN));
                if (reloader != null) {
                    reloader.sendMessage(completeMessage);
                }
            }
        }.runTask(this);
    }

    public void hotReload() {
        reloadConfig();
        recipeConfigManager.reloadConfig();
        configUtil.checkAndUpdate();

        this.recipeLoader = new RecipeLoader(this, mythicItemUtil);
        Map<Integer, Map<Integer, RecipeData>> loadedItems = recipeLoader.loadAllItems(recipeConfigManager.getConfig());
        updateRecipesById(loadedItems);

        HandlerList.unregisterAll(this);

        Map<String, List<String>> loadedLores = recipeLoader.loadLores(recipeConfigManager.getConfig());
        GuiUtil guiUtil = new GuiUtil(inventoryUtil, mythicItemUtil, loadedLores, mapUtil);

        this.guiManager = new GuiManager(this, mapUtil, guiUtil, inventoryUtil, fileLogger, loadedItems, mythicItemUtil);
        this.registerGuiManager = new RegisterGuiManager(this, mythicItemUtil, recipeConfigManager, inventoryUtil);
        this.editGuiManager = new EditGuiManager(this, recipeConfigManager, registerGuiManager);

        registerEvents();
        setupCommands();
        logConfigSummary(loadedItems, recipeLoader.getErrorDetails());
        startAutoReloadTask();
    }

    private void logConfigSummary(Map<Integer, Map<Integer, RecipeData>> loadedItems, List<String> errorDetails) {
        int totalItems = loadedItems.values().stream().mapToInt(Map::size).sum();
        getComponentLogger().info(Component.text("--- CraftGUI Loading Summary ---", NamedTextColor.GRAY));

        var mm = MiniMessage.miniMessage();
        getComponentLogger().info(mm.deserialize("<green>  ✓ レシピ読み込み成功: <white>" + totalItems + " 件"));

        if (!errorDetails.isEmpty()) {
            getComponentLogger().info(mm.deserialize("<red>  ✘ エラー発生: <white>" + errorDetails.size() + " 件"));
            errorDetails.forEach(err -> getComponentLogger().warn(Component.text("    - " + err, NamedTextColor.GOLD)));
        }
        getComponentLogger().info(Component.text("------------------------------------", NamedTextColor.GRAY));
    }

    public void sendMessage(CommandSender sender, Component message) {
        if (message == null) return;
        sender.sendMessage(prefix.append(message));
    }

    public Component getPrefixComponent() { return prefix; }
    public RecipeData getRecipeById(String id) { return recipesById.get(id.toLowerCase()); }
    public Set<String> getRecipeIds() { return recipesById.keySet(); }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public EditGuiManager getEditGuiManager() { return editGuiManager; }
}