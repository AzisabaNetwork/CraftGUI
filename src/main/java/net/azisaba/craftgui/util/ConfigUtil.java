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

    public ConfigUtil(CraftGUI plugin) {
        this.plugin = plugin;
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

    public void updateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File oldFolder = new File(plugin.getDataFolder(), "old");

        if (!oldFolder.exists()) {
            oldFolder.mkdirs();
        }

        String oldFileName = "config-v" + plugin.getConfig().getString("configVersion", "0.0") + ".yml";
        Path oldPath = Paths.get(oldFolder.getAbsolutePath(), oldFileName);

        try {
            Files.move(configFile.toPath(), oldPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info(ChatColor.YELLOW + "新しいバージョンのconfig.ymlが必要です");
            plugin.getLogger().info(ChatColor.YELLOW + "config.ymlの更新をしています...");
        } catch (IOException e) {
            plugin.getLogger().severe("config.ymlの移動に失敗しました: " + e.getMessage());
        }

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getLogger().info(ChatColor.GREEN + "config.ymlを最新バージョンに更新しました");
    }
}
