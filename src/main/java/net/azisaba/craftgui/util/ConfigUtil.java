package net.azisaba.craftgui.util;

import net.azisaba.craftgui.CraftGUI;
import org.bukkit.ChatColor;

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
    private static final String LATEST_CONFIG_VERSION = "1.5";

    public ConfigUtil(CraftGUI plugin) {
        this.plugin = plugin;
    }

    public void checkAndUpdate() {
        String currentVersion = plugin.getConfig().getString("configVersion", "0.0");
        if (!LATEST_CONFIG_VERSION.equals(currentVersion)) {
            updateConfig(currentVersion);
        } else {
            plugin.getLogger().info(ChatColor.GREEN + "config.ymlは最新バージョンです");
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
            plugin.getLogger().info(ChatColor.YELLOW + "古いconfig.ymlをoldフォルダにバックアップしました。");
            plugin.getLogger().info(ChatColor.YELLOW + "config.ymlを更新しています...");
        } catch (IOException e) {
            plugin.getLogger().severe("config.ymlのバックアップに失敗しました: " + e.getMessage());
        }

        plugin.saveDefaultConfig();
        plugin.reloadConfig(); // 新しいconfigをすぐに読み込む
        plugin.getLogger().info(ChatColor.GREEN + "config.ymlを最新バージョン(" + LATEST_CONFIG_VERSION + ")に更新しました");
    }

    public void updateConfigFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            try (InputStream in = url.openStream()) {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("config.ymlを" + urlString + "からダウンロードしました");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("URLからのconfig.ymlのダウンロードに失敗しました: " + e.getMessage());
        }
    }
}
