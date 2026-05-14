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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
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
            case "edit": handleEditCommand(sender, args); break;
            default:
                if (subCommand.matches("\\d+")) {
                    handleOpenGui(sender, subCommand);
                    return true;
                }
                plugin.sendMessage(sender, Component.text("不明なコマンドまたは引数です: " + subCommand).color(NamedTextColor.RED));
                break;
        }
        return true;
    }

    private void handleOpenGui(CommandSender sender, String pageStr) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Component.text("このコマンドはプレイヤーのみ実行できます．").color(NamedTextColor.RED));
            return;
        }
        int page;
        try {
            page = Integer.parseInt(pageStr);
            if (page <= 0) {
                plugin.sendMessage(player, Component.text("ページ番号は1以上で指定してください．").color(NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, Component.text("'" + pageStr + "' は有効なページ番号ではありません．").color(NamedTextColor.RED));
            return;
        }

        mapUtil.setPlayerPage(player.getUniqueId(), page);
        guiManager.openCraftGUI(player, page);
    }

    private void handleCraftCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Component.text("このコマンドはプレイヤーのみ実行できます．").color(NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, Component.text("使い方: /craftgui craft <ID> [数量]").color(NamedTextColor.YELLOW));
            return;
        }

        String recipeId = args[1];
        RecipeData recipe = plugin.getRecipeById(recipeId);

        if (recipe == null) {
            plugin.sendMessage(player, Component.text("レシピID'" + recipeId + "'は見つかりませんでした．").color(NamedTextColor.RED));
            return;
        }

        if (recipe.isCraftable()) {
            plugin.sendMessage(player, Component.text("このレシピは表示専用のため，コマンドで変換することはできません．").color(NamedTextColor.RED));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, Component.text(args[2] + "は有効な数量ではありません．").color(NamedTextColor.RED));
                return;
            }
        }

        attemptCraftByCommand(player, recipe, amount);
    }

    private void attemptCraftByCommand(Player player, RecipeData recipe, int craftAmount) {
        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems(), recipe.getRequiredItems());

        if (maxCraftable <= 0) {
            plugin.sendMessage(player, Component.text("変換に必要な素材が不足しています．").color(NamedTextColor.RED));
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            player.sendMessage(Component.text("------------------------------------").color(NamedTextColor.GRAY));
            for (CraftingMaterial material : recipe.getRequiredItems()) {
                long owned = inventoryUtil.countItems(player, material);
                int required = material.amount();
                String name = mythicItemUtil.resolveDisplayName(material, player);

                if (owned < required) {
                    player.sendMessage(Component.text("✘ " + name + ": " + (required - owned) + "個不足 (所持: " + owned + ")").color(NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("✓ " + name + ": 変換可能です (所持: " + owned + ")").color(NamedTextColor.GREEN));
                }
            }
            player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
            return;
        }

        int finalCraftAmount = (int) Math.min(craftAmount, maxCraftable);
        if (finalCraftAmount <= 0) return;

        for (CraftingMaterial material : recipe.getRequiredItems()) {
            inventoryUtil.removeItems(player, material, material.amount() * finalCraftAmount);
        }
        inventoryUtil.giveResultItems(player, recipe.getResultItems(), finalCraftAmount);

        fileLogger.logCraft(player, recipe, finalCraftAmount);
        plugin.sendMessage(player,  Component.text(finalCraftAmount + "回変換しました").color(NamedTextColor.GREEN));
        if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        player.sendMessage(Component.text("------------------------------------").color(NamedTextColor.GRAY));
        for (CraftingMaterial material : recipe.getRequiredItems()) {
            long remaining = inventoryUtil.countItems(player, material);
            String name = mythicItemUtil.resolveDisplayName(material, player);
            player.sendMessage(Component.text(" - " + name + ": 残り" + remaining + "個").color(NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("------------------------------------").color(NamedTextColor.GRAY));
    }

    private void handleErrorsCommand(CommandSender sender) {
        if (!sender.hasPermission("craftgui.admin")) {
            return;
        }
        List<String> errorDetails = recipeLoader.getErrorDetails();
        if (errorDetails.isEmpty()) {
            plugin.sendMessage(sender, Component.text("読み込みエラーはありませんでした．").color(NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("--- 設定ファイルの読み込みエラー詳細 ---").color(NamedTextColor.RED));
            errorDetails.forEach(error -> sender.sendMessage(Component.text("- " + error).color(NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("------------------------------------").color(NamedTextColor.RED));
        }
    }

    private void handleConfigCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, Component.text("権限がありません．").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Component.text("/craftgui config <reload|set>").color(NamedTextColor.YELLOW));
            return;
        }

        if (args[1].equalsIgnoreCase("reload")) {
            boolean isExternal = args.length == 3 && args[2].equalsIgnoreCase("--external");
            if (isExternal) {
                String url = plugin.getConfig().getString("configUrl");
                if (url == null || url.isEmpty()) {
                    plugin.sendMessage(sender, Component.text("外部URLが設定されていません．").color(NamedTextColor.RED));
                    return;
                }
                configUtil.reloadConfigFromUrl(url);
                plugin.sendMessage(sender, Component.text("外部URLからConfigを再読み込みしました．").color(NamedTextColor.GREEN));
            } else {
                plugin.performSafeReload(sender);
                plugin.sendMessage(sender, Component.text("Configを再読み込みしました．").color(NamedTextColor.GREEN));
            }
            return;
        }

        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, Component.text("/craftgui config set <URL>").color(NamedTextColor.YELLOW));
                return;
            }
            String url = args[2];
            plugin.getConfig().set("configUrl", url);
            plugin.saveConfig();
            plugin.sendMessage(sender, Component.text("外部リロードURLを設定しました．").color(NamedTextColor.GREEN));
        }
    }

    private void handleRegisterCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Component.text("このコマンドはプレイヤーのみ実行できます").color(NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, Component.text("権限がありません").color(NamedTextColor.RED));
            return;
        }

        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                options.put(parts[0].toLowerCase(), parts[1]);
            }
        }

        String recipeId = options.get("id");
        if (recipeId == null || recipeId.isEmpty()) {
            plugin.sendMessage(player, Component.text("使い方: /craftgui register id=<レシピID> [page=<ページ>] [slot=<スロット>]").color(NamedTextColor.YELLOW));
            plugin.sendMessage(player, Component.text("例: /craftgui register id=my_recipe page=1 slot=5").color(NamedTextColor.GRAY));
            plugin.sendMessage(player, Component.text("例: /craftgui register id=new_item (ページとスロットは自動)").color(NamedTextColor.GRAY));
            return;
        }

        int page = -1;
        int slot = -1;

        try {
            if (options.containsKey("page")) {
                page = Integer.parseInt(options.get("page"));
                if (page <= 0) {
                    plugin.sendMessage(player, Component.text("ページ番号は1以上で指定してください．").color(NamedTextColor.RED));
                    return;
                }
            }
            if (options.containsKey("slot")) {
                slot = Integer.parseInt(options.get("slot"));
                if (slot < 0 || slot >= 45) {
                    plugin.sendMessage(player, Component.text("スロット番号は0から44の間で指定してください．").color(NamedTextColor.RED));
                    return;
                }
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, Component.text("ページまたはスロットが有効な数字ではありません．").color(NamedTextColor.RED));
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
                plugin.sendMessage(player, Component.text("エラー: page=" + page + "には空きスロットがありません (0-44)").color(NamedTextColor.RED));
                return;
            }
        }
        else if (page == -1 && slot != -1) {
            plugin.sendMessage(player, Component.text("エラー: slotを指定する場合はpageも指定する必要があります").color(NamedTextColor.RED));
            return;
        }

        String targetPath = "Items.page" + page + "." + slot;
        if (recipeConfigManager.getConfig().isConfigurationSection(targetPath)) {
            plugin.sendMessage(player, Component.text("エラー: 指定されたスロット(" + targetPath + ")は既に使用されています．").color(NamedTextColor.RED));
            plugin.sendMessage(player, Component.text("/craftgui editを使用するか，別のスロットを指定してください．").color(NamedTextColor.GRAY));
            return;
        }

        String existingLocation = recipeConfigManager.findExistingRecipeById(recipeId);
        if (existingLocation != null) {
            plugin.sendMessage(player, Component.text("エラー: 指定されたレシピID'" + recipeId + "'は既に使用されています．").color(NamedTextColor.RED));
            plugin.sendMessage(player, Component.text("(場所: " + existingLocation + ")").color(NamedTextColor.GRAY));
            return;
        }

        plugin.sendMessage(player, Component.text("レシピ登録GUIを開きます (ID: " + recipeId + ", 場所: page" + page + "." + slot + ")").color(NamedTextColor.GREEN));
        registerGuiManager.createAndOpenGui(player, page, slot, recipeId, null);
    }

    private void handleEditCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, Component.text("このコマンドはプレイヤーのみ実行できます").color(NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("craftgui.admin")) {
            plugin.sendMessage(sender, Component.text("権限がありません").color(NamedTextColor.RED));
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
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NotNull String alias, String[] args) {
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