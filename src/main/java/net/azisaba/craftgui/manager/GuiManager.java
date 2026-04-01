package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.gui.CraftGuiHolder;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.util.GuiUtil;
import net.azisaba.craftgui.util.InventoryUtil;
import net.azisaba.craftgui.util.MapUtil;
import net.azisaba.craftgui.util.MythicItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {

    private final CraftGUI plugin;
    private final MapUtil mapUtil;
    private final GuiUtil guiUtil;
    private final InventoryUtil inventoryUtil;
    private final FileLogger fileLogger;
    private final MythicItemUtil mythicItemUtil;

    private final Map<Integer, Map<Integer, RecipeData>> loadedItems;
    private final List<RecipeData> allEnabledRecipes = new ArrayList<>();

    public GuiManager(CraftGUI plugin, MapUtil mapUtil, GuiUtil guiUtil, InventoryUtil inventoryUtil, FileLogger fileLogger, Map<Integer, Map<Integer, RecipeData>> loadedItems, MythicItemUtil mythicItemUtil) {
        this.plugin = plugin;
        this.mapUtil = mapUtil;
        this.guiUtil = guiUtil;
        this.inventoryUtil = inventoryUtil;
        this.fileLogger = fileLogger;
        this.loadedItems = loadedItems;
        this.mythicItemUtil = mythicItemUtil;

        rebuildEnabledRecipeList();
    }

    public void hotUpdateRecipe(RecipeData recipeData, int page, int slot) {
        Map<Integer, RecipeData> pageItems = loadedItems.computeIfAbsent(page, k -> new LinkedHashMap<>());
        pageItems.put(slot, recipeData);
        rebuildEnabledRecipeList();
        plugin.getLogger().info("GuiManager recipe list updated.");
    }

    public void openCraftGUI(Player player, int page) {
        boolean isCompactView = isCompactLayout(player);
        List<Map<Integer, RecipeData>> visiblePages = getVisiblePages(player, isCompactView);
        int resolvedPage = resolvePage(page, visiblePages.size());

        String title = "CraftGUI - " + (isCompactView ? "All Items (Page " + resolvedPage + ")" : "Page " + resolvedPage);
        CraftGuiHolder holder = new CraftGuiHolder(resolvedPage);
        Inventory playerGui = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(playerGui);

        for (Map.Entry<Integer, RecipeData> entry : visiblePages.get(resolvedPage - 1).entrySet()) {
            ItemStack item = guiUtil.createBaseRecipeItem(entry.getValue());
            guiUtil.updateLoreForPlayer(item, entry.getValue(), player);
            playerGui.setItem(entry.getKey(), item);
        }

        mapUtil.setPlayerPage(player.getUniqueId(), resolvedPage);
        setNavigationButtons(playerGui, resolvedPage, player, visiblePages.size());
        player.openInventory(playerGui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof CraftGuiHolder)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (slot >= 45 && slot < 54) {
            handleNavigationAndSettings(player, event);
            return;
        }

        int currentPage = mapUtil.getPlayerPage(player.getUniqueId());
        boolean isCompactView = isCompactLayout(player);
        List<Map<Integer, RecipeData>> visiblePages = getVisiblePages(player, isCompactView);
        int resolvedPage = resolvePage(currentPage, visiblePages.size());
        RecipeData clickedRecipe = visiblePages.get(resolvedPage - 1).get(slot);

        if (clickedRecipe == null || !clickedRecipe.isEnabled()) {
            return;
        }

        if (!clickedRecipe.isCraftable()) {
            plugin.sendMessage(player, "&cこのアイテムはクラフトできません。");
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        attemptCraft(player, clickedRecipe, event.getClick(), event);
    }

    private void attemptCraft(Player player, RecipeData recipe, ClickType click, InventoryClickEvent event) {
        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems(), recipe.getResultItems());
        if (maxCraftable <= 0) {
            plugin.sendMessage(player, "&cクラフトに必要なアイテムが不足しているか、インベントリに空きがありません。");
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        int craftAmount = 0;
        if (click.isLeftClick()) {
            craftAmount = 1;
        } else if (click.isRightClick()) {
            craftAmount = (int) maxCraftable;
        }

        if (craftAmount <= 0) {
            return;
        }

        craftAmount = (int) Math.min(craftAmount, maxCraftable);
        for (CraftingMaterial material : recipe.getRequiredItems()) {
            inventoryUtil.removeItems(player, material, material.getAmount() * craftAmount);
        }
        inventoryUtil.giveResultItems(player, recipe.getResultItems(), craftAmount);
        fileLogger.logCraft(player, recipe, craftAmount);
        plugin.sendMessage(player, ChatColor.GREEN + "" + craftAmount + "回変換しました。");
        if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        int currentPage = mapUtil.getPlayerPage(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> openCraftGUI(player, currentPage));

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack updated = guiUtil.createBaseRecipeItem(recipe);
        guiUtil.updateLoreForPlayer(updated, recipe, player);
        inv.setItem(event.getRawSlot(), updated);
    }

    private void handleNavigationAndSettings(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int currentPage = mapUtil.getPlayerPage(player.getUniqueId());
        boolean isCompactView = isCompactLayout(player);
        int maxPage = getVisiblePages(player, isCompactView).size();

        if ((slot == 45 && currentPage > 1) || (slot == 53 && currentPage < maxPage) || slot == 46 || slot == 48 || slot == 50 || slot == 51 || slot == 52) {
            int newPage = currentPage;
            if (slot == 45) {
                newPage = currentPage - 1;
            } else if (slot == 46) {
                mapUtil.toggleCraftableOnlyState(player.getUniqueId());
                newPage = 1;
            } else if (slot == 48) {
                mapUtil.toggleStashEnabled(player.getUniqueId());
                boolean isStashEnabled = mapUtil.isStashEnabled(player.getUniqueId());
                String msg = isStashEnabled ? "アイテムをStashに送るように切り替えました。" : "通常どおりインベントリに送るように切り替えました。";
                plugin.sendMessage(player, ChatColor.GREEN + msg);
            } else if (slot == 50) {
                mapUtil.toggleCompactViewState(player.getUniqueId());
                newPage = 1;
            } else if (slot == 51) {
                mapUtil.toggleShowResultItems(player.getUniqueId());
            } else if (slot == 52) {
                mapUtil.toggleLoreState(player.getUniqueId());
            } else if (slot == 53) {
                newPage = currentPage + 1;
            }

            mapUtil.setPlayerPage(player.getUniqueId(), newPage);
            final int finalPageToOpen = newPage;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                }
                openCraftGUI(player, finalPageToOpen);
            });
            return;
        }

        if (slot == 47) {
            mapUtil.toggleSoundState(player.getUniqueId());
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            }
            setNavigationButtons(event.getInventory(), currentPage, player, maxPage);
            return;
        }

        if (slot == 49) {
            player.closeInventory();
        }
    }

    private void setNavigationButtons(Inventory gui, int currentPage, Player player, int maxPage) {
        UUID uuid = player.getUniqueId();
        boolean isCompactView = mapUtil.isCompactViewEnabled(uuid);

        if (currentPage > 1) {
            gui.setItem(45, createNavItem(Material.ARROW, ChatColor.YELLOW + "前のページへ", Collections.emptyList()));
        }
        if (currentPage < maxPage) {
            gui.setItem(53, createNavItem(Material.ARROW, ChatColor.GREEN + "次のページへ", Collections.emptyList()));
        }

        boolean craftableOnly = mapUtil.isCraftableOnlyEnabled(uuid);
        gui.setItem(46, createNavItem(craftableOnly ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, ChatColor.GREEN + "クラフト可能のみ表示", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (craftableOnly ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));

        boolean soundOn = mapUtil.isSoundToggleOn(uuid);
        gui.setItem(47, createNavItem(soundOn ? Material.JUKEBOX : Material.NOTE_BLOCK, ChatColor.GREEN + "サウンド設定", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (soundOn ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));

        boolean isStashEnabled = mapUtil.isStashEnabled(uuid);
        Material stashIcon = isStashEnabled ? Material.ENDER_CHEST : Material.CHEST;
        String stashStatus = isStashEnabled ? ChatColor.AQUA + "Stashへ送る" : ChatColor.GOLD + "インベントリへ送る";
        gui.setItem(48, createNavItem(stashIcon, ChatColor.GREEN + "アイテム受け取り先", Arrays.asList(ChatColor.GRAY + "現在の設定: " + stashStatus, ChatColor.GRAY + "クリックで切り替え")));

        gui.setItem(49, createNavItem(Material.BARRIER, ChatColor.RED + "閉じる", Collections.emptyList()));

        gui.setItem(50, createNavItem(isCompactView ? Material.WATER_BUCKET : Material.BUCKET, ChatColor.GREEN + "表示モード", Collections.singletonList(ChatColor.GRAY + "現在のモード: " + (isCompactView ? ChatColor.AQUA + "コンパクト" : ChatColor.GRAY + "デフォルト"))));

        boolean showResult = mapUtil.isShowResultItems(uuid);
        gui.setItem(51, createNavItem(showResult ? Material.HONEY_BOTTLE : Material.GLASS_BOTTLE, ChatColor.GREEN + "結果アイテム表示", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (showResult ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));

        boolean loreOn = mapUtil.isLoreToggledOn(uuid);
        gui.setItem(52, createNavItem(loreOn ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.GREEN + "説明文表示", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (loreOn ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));
    }

    private ItemStack createNavItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void rebuildEnabledRecipeList() {
        this.allEnabledRecipes.clear();
        this.loadedItems.keySet().stream().sorted().forEach(page -> {
            Map<Integer, RecipeData> pageItems = this.loadedItems.get(page);
            pageItems.keySet().stream().sorted().forEach(slot -> {
                RecipeData recipe = pageItems.get(slot);
                if (recipe.isEnabled()) {
                    this.allEnabledRecipes.add(recipe);
                }
            });
        });
    }

    private List<Map<Integer, RecipeData>> getVisiblePages(Player player, boolean isCompactView) {
        boolean craftableOnly = mapUtil.isCraftableOnlyEnabled(player.getUniqueId());
        List<RecipeData> visibleRecipes = new ArrayList<>();

        for (RecipeData recipe : allEnabledRecipes) {
            if (!recipe.isEnabled()) {
                continue;
            }
            if (craftableOnly && !isCurrentlyCraftable(player, recipe)) {
                continue;
            }
            visibleRecipes.add(recipe);
        }

        return isCompactView ? createCompactPages(visibleRecipes) : createDefaultPages(player, craftableOnly);
    }

    private List<Map<Integer, RecipeData>> createCompactPages(List<RecipeData> visibleRecipes) {
        List<Map<Integer, RecipeData>> pages = new ArrayList<>();
        Map<Integer, RecipeData> currentPage = new LinkedHashMap<>();

        for (RecipeData recipe : visibleRecipes) {
            if (currentPage.size() >= 45) {
                pages.add(currentPage);
                currentPage = new LinkedHashMap<>();
            }
            currentPage.put(currentPage.size(), recipe);
        }

        if (!currentPage.isEmpty() || pages.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
    }

    private List<Map<Integer, RecipeData>> createDefaultPages(Player player, boolean craftableOnly) {
        List<Map<Integer, RecipeData>> pages = new ArrayList<>();

        loadedItems.keySet().stream().sorted().forEach(page -> {
            Map<Integer, RecipeData> pageItems = loadedItems.getOrDefault(page, Collections.emptyMap());
            Map<Integer, RecipeData> visiblePageItems = new LinkedHashMap<>();

            pageItems.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                RecipeData recipe = entry.getValue();
                if (!recipe.isEnabled()) {
                    return;
                }
                if (craftableOnly && !isCurrentlyCraftable(player, recipe)) {
                    return;
                }
                visiblePageItems.put(entry.getKey(), recipe);
            });

            if (!visiblePageItems.isEmpty()) {
                pages.add(visiblePageItems);
            }
        });

        if (pages.isEmpty()) {
            pages.add(new LinkedHashMap<>());
        }
        return pages;
    }

    private boolean isCurrentlyCraftable(Player player, RecipeData recipe) {
        if (!recipe.isCraftable()) {
            return false;
        }
        return inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems(), recipe.getResultItems()) > 0;
    }

    private int resolvePage(int requestedPage, int maxPage) {
        if (maxPage <= 0) {
            return 1;
        }
        if (requestedPage < 1) {
            return 1;
        }
        return Math.min(requestedPage, maxPage);
    }

    private boolean isCompactLayout(Player player) {
        return mapUtil.isCompactViewEnabled(player.getUniqueId()) || mapUtil.isCraftableOnlyEnabled(player.getUniqueId());
    }
}
