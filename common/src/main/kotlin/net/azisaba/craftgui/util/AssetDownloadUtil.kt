package net.azisaba.craftgui.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.FileReader
import java.net.URI
import java.nio.file.Files
import java.util.logging.Level

class AssetDownloadUtil(private val plugin: Plugin) {

    private val translations = HashMap<String, Map<String, String>>()
    private val cacheDirectory = File(plugin.dataFolder, "lang_cache").apply {
        if (!exists()) mkdir()
    }

    val successfulLoads: MutableList<String> = ArrayList()
    val failedLoads: MutableList<String> = ArrayList()

    fun downloadAndLoadLanguages(languages: List<String>?) {
        translations.clear()
        successfulLoads.clear()
        failedLoads.clear()

        if (languages.isNullOrEmpty()) {
            plugin.logger.warning("ダウンロードする言語がconfig.ymlで指定されていません．")
            return
        }

        val baseUrl = plugin.config.getString("jsonUrl")
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, String>>() {}.type

        for (langCode in languages) {
            val cacheFile = File(cacheDirectory, "$langCode.json")
            try {
                val downloaded = !cacheFile.exists()
                if (downloaded) {
                    URI.create("$baseUrl$langCode.json").toURL().openStream().use { Files.copy(it, cacheFile.toPath()) }
                }
                val langMap: Map<String, String>? = FileReader(cacheFile).use { gson.fromJson(it, mapType) }
                plugin.logger.info(if (downloaded) "$langCode.json をダウンロードしました．" else "$langCode.jsonを読み込みました．")

                translations[langCode.lowercase()] = langMap ?: emptyMap()
                successfulLoads.add("$langCode.json")
            } catch (e: Exception) {
                failedLoads.add("$langCode.json ${e.javaClass.simpleName}")
                plugin.logger.log(Level.WARNING, "$langCode.jsonの処理に失敗しました: ", e)
            }
        }
    }

    fun getTranslations(langCode: String): Map<String, String>? = translations[langCode.lowercase()]
}
