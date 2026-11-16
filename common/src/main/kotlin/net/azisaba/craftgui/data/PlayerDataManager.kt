package net.azisaba.craftgui.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.util.MapUtil
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.UUID
import java.util.logging.Level

class PlayerDataManager(private val plugin: CraftGUI) {

    private val dataFolder = File(plugin.dataFolder, "playerdata").apply {
        if (!exists()) mkdirs()
    }
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun loadPlayerData(uuid: UUID, mapUtil: MapUtil) {
        val playerFile = File(dataFolder, "$uuid.json")
        val data: PlayerData = if (!playerFile.exists()) {
            PlayerData()
        } else {
            try {
                FileReader(playerFile).use { gson.fromJson(it, PlayerData::class.java) } ?: PlayerData()
            } catch (e: IOException) {
                plugin.logger.log(Level.SEVERE, "Failed to load player data for $uuid", e)
                PlayerData()
            }
        }
        mapUtil.setSoundToggleState(uuid, data.soundOn)
        mapUtil.setShowResultItems(uuid, data.showResultItems)
        mapUtil.setCraftableOnlyState(uuid, data.craftableOnly)
        mapUtil.setStashEnabled(uuid, data.vanillaItemsToStash)
    }

    fun savePlayerData(uuid: UUID, mapUtil: MapUtil) {
        val data = PlayerData().apply {
            soundOn = mapUtil.isSoundToggleOn(uuid)
            showResultItems = mapUtil.isShowResultItems(uuid)
            craftableOnly = mapUtil.isCraftableOnlyEnabled(uuid)
            vanillaItemsToStash = mapUtil.isStashEnabled(uuid)
        }

        val playerFile = File(dataFolder, "$uuid.json")
        try {
            FileWriter(playerFile).use { gson.toJson(data, it) }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "プレイヤーデータの保存に失敗しました：$uuid", e)
        }
    }
}
