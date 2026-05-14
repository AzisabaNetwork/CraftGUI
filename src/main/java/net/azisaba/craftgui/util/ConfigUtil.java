package net.azisaba.craftgui.util;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.manager.RecipeConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
        }
    }

    public void updateConfigFromUrl(String urlString) {
        try {
            URL url = URI.create(urlString).toURL();
            try (InputStream in = url.openStream()) {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getComponentLogger().info(Component.text("URLからconfig.ymlをダウンロードしました．", NamedTextColor.GREEN));
            }
        } catch (IllegalArgumentException | IOException e) {
            plugin.getComponentLogger().error(Component.text("URLからのダウンロードに失敗しました: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    public void reloadConfigFromUrl(String url) {
        plugin.getComponentLogger().info(Component.text("外部URLからconfig.ymlを上書きしています...", NamedTextColor.YELLOW));
        updateConfigFromUrl(url);
        plugin.performSafeReload(null);
    }

    private void migrateRecipesFromConfig() {
        FileConfiguration mainConfig = plugin.getConfig();
        FileConfiguration recipesConfig = recipeConfigManager.getConfig();
        boolean modified = false;

        if (mainConfig.isConfigurationSection("Items")) {
            ConfigurationSection itemsSection = mainConfig.getConfigurationSection("Items");
            recipesConfig.set("Items", itemsSection);
            mainConfig.set("Items", null);
            modified = true;
        }

        if (mainConfig.isConfigurationSection("Lores")) {
            ConfigurationSection loresSection = mainConfig.getConfigurationSection("Lores");
            recipesConfig.set("Lores", loresSection);
            mainConfig.set("Lores", null);
            modified = true;
        }

        if (modified) {
            plugin.saveConfig();
            recipeConfigManager.saveConfig();
            plugin.getComponentLogger().info(Component.text("旧形式のデータを移行しました．", NamedTextColor.GREEN));
        }
    }

    private void updateConfig(String oldVersion) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File oldFolder = new File(plugin.getDataFolder(), "old");
        if (!oldFolder.exists()) oldFolder.mkdirs();

        String oldFileName = "config-v" + oldVersion + ".yml";
        Path oldPath = Paths.get(oldFolder.getAbsolutePath(), oldFileName);

        try {
            Files.move(configFile.toPath(), oldPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getComponentLogger().error(Component.text("バックアップ失敗: " + e.getMessage(), NamedTextColor.RED));
        }

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getComponentLogger().info(Component.text("config.ymlを更新しました(v" + LATEST_CONFIG_VERSION + ")", NamedTextColor.GREEN));
    }
}