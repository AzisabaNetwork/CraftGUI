package net.azisaba.craftgui.command;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeLoader;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.manager.EditGuiManager;
import net.azisaba.craftgui.manager.GuiManager;
import net.azisaba.craftgui.manager.RecipeConfigManager;
import net.azisaba.craftgui.manager.RegisterGuiManager;
import net.azisaba.craftgui.util.ConfigUtil;
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

import java.util.*;

public class CraftGuiCommand implements CommandExecutor, TabCompleter {

    private final CraftGUI plugin;
    private final RecipeLoader recipeLoader;
    private final MapUtil mapUtil;
    private final GuiManager guiManager;
    private final InventoryUtil inventoryUtil;
    private final MythicItemUtil mythicItemUtil;
    private final ConfigUtil configUtil;
    private final FileLogger fileLogger;
    private final RegisterGuiManager registerGuiManager;
    private final RecipeConfigManager recipeConfigManager;
    private final EditGuiManager editGuiManager;

    public CraftGuiCommand(CraftGUI plugin, RecipeLoader recipeLoader, MapUtil mapUtil, GuiManager guiManager, InventoryUtil inventoryUtil, MythicItemUtil mythicItemUtil, ConfigUtil configUtil, FileLogger fileLogger, RegisterGuiManager registerGuiManager, RecipeConfigManager recipeConfigManager, EditGuiManager editGuiManager) {
        this.plugin = plugin;
        this.recipeLoader = recipeLoader;
        this.mapUtil = mapUtil;
        this.guiManager = guiManager;
        this.inventoryUtil = inventoryUtil;
        this.mythicItemUtil = mythicItemUtil;
        this.configUtil = configUtil;
        this.fileLogger = fileLogger;
        this.registerGuiManager = registerGuiManager;
        this.recipeConfigManager = recipeConfigManager;
        this.editGuiManager = editGuiManager;
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
            case "register": handleRegisterCommand(sender, args); break;
            case "edit": handleEditCommand(sender, args); break; // 追加
            default:
                if (subCommand.matches("\\d+")) {
                    handleOpenGui(sender, subCommand);
                    return true;
                }
                plugin.sendMessage(sender, ChatColor.RED + "不明なコマンドまたは引数です: " + subCommand);
                break;
        }
        return true;
    }

    private void handleOpenGui(CommandSender sender, String pageStr) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cこのコマンドはプレイヤーのみ実行できます．");
            return;
        }
        Player player = (Player) sender;
        int page;
        try {
            page = Integer.parseInt(pageStr);
            if (page <= 0) {
                plugin.sendMessage(player, "&cページ番号は1以上で指定してください．");
                return;
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "&c'" + pageStr + "' は有効なページ番号ではありません．");
            return;
        }

        mapUtil.setPlayerPage(player.getUniqueId(), page);
        guiManager.openCraftGUI(player, page);
    }

    private void handleCraftCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cこのコマンドはプレイヤーのみ実行できます．");
            return;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, "&e使い方: /craftgui craft <ID> [数量]");
            return;
        }

        Player player = (Player) sender;
        String recipeId = args[1];
        RecipeData recipe = plugin.getRecipeById(recipeId);

        if (recipe == null) {
            plugin.sendMessage(player, "&cレシピID'" + recipeId + "'は見つかりませんでした．");
            return;
        }

        if (!recipe.isCraftable()) {
            plugin.sendMessage(player, "&cこのレシピは表示専用のため，コマンドで変換することはできません．");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "&c" + args[2] + "は有効な数量ではありません．");
                return;
            }
        }

        attemptCraftByCommand(player, recipe, amount);
    }

    private void attemptCraftByCommand(Player player, RecipeData recipe, int craftAmount) {
        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems());

        if (maxCraftable <= 0) {
            plugin.sendMessage(player, "&c変換に必要な素材が不足しています．");
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
        plugin.sendMessage(player,  "&a" + finalCraftAmount + "回変換しました");
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
            plugin.sendMessage(sender, "&a読み込みエラーはありませんでした．");
        } else {
            sender.sendMessage(ChatColor.RED + "--- 設定ファイルの読み込みエラー詳細 ---");
            errorDetails.forEach(error -> sender.sendMessage(ChatColor.YELLOW + "- " + error));
            sender.sendMessage(ChatColor.RED + "------------------------------------");
        }
    }

    private void handleConfigCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, "&c権限がありません．");
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, "&e/craftgui config <reload|set>");
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            boolean isExternal = args.length == 3 && args[2].equalsIgnoreCase("--external");
            if (isExternal) {
                String url = plugin.getConfig().getString("configUrl");
                if (url == null || url.isEmpty()) {
                    plugin.sendMessage(sender, "&c外部URLが設定されていません．");
                    return;
                }
                configUtil.reloadConfigFromUrl(url);
                plugin.sendMessage(sender, "&a外部URLからConfigを再読み込みしました．");
            } else {
                plugin.performSafeReload(sender);
                plugin.sendMessage(sender, "&aConfigを再読み込みしました．");
            }
            return;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, "&e/craftgui config set <URL>");
                return;
            }
            String url = args[2];
            plugin.getConfig().set("configUrl", url);
            plugin.saveConfig();
            plugin.sendMessage(sender, "&a外部リロードURLを設定しました．");
        }
    }

    private void handleRegisterCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cこのコマンドはプレイヤーのみ実行できます");
            return;
        }
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, "&c権限がありません");
            return;
        }

        Player player = (Player) sender;

        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                options.put(parts[0].toLowerCase(), parts[1]);
            }
        }

        String recipeId = options.get("id");
        if (recipeId == null || recipeId.isEmpty()) {
            plugin.sendMessage(player, "&e使い方: /craftgui register id=<レシピID> [page=<ページ>] [slot=<スロット>]");
            plugin.sendMessage(player, "&7例: /craftgui register id=my_recipe page=1 slot=5");
            plugin.sendMessage(player, "&7例: /craftgui register id=new_item (ページとスロットは自動)");
            return;
        }

        int page = -1;
        int slot = -1;

        try {
            if (options.containsKey("page")) {
                page = Integer.parseInt(options.get("page"));
                if (page <= 0) {
                    plugin.sendMessage(player, "&cページ番号は1以上で指定してください．");
                    return;
                }
            }
            if (options.containsKey("slot")) {
                slot = Integer.parseInt(options.get("slot"));
                if (slot < 0 || slot >= 45) {
                    plugin.sendMessage(player, "&cスロット番号は0から44の間で指定してください．");
                    return;
                }
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "&cページまたはスロットが有効な数字ではありません．");
            return;
        }

        if (page == -1 && slot == -1) {
            page = recipeConfigManager.getHighestPageNumber();
            slot = recipeConfigManager.findNextFreeSlot(page);
            if (slot == -1) {
                page++;
                slot = 0;
            }
        } else if (page != -1 && slot == -1) {
            slot = recipeConfigManager.findNextFreeSlot(page);
            if (slot == -1) {
                plugin.sendMessage(player, "&cエラー: page=" + page + "には空きスロットがありません (0-44)");
                return;
            }
        }
        else if (page == -1 && slot != -1) {
            plugin.sendMessage(player, "&cエラー: slotを指定する場合はpageも指定する必要があります");
            return;
        }

        String targetPath = "Items.page" + page + "." + slot;
        if (recipeConfigManager.getConfig().isConfigurationSection(targetPath)) {
            plugin.sendMessage(player, "&cエラー: 指定されたスロット(" + targetPath + ")は既に使用されています．");
            plugin.sendMessage(player, "&7/craftgui editを使用するか，別のスロットを指定してください．");
            return;
        }

        String existingLocation = recipeConfigManager.findExistingRecipeById(recipeId);
        if (existingLocation != null) {
            plugin.sendMessage(player, "&cエラー: 指定されたレシピID'" + recipeId + "'は既に使用されています．");
            plugin.sendMessage(player, "&7(場所: " + existingLocation + ")");
            return;
        }

        plugin.sendMessage(player, ChatColor.GREEN + "レシピ登録GUIを開きます (ID: " + recipeId + ", 場所: page" + page + "." + slot + ")");
        registerGuiManager.createAndOpenGui(player, page, slot, recipeId, null);
    }

    private void handleEditCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "&cこのコマンドはプレイヤーのみ実行できます");
            return;
        }
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, "&c権限がありません");
            return;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page <= 0) page = 1;
            } catch (NumberFormatException e) {
            }
        }

        editGuiManager.openEditGui((Player) sender, page);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("page=");
            suggestions.add("craft");
            if (sender.hasPermission("craftgui.admin")) {
                suggestions.addAll(Arrays.asList("config", "errors", "register", "edit"));
            }
            return StringUtil.copyPartialMatches(args[0], suggestions, completions);
        }
        String subCommand = args[0].toLowerCase();
        if (sender.hasPermission("craftgui.admin")) {
            if (subCommand.equals("register")) {
                String lastArg = args[args.length - 1];
                List<String> suggestions = new ArrayList<>(Arrays.asList("id=", "page=", "slot="));
                for (int i = 1; i < args.length - 1; i++) {
                    if (args[i].startsWith("id=")) suggestions.remove("id=");
                    if (args[i].startsWith("page=")) suggestions.remove("page=");
                    if (args[i].startsWith("slot=")) suggestions.remove("slot=");
                }
                if (lastArg.contains("=")) {
                    return Collections.emptyList();
                }
                return StringUtil.copyPartialMatches(lastArg, suggestions, new ArrayList<>());
            }
        }

        if (args.length == 2) {
            if (sender.hasPermission("craftgui.admin")) {
                if (args[0].equalsIgnoreCase("craft")) {
                    return StringUtil.copyPartialMatches(args[1], plugin.getRecipeIds(), new ArrayList<>());
                } else if(args[0].equalsIgnoreCase("config")) {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("reload", "set"), new ArrayList<>());
                }
            }
        }
        if (args.length == 3) {
            if (sender.hasPermission("craftgui.admin")) {
                if(args[0].equalsIgnoreCase("craft")) {
                    return StringUtil.copyPartialMatches(args[2], Arrays.asList("1", "2", "4", "8", "16", "32", "64"), new ArrayList<>());
                } else if(args[0].equalsIgnoreCase("config")) {
                    if (args[1].equalsIgnoreCase("reload")) {
                        return Collections.singletonList("--external");
                    } else if(args[1].equalsIgnoreCase("set")) {
                        return Collections.singletonList("<URL>");
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}