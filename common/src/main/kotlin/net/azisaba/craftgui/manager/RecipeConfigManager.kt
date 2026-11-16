package net.azisaba.craftgui.manager

import net.azisaba.craftgui.CraftGUI
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level

class RecipeConfigManager(private val plugin: CraftGUI) {

    private val recipesFile = File(plugin.dataFolder, "recipes.yml")
    private var recipesConfig: FileConfiguration? = null

    val config: FileConfiguration
        get() = recipesConfig ?: run {
            reloadConfig()
            recipesConfig!!
        }

    fun reloadConfig() {
        if (!recipesFile.exists()) {
            plugin.logger.info("recipes.ymlが見つかりませんでした．デフォルトファイルを作成します...")
            saveDefaultConfig()
        }
        val loaded = YamlConfiguration.loadConfiguration(recipesFile)
        recipesConfig = loaded

        try {
            plugin.getResource("recipes.yml")?.use { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                    loaded.setDefaults(YamlConfiguration.loadConfiguration(reader))
                }
            }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "recipes.ymlの読み込みに失敗しました: ", e)
        }
    }

    fun saveConfig() {
        val current = recipesConfig ?: return
        try {
            current.save(recipesFile)
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "recipes.ymlの保存に失敗しました: $recipesFile", e)
        }
    }

    fun saveDefaultConfig() {
        if (!recipesFile.exists()) {
            plugin.saveResource("recipes.yml", false)
        }
    }

    fun getHighestPageNumber(): Int {
        val itemsSection = config.getConfigurationSection("Items") ?: return 1
        return itemsSection.getKeys(false)
            .filter { it.lowercase().startsWith("page") }
            .maxOfOrNull { safeParseInt(it.substring(4)) }
            ?: 1
    }

    fun findNextFreeSlot(page: Int): Int {
        val pageSection = config.getConfigurationSection("Items.page$page") ?: return 0
        val usedSlots = pageSection.getKeys(false).map { safeParseInt(it) }.filter { it in 0 until 45 }.toSet()
        return (0 until 45).firstOrNull { it !in usedSlots } ?: -1
    }

    fun findExistingRecipeById(recipeId: String): String? {
        val itemsSection = config.getConfigurationSection("Items") ?: return null
        for (pageKey in itemsSection.getKeys(false)) {
            val pageSection = itemsSection.getConfigurationSection(pageKey) ?: continue
            for (slotKey in pageSection.getKeys(false)) {
                if (recipeId.equals(pageSection.getString("$slotKey.id"), ignoreCase = true)) {
                    return "$pageKey.$slotKey"
                }
            }
        }
        return null
    }

    private fun safeParseInt(s: String): Int = s.toIntOrNull() ?: -1
}
