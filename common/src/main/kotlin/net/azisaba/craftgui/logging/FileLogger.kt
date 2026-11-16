package net.azisaba.craftgui.logging

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.data.RecipeBranch
import net.azisaba.craftgui.data.RecipeData
import net.azisaba.craftgui.util.InventoryUtil
import net.azisaba.craftgui.util.MythicItemUtil
import org.bukkit.entity.Player
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

class FileLogger(
    private val plugin: CraftGUI,
    private val mythicItemUtil: MythicItemUtil,
    private val inventoryUtil: InventoryUtil,
) {

    private val logDirectory = File(plugin.dataFolder, "logs")

    init {
        if (!logDirectory.exists()) {
            if (logDirectory.mkdirs()) {
                plugin.logger.info("ログディレクトリを作成しました: ${logDirectory.path}")
            } else {
                plugin.logger.severe("ログディレクトリの作成に失敗しました．")
            }
        }
    }

    fun saveLog(message: String) {
        val now = Date()
        val logFile = File(logDirectory, SimpleDateFormat("yyyy-MM-dd").format(now) + ".log")
        try {
            PrintWriter(BufferedWriter(FileWriter(logFile, true))).use { writer ->
                writer.println("[${SimpleDateFormat("HH:mm:ss").format(now)}] $message")
            }
        } catch (e: IOException) {
            plugin.logger.severe("ログファイルへの書き込みに失敗しました: ${e.message}")
        }
    }

    fun logCraft(player: Player, recipe: RecipeData, consumedBranch: RecipeBranch?, craftAmount: Int) {
        try {
            val requiredString = consumedBranch?.materials?.joinToString(", ") { m ->
                val name = mythicItemUtil.resolvePlainName(m, player)
                val consumedAmount = m.amount * craftAmount
                val remainingAmount = inventoryUtil.countItems(player, m)
                "$name(x$consumedAmount)[残り: $remainingAmount]"
            } ?: "なし"

            val resultString = recipe.resultItems.joinToString(", ") { m ->
                val name = mythicItemUtil.resolvePlainName(m, player)
                "$name(x${m.amount * craftAmount})"
            }

            saveLog("${player.name}が${requiredString}を${resultString}に変換しました")
        } catch (e: Exception) {
            plugin.logger.warning("クラフトログの保存に失敗しました: ${e.message}")
        }
    }
}
