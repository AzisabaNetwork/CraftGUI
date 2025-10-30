package net.azisaba.craftgui.util;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.manager.RecipeConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ConfigUtil {

    private final CraftGUI plugin;
    private final RecipeConfigManager recipeConfigManager;
    private static final String LATEST_CONFIG_VERSION = "1.1";

    public ConfigUtil(CraftGUI plugin, RecipeConfigManager recipeConfigManager) {
        this.plugin = plugin;
        this.recipeConfigManager = recipeConfigManager;
    }

    public void checkAndUpdate() {
        migrateRecipesFromConfig();

        String currentVersion = plugin.getConfig().getString("configVersion", "0.0");
        if (!LATEST_CONFIG_VERSION.equals(currentVersion)) {
            updateConfig(currentVersion);
        } else {
            plugin.getLogger().info(ChatColor.GREEN + "config.ymlは最新バージョンです．");
        }
    }

    private void migrateRecipesFromConfig() {
        FileConfiguration mainConfig = plugin.getConfig();
        FileConfiguration recipesConfig = recipeConfigManager.getConfig();
        boolean modified = false;

        if (mainConfig.isConfigurationSection("Items")) {
            plugin.getLogger().info(ChatColor.YELLOW + "旧形式のconfig.ymlを検出しました．'Items'セクションをrecipes.ymlに移行します...");
            ConfigurationSection itemsSection = mainConfig.getConfigurationSection("Items");
            recipesConfig.set("Items", itemsSection);
            mainConfig.set("Items", null);
            modified = true;
            plugin.getLogger().info(ChatColor.GREEN + "'Items'セクションをrecipes.ymlに移行しました．");
        }

        if (mainConfig.isConfigurationSection("Lores")) {
            plugin.getLogger().info(ChatColor.YELLOW + "'Lores'セクションをrecipes.ymlに移行します...");
            ConfigurationSection loresSection = mainConfig.getConfigurationSection("Lores");
            recipesConfig.set("Lores", loresSection);
            mainConfig.set("Lores", null);
            modified = true;
            plugin.getLogger().info(ChatColor.GREEN + "'Lores'セクションを recipes.ymlに移行しました．");
        }

        if (modified) {
            plugin.saveConfig();
            recipeConfigManager.saveConfig();
            plugin.getLogger().info(ChatColor.GREEN + "レシピデータの移行が完了しました．");
        }
    }

    private void updateConfig(String oldVersion) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File oldFolder = new File(plugin.getDataFolder(), "old");

        if (!oldFolder.exists()) {
            oldFolder.mkdirs();
        }

        String oldFileName = "config-v" + oldVersion + ".yml";
        Path oldPath = Paths.get(oldFolder.getAbsolutePath(), oldFileName);

        try {
            Files.move(configFile.toPath(), oldPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info(ChatColor.YELLOW + "古いconfig.ymlをoldフォルダにバックアップしました．");
            plugin.getLogger().info(ChatColor.YELLOW + "config.ymlを更新しています...");
        } catch (IOException e) {
            plugin.getLogger().severe("config.ymlのバックアップに失敗しました: " + e.getMessage());
        }

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getLogger().info(ChatColor.GREEN + "config.ymlを最新バージョン(" + LATEST_CONFIG_VERSION + ")に更新しました");
    }

    public void updateConfigFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            try (InputStream in = url.openStream()) {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("config.ymlを" + urlString + "からダウンロードしました．");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("URLからのconfig.ymlのダウンロードに失敗しました: " + e.getMessage());
        }
    }

    public void reloadConfigFromUrl(String url) {
        plugin.getLogger().info("外部URLからconfig.ymlを上書きしています...");
        updateConfigFromUrl(url);
        plugin.performSafeReload(null);
        plugin.getLogger().info(ChatColor.GREEN + "外部URLからconfig.ymlを上書きしました．");
    }
}
