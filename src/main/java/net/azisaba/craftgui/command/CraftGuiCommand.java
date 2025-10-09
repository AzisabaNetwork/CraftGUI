package net.azisaba.craftgui.command;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeLoader;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.manager.GuiManager;
import net.azisaba.craftgui.util.InventoryUtil;
import net.azisaba.craftgui.util.MapUtil;
import net.azisaba.craftgui.util.MythicItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftGuiCommand implements CommandExecutor, TabCompleter {

    private final CraftGUI plugin;
    private final RecipeLoader recipeLoader;
    private final MapUtil mapUtil;
    private final GuiManager guiManager;
    private final InventoryUtil inventoryUtil;
    private final MythicItemUtil mythicItemUtil;
    private final FileLogger fileLogger;

    public CraftGuiCommand(CraftGUI plugin, RecipeLoader recipeLoader, MapUtil mapUtil, GuiManager guiManager, InventoryUtil inventoryUtil, MythicItemUtil mythicItemUtil, FileLogger fileLogger) {
        this.plugin = plugin;
        this.recipeLoader = recipeLoader;
        this.mapUtil = mapUtil;
        this.guiManager = guiManager;
        this.inventoryUtil = inventoryUtil;
        this.mythicItemUtil = mythicItemUtil;
        this.fileLogger = fileLogger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleOpenGui(sender, "1");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.startsWith("page=")) {
            handleOpenGui(sender, subCommand.substring(5));
            return true;
        }

        switch (subCommand) {
            case "craft": handleCraftCommand(sender, args); break;
            case "config": handleConfigCommand(sender, args); break;
            case "errors": handleErrorsCommand(sender); break;
            default: plugin.sendMessage(sender, ChatColor.RED + "不明なコマンドまたは引数です: " + subCommand); break;
        }
        return true;
    }

    private void handleOpenGui(CommandSender sender, String pageStr) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, ChatColor.RED + "このコマンドはプレイヤーのみが実行できます");
            return;
        }
        Player player = (Player) sender;
        int page;
        try {
            page = Integer.parseInt(pageStr);
            if (page <= 0) {
                plugin.sendMessage(player, ChatColor.RED + "ページ番号は1以上で指定してください");
                return;
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, ChatColor.RED + "'" + pageStr + "' は有効なページ番号ではありません");
            return;
        }

        mapUtil.setPlayerPage(player.getUniqueId(), page);
        guiManager.openCraftGUI(player, page);
    }

    private void handleCraftCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, ChatColor.RED + "このコマンドはプレイヤーのみが実行できます");
            return;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, ChatColor.YELLOW + "使い方: /craftgui craft <ID> [数量]");
            return;
        }

        Player player = (Player) sender;
        String recipeId = args[1];
        RecipeData recipe = plugin.getRecipeById(recipeId);

        if (recipe == null) {
            plugin.sendMessage(player, ChatColor.RED + "レシピID'" + recipeId + "'が見つかりません");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, ChatColor.RED + "'" + args[2] + "' は有効な数量ではありません");
                return;
            }
        }

        attemptCraftByCommand(player, recipe, amount);
    }

    private void attemptCraftByCommand(Player player, RecipeData recipe, int craftAmount) {
        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems());

        if (maxCraftable <= 0) {
            plugin.sendMessage(player, ChatColor.RED + "変換に必要な素材が不足しています");
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            player.sendMessage(ChatColor.GRAY + "------------------------------------");
            for (CraftingMaterial material : recipe.getRequiredItems()) {
                long owned = inventoryUtil.countItems(player, material);
                int required = material.getAmount();
                String name = mythicItemUtil.resolveDisplayName(material, player);

                if (owned < required) {
                    player.sendMessage(ChatColor.RED + "✘ " + name + ": " + (required - owned) + "個不足 (所持: " + owned + ")");
                } else {
                    player.sendMessage(ChatColor.GREEN + "✓ " + name + ": 変換可能です (所持: " + owned + ")");
                }
            }
            player.sendMessage(ChatColor.GRAY + "-----------------------------------");
            return;
        }

        int finalCraftAmount = (int) Math.min(craftAmount, maxCraftable);
        if (finalCraftAmount <= 0) return;

        for (CraftingMaterial material : recipe.getRequiredItems()) {
            inventoryUtil.removeItems(player, material, material.getAmount() * finalCraftAmount);
        }
        inventoryUtil.giveResultItems(player, recipe.getResultItems(), finalCraftAmount);

        fileLogger.logCraft(player, recipe, finalCraftAmount);
        plugin.sendMessage(player, ChatColor.GREEN + "" + finalCraftAmount + "回変換しました");
        if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        player.sendMessage(ChatColor.GRAY + "------------------------------------");
        for (CraftingMaterial material : recipe.getRequiredItems()) {
            long remaining = inventoryUtil.countItems(player, material);
            String name = mythicItemUtil.resolveDisplayName(material, player);
            player.sendMessage(ChatColor.GRAY + " - " + name + ": 残り" + remaining + "個");
        }
        player.sendMessage(ChatColor.GRAY + "------------------------------------");
    }

    private void handleErrorsCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgui.admin")) {
            return;
        }
        List<String> errorDetails = recipeLoader.getErrorDetails();
        if (errorDetails.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "読み込みエラーはありませんでした");
        } else {
            sender.sendMessage(ChatColor.RED + "--- 設定ファイルの読み込みエラー詳細 ---");
            errorDetails.forEach(error -> sender.sendMessage(ChatColor.YELLOW + "- " + error));
            sender.sendMessage(ChatColor.RED + "------------------------------------");
        }
    }

    private void handleConfigCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, ChatColor.RED + "権限がありません");
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, "/craftgui config <reload|set>");
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            boolean isExternal = args.length == 3 && args[2].equalsIgnoreCase("--external");
            if (isExternal) {
                String url = plugin.getConfig().getString("configUrl");
                if (url == null || url.isEmpty()) {
                    plugin.sendMessage(sender, ChatColor.RED + "外部URLが設定されていません");
                    return;
                }
                plugin.reloadConfigFromUrl(url);
                plugin.sendMessage(sender, "外部URLからConfigを再読み込みしました");
            } else {
                plugin.reload();
                plugin.sendMessage(sender, ChatColor.GREEN + "Configを再読み込みしました");
            }
            return;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, "/craftgui config set <URL>");
                return;
            }
            String url = args[2];
            plugin.getConfig().set("configUrl", url);
            plugin.saveConfig();
            plugin.sendMessage(sender, ChatColor.GREEN + "外部リロードURLを設定しました");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("page=");

            if (sender.hasPermission("craftgui.admin")) {
                suggestions.add("craft");
            }
            if (sender.hasPermission("craftgui.admin")) {
                suggestions.addAll(Arrays.asList("config", "errors"));
            }
            return StringUtil.copyPartialMatches(args[0], suggestions, completions);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("craft") && sender.hasPermission("craftgui.admin")) {
                return StringUtil.copyPartialMatches(args[1], plugin.getRecipeIds(), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("config") && sender.hasPermission("craftgui.admin")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("reload", "set"), new ArrayList<>());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("craft") && sender.hasPermission("craftgui.admin")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "4", "8", "16", "32", "64"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("reload")) {
                return Collections.singletonList("--external");
            }
            if (args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("set")) {
                return Collections.singletonList("<URL>");
            }
        }

        return Collections.emptyList();
    }
}