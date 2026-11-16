package net.azisaba.craftgui.command

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.data.RecipeData
import net.azisaba.craftgui.data.RecipeLoader
import net.azisaba.craftgui.logging.FileLogger
import net.azisaba.craftgui.manager.EditGuiManager
import net.azisaba.craftgui.manager.GuiManager
import net.azisaba.craftgui.manager.RecipeConfigManager
import net.azisaba.craftgui.manager.RegisterGuiManager
import net.azisaba.craftgui.util.ConfigUtil
import net.azisaba.craftgui.util.InventoryUtil
import net.azisaba.craftgui.util.MapUtil
import net.azisaba.craftgui.util.MythicItemUtil
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

class CraftGuiCommand(
    private val plugin: CraftGUI,
    private val recipeLoader: RecipeLoader,
    private val mapUtil: MapUtil,
    private val guiManager: GuiManager,
    private val inventoryUtil: InventoryUtil,
    private val mythicItemUtil: MythicItemUtil,
    private val configUtil: ConfigUtil,
    private val fileLogger: FileLogger,
    private val registerGuiManager: RegisterGuiManager,
    private val recipeConfigManager: RecipeConfigManager,
    private val editGuiManager: EditGuiManager,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            handleOpenGui(sender, "1")
            return true
        }

        val subCommand = args[0].lowercase()
        if (subCommand.startsWith("page=")) {
            handleOpenGui(sender, subCommand.removePrefix("page="))
            return true
        }

        when (subCommand) {
            "craft" -> handleCraftCommand(sender, args)
            "config" -> handleConfigCommand(sender, args)
            "errors" -> handleErrorsCommand(sender)
            "register" -> handleRegisterCommand(sender, args)
            "edit" -> handleEditCommand(sender, args)
            else -> if (subCommand.matches(DIGITS)) {
                handleOpenGui(sender, subCommand)
            } else {
                plugin.sendMessage(sender, "${ChatColor.RED}不明なコマンドまたは引数です: $subCommand")
            }
        }
        return true
    }

    private fun requirePlayer(sender: CommandSender, message: String = "&cこのコマンドはプレイヤーのみ実行できます．"): Player? {
        if (sender !is Player) {
            plugin.sendMessage(sender, message)
            return null
        }
        return sender
    }

    private fun handleOpenGui(sender: CommandSender, pageStr: String) {
        val player = requirePlayer(sender) ?: return
        val page = pageStr.toIntOrNull()
        if (page == null) {
            plugin.sendMessage(player, "&c'$pageStr' は有効なページ番号ではありません．")
            return
        }
        if (page <= 0) {
            plugin.sendMessage(player, "&cページ番号は1以上で指定してください．")
            return
        }

        mapUtil.setPlayerPage(player.uniqueId, page)
        guiManager.openCraftGUI(player, page)
    }

    private fun handleCraftCommand(sender: CommandSender, args: Array<String>) {
        val player = requirePlayer(sender) ?: return
        if (args.size < 2) {
            plugin.sendMessage(sender, "&e使い方: /craftgui craft <ID> [数量]")
            return
        }

        val recipeId = args[1]
        val recipe = plugin.getRecipeById(recipeId)
        if (recipe == null) {
            plugin.sendMessage(player, "&cレシピID'$recipeId'は見つかりませんでした．")
            return
        }
        if (!recipe.isCraftable) {
            plugin.sendMessage(player, "&cこのレシピは表示専用のため，コマンドで変換することはできません．")
            return
        }

        var amount = 1
        if (args.size >= 3) {
            val parsed = args[2].toIntOrNull()
            if (parsed == null || parsed <= 0) {
                plugin.sendMessage(player, "&c${args[2]}は有効な数量ではありません．")
                return
            }
            amount = parsed
        }

        attemptCraftByCommand(player, recipe, amount)
    }

    private fun playSoundIfEnabled(player: Player, sound: Sound) {
        if (mapUtil.isSoundToggleOn(player.uniqueId)) {
            player.playSound(player.location, sound, 1.0f, 1.0f)
        }
    }

    private fun attemptCraftByCommand(player: Player, recipe: RecipeData, craftAmount: Int) {
        val maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.requiredBranches, recipe.resultItems)

        if (maxCraftable <= 0) {
            plugin.sendMessage(player, "&c変換に必要な素材が不足しています．")
            playSoundIfEnabled(player, Sound.ENTITY_VILLAGER_NO)

            player.sendMessage("${ChatColor.GRAY}------------------------------------")
            val branches = recipe.requiredBranches
            branches.forEachIndexed { i, branch ->
                if (branches.size > 1) {
                    player.sendMessage("${ChatColor.YELLOW}[パターン ${i + 1}]")
                }
                for (material in branch.materials) {
                    val owned = inventoryUtil.countItems(player, material)
                    val required = material.amount
                    val name = mythicItemUtil.resolveDisplayName(material, player)
                    if (owned < required) {
                        player.sendMessage("${ChatColor.RED}✘ $name: ${required - owned}個不足 (所持: $owned)")
                    } else {
                        player.sendMessage("${ChatColor.GREEN}✓ $name: 変換可能です (所持: $owned)")
                    }
                }
            }
            player.sendMessage("${ChatColor.GRAY}-----------------------------------")
            return
        }

        val finalCraftAmount = minOf(craftAmount.toLong(), maxCraftable).toInt()
        if (finalCraftAmount <= 0) return

        val bestBranch = inventoryUtil.getBestBranch(player, recipe.requiredBranches, finalCraftAmount)
        bestBranch?.materials?.forEach { inventoryUtil.removeItems(player, it, it.amount * finalCraftAmount) }
        inventoryUtil.giveResultItems(player, recipe.resultItems, finalCraftAmount)

        fileLogger.logCraft(player, recipe, bestBranch, finalCraftAmount)
        plugin.sendMessage(player, "&a${finalCraftAmount}回変換しました")
        playSoundIfEnabled(player, Sound.ENTITY_PLAYER_LEVELUP)

        player.sendMessage("${ChatColor.GRAY}------------------------------------")
        bestBranch?.materials?.forEach { material ->
            val remaining = inventoryUtil.countItems(player, material)
            val name = mythicItemUtil.resolveDisplayName(material, player)
            player.sendMessage("${ChatColor.GRAY} - $name: 残り${remaining}個")
        }
        player.sendMessage("${ChatColor.GRAY}------------------------------------")
    }

    private fun handleErrorsCommand(sender: CommandSender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return

        val errorDetails = recipeLoader.errorDetails
        if (errorDetails.isEmpty()) {
            plugin.sendMessage(sender, "&a読み込みエラーはありませんでした．")
        } else {
            sender.sendMessage("${ChatColor.RED}--- 設定ファイルの読み込みエラー詳細 ---")
            errorDetails.forEach { sender.sendMessage("${ChatColor.YELLOW}- $it") }
            sender.sendMessage("${ChatColor.RED}------------------------------------")
        }
    }

    private fun handleConfigCommand(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.sendMessage(sender, "&c権限がありません．")
            return
        }
        if (args.size < 2) {
            plugin.sendMessage(sender, "&e/craftgui config <reload|set>")
            return
        }

        when {
            args[1].equals("reload", ignoreCase = true) -> {
                val isExternal = args.size == 3 && args[2].equals("--external", ignoreCase = true)
                if (isExternal) {
                    val url = plugin.config.getString("configUrl")
                    if (url.isNullOrEmpty()) {
                        plugin.sendMessage(sender, "&c外部URLが設定されていません．")
                        return
                    }
                    configUtil.reloadConfigFromUrl(url)
                    plugin.sendMessage(sender, "&a外部URLからConfigを再読み込みしました．")
                } else {
                    plugin.performSafeReload(sender)
                    plugin.sendMessage(sender, "&aConfigを再読み込みしました．")
                }
            }
            args[1].equals("set", ignoreCase = true) -> {
                if (args.size < 3) {
                    plugin.sendMessage(sender, "&e/craftgui config set <URL>")
                    return
                }
                plugin.config.set("configUrl", args[2])
                plugin.saveConfig()
                plugin.sendMessage(sender, "&a外部リロードURLを設定しました．")
            }
        }
    }

    private fun handleRegisterCommand(sender: CommandSender, args: Array<String>) {
        val player = requirePlayer(sender, "&cこのコマンドはプレイヤーのみ実行できます") ?: return
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.sendMessage(sender, "&c権限がありません")
            return
        }

        val options = args.drop(1)
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) -> key.lowercase() to value }

        val recipeId = options["id"]
        if (recipeId.isNullOrEmpty()) {
            plugin.sendMessage(player, "&e使い方: /craftgui register id=<レシピID> [page=<ページ>] [slot=<スロット>]")
            plugin.sendMessage(player, "&7例: /craftgui register id=my_recipe page=1 slot=5")
            plugin.sendMessage(player, "&7例: /craftgui register id=new_item (ページとスロットは自動)")
            return
        }

        var page = -1
        var slot = -1
        options["page"]?.let {
            page = it.toIntOrNull() ?: run {
                plugin.sendMessage(player, "&cページまたはスロットが有効な数字ではありません．")
                return
            }
            if (page <= 0) {
                plugin.sendMessage(player, "&cページ番号は1以上で指定してください．")
                return
            }
        }
        options["slot"]?.let {
            slot = it.toIntOrNull() ?: run {
                plugin.sendMessage(player, "&cページまたはスロットが有効な数字ではありません．")
                return
            }
            if (slot !in 0 until 45) {
                plugin.sendMessage(player, "&cスロット番号は0から44の間で指定してください．")
                return
            }
        }

        if (page == -1 && slot == -1) {
            page = recipeConfigManager.getHighestPageNumber()
            slot = recipeConfigManager.findNextFreeSlot(page)
            if (slot == -1) {
                page++
                slot = 0
            }
        } else if (page != -1 && slot == -1) {
            slot = recipeConfigManager.findNextFreeSlot(page)
            if (slot == -1) {
                plugin.sendMessage(player, "&cエラー: page=${page}には空きスロットがありません (0-44)")
                return
            }
        } else if (page == -1) {
            plugin.sendMessage(player, "&cエラー: slotを指定する場合はpageも指定する必要があります")
            return
        }

        val targetPath = "Items.page$page.$slot"
        if (recipeConfigManager.config.isConfigurationSection(targetPath)) {
            plugin.sendMessage(player, "&cエラー: 指定されたスロット(${targetPath})は既に使用されています．")
            plugin.sendMessage(player, "&7/craftgui editを使用するか，別のスロットを指定してください．")
            return
        }

        val existingLocation = recipeConfigManager.findExistingRecipeById(recipeId)
        if (existingLocation != null) {
            plugin.sendMessage(player, "&cエラー: 指定されたレシピID'$recipeId'は既に使用されています．")
            plugin.sendMessage(player, "&7(場所: $existingLocation)")
            return
        }

        plugin.sendMessage(player, "${ChatColor.GREEN}レシピ登録GUIを開きます (ID: $recipeId, 場所: page$page.$slot)")
        registerGuiManager.createAndOpenGui(player, page, slot, recipeId, null)
    }

    private fun handleEditCommand(sender: CommandSender, args: Array<String>) {
        val player = requirePlayer(sender, "&cこのコマンドはプレイヤーのみ実行できます") ?: return
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.sendMessage(sender, "&c権限がありません")
            return
        }

        val page = args.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        editGuiManager.openEditGui(player, page)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        val isAdmin = sender.hasPermission(ADMIN_PERMISSION)

        if (args.size == 1) {
            val suggestions = mutableListOf("page=", "craft")
            if (isAdmin) suggestions.addAll(listOf("config", "errors", "register", "edit"))
            return partialMatches(args[0], suggestions)
        }

        val subCommand = args[0].lowercase()
        if (isAdmin && subCommand == "register") {
            val lastArg = args.last()
            if (lastArg.contains("=")) return emptyList()
            val used = args.slice(1 until args.size - 1)
            val suggestions = listOf("id=", "page=", "slot=").filter { key -> used.none { it.startsWith(key) } }
            return partialMatches(lastArg, suggestions)
        }

        if (!isAdmin) return emptyList()

        return when (args.size) {
            2 -> when (subCommand) {
                "craft" -> partialMatches(args[1], plugin.getRecipeIds())
                "config" -> partialMatches(args[1], listOf("reload", "set"))
                else -> emptyList()
            }
            3 -> when {
                subCommand == "craft" -> partialMatches(args[2], listOf("1", "2", "4", "8", "16", "32", "64"))
                subCommand == "config" && args[1].equals("reload", ignoreCase = true) -> listOf("--external")
                subCommand == "config" && args[1].equals("set", ignoreCase = true) -> listOf("<URL>")
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun partialMatches(token: String, candidates: Iterable<String>): List<String> =
        StringUtil.copyPartialMatches(token, candidates, ArrayList())

    companion object {
        private const val ADMIN_PERMISSION = "craftgui.admin"
        private val DIGITS = Regex("\\d+")
    }
}
