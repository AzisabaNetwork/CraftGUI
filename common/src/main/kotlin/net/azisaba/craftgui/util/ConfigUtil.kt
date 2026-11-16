package net.azisaba.craftgui.util

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.manager.RecipeConfigManager
import org.bukkit.ChatColor
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ConfigUtil(private val plugin: CraftGUI, private val recipeConfigManager: RecipeConfigManager) {

    fun checkAndUpdate() {
        migrateRecipesFromConfig()

        val currentVersion = plugin.config.getString("configVersion", "0.0") ?: "0.0"
        if (LATEST_CONFIG_VERSION != currentVersion) {
            updateConfig(currentVersion)
        } else {
            plugin.logger.info("${ChatColor.GREEN}config.ymlは最新バージョンです．")
        }
    }

    private fun migrateRecipesFromConfig() {
        val mainConfig = plugin.config
        val recipesConfig = recipeConfigManager.config
        var modified = false

        if (mainConfig.isConfigurationSection("Items")) {
            plugin.logger.info("${ChatColor.YELLOW}旧形式のconfig.ymlを検出しました．'Items'セクションをrecipes.ymlに移行します...")
            recipesConfig.set("Items", mainConfig.getConfigurationSection("Items"))
            mainConfig.set("Items", null)
            modified = true
            plugin.logger.info("${ChatColor.GREEN}'Items'セクションをrecipes.ymlに移行しました．")
        }

        if (mainConfig.isConfigurationSection("Lores")) {
            plugin.logger.info("${ChatColor.YELLOW}'Lores'セクションをrecipes.ymlに移行します...")
            recipesConfig.set("Lores", mainConfig.getConfigurationSection("Lores"))
            mainConfig.set("Lores", null)
            modified = true
            plugin.logger.info("${ChatColor.GREEN}'Lores'セクションを recipes.ymlに移行しました．")
        }

        if (modified) {
            plugin.saveConfig()
            recipeConfigManager.saveConfig()
            plugin.logger.info("${ChatColor.GREEN}レシピデータの移行が完了しました．")
        }
    }

    private fun updateConfig(oldVersion: String) {
        val configFile = File(plugin.dataFolder, "config.yml")
        val oldFolder = File(plugin.dataFolder, "old")
        if (!oldFolder.exists()) {
            oldFolder.mkdirs()
        }

        val oldPath = Paths.get(oldFolder.absolutePath, "config-v$oldVersion.yml")
        try {
            Files.move(configFile.toPath(), oldPath, StandardCopyOption.REPLACE_EXISTING)
            plugin.logger.info("${ChatColor.YELLOW}古いconfig.ymlをoldフォルダにバックアップしました．")
            plugin.logger.info("${ChatColor.YELLOW}config.ymlを更新しています...")
        } catch (e: IOException) {
            plugin.logger.severe("config.ymlのバックアップに失敗しました: ${e.message}")
        }

        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        plugin.logger.info("${ChatColor.GREEN}config.ymlを最新バージョン($LATEST_CONFIG_VERSION)に更新しました")
    }

    fun updateConfigFromUrl(urlString: String) {
        try {
            @Suppress("DEPRECATION")
            URL(urlString).openStream().use { input ->
                val configFile = File(plugin.dataFolder, "config.yml")
                Files.copy(input, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                plugin.logger.info("config.ymlを${urlString}からダウンロードしました．")
            }
        } catch (e: IOException) {
            plugin.logger.severe("URLからのconfig.ymlのダウンロードに失敗しました: ${e.message}")
        }
    }

    fun reloadConfigFromUrl(url: String) {
        plugin.logger.info("外部URLからconfig.ymlを上書きしています...")
        updateConfigFromUrl(url)
        plugin.performSafeReload(null)
        plugin.logger.info("${ChatColor.GREEN}外部URLからconfig.ymlを上書きしました．")
    }

    companion object {
        private const val LATEST_CONFIG_VERSION = "1.1"
    }
}
