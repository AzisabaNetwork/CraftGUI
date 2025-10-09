package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.logging.FileLogger;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

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

        this.allEnabledRecipes.clear();
        this.loadedItems.keySet().stream().sorted().forEach(page -> { // category -> page
            Map<Integer, RecipeData> pageItems = this.loadedItems.get(page);
            pageItems.keySet().stream().sorted().forEach(slot -> {
                RecipeData recipe = pageItems.get(slot);
                if (recipe.isEnabled()) {
                    this.allEnabledRecipes.add(recipe);
                }
            });
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openCraftGUI(Player player, int page) {
        boolean isCompactView = mapUtil.isCompactViewEnabled(player.getUniqueId());
        String title = "CraftGUI - " + (isCompactView ? "All Items (Page " + page + ")" : "Page " + page);
        Inventory playerGui = Bukkit.createInventory(null, 54, title);

        if (isCompactView) {
            int itemsPerPage = 45;
            int startIndex = (page - 1) * itemsPerPage;

            if (startIndex >= allEnabledRecipes.size() && page > 1) {
                plugin.sendMessage(player, ChatColor.RED + "そのページは存在しません");
                return;
            }

            for (int i = 0; i < itemsPerPage; i++) {
                int recipeIndex = startIndex + i;
                if (recipeIndex >= allEnabledRecipes.size()) break;

                RecipeData recipeData = allEnabledRecipes.get(recipeIndex);
                ItemStack item = guiUtil.createBaseRecipeItem(recipeData);
                guiUtil.updateLoreForPlayer(item, recipeData, player);
                playerGui.setItem(i, item);
            }
        } else {
            Map<Integer, RecipeData> pageItems = loadedItems.get(page);
            if (pageItems == null) {
                plugin.sendMessage(player, ChatColor.translateAlternateColorCodes('&', "&cページ" + page + "は存在しません。"));
                return;
            }
            for (Map.Entry<Integer, RecipeData> entry : pageItems.entrySet()) {
                int slot = entry.getKey();
                RecipeData recipeData = entry.getValue();
                if (recipeData.isEnabled()) {
                    ItemStack item = guiUtil.createBaseRecipeItem(recipeData);
                    guiUtil.updateLoreForPlayer(item, recipeData, player);
                    playerGui.setItem(slot, item);
                }
            }
        }

        setNavigationButtons(playerGui, page, player);
        player.openInventory(playerGui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("CraftGUI - ")) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        int currentPage = mapUtil.getPlayerPage(player.getUniqueId());

        if (slot >= 45 && slot < 54) {
            handleNavigation(player, slot, currentPage);
            return;
        }

        RecipeData clickedRecipe;
        boolean isCompactView = mapUtil.isCompactViewEnabled(player.getUniqueId());

        if (isCompactView) {
            int recipeIndex = (currentPage - 1) * 45 + slot;
            if (slot < 45 && recipeIndex >= 0 && recipeIndex < allEnabledRecipes.size()) {
                clickedRecipe = allEnabledRecipes.get(recipeIndex);
            } else {
                return;
            }
        } else {
            clickedRecipe = loadedItems.getOrDefault(currentPage, Collections.emptyMap()).get(slot);
        }

        if (clickedRecipe == null || !clickedRecipe.isEnabled()) return;
        attemptCraft(player, clickedRecipe, event.getClick(), currentPage, event);
    }

    private void attemptCraft(Player player, RecipeData recipe, ClickType click, int currentPage, InventoryClickEvent event) {
        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipe.getRequiredItems());
        if (maxCraftable <= 0) {
            plugin.sendMessage(player, ChatColor.RED + "変換に必要な素材が不足しています");
            if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }
        int craftAmount = 0;
        if (click.isLeftClick()) craftAmount = 1;
        else if (click.isRightClick()) craftAmount = (int) maxCraftable;
        if (craftAmount <= 0) return;
        craftAmount = (int) Math.min(craftAmount, maxCraftable);
        for (CraftingMaterial material : recipe.getRequiredItems()) {
            inventoryUtil.removeItems(player, material, material.getAmount() * craftAmount);
        }
        inventoryUtil.giveResultItems(player, recipe.getResultItems(), craftAmount);
        fileLogger.logCraft(player, recipe, craftAmount);
        plugin.sendMessage(player, ChatColor.GREEN + "" + craftAmount + "回変換しました");
        if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        int slot = event.getRawSlot();

        ItemStack updated = guiUtil.createBaseRecipeItem(recipe);
        guiUtil.updateLoreForPlayer(updated, recipe, player);
        inv.setItem(slot, updated);
    }

    private void handleNavigation(Player player, int slot, int currentPage) {
        boolean isCompactView = mapUtil.isCompactViewEnabled(player.getUniqueId());
        int maxPage;
        if (isCompactView) {
            maxPage = (int) Math.ceil((double) allEnabledRecipes.size() / 45.0);
            if (maxPage == 0) maxPage = 1;
        } else {
            maxPage = loadedItems.keySet().stream().max(Integer::compareTo).orElse(1);
        }

        boolean needsRedraw = true;

        if (slot == 45 && currentPage > 1) {
            mapUtil.setPlayerPage(player.getUniqueId(), currentPage - 1);
        } else if (slot == 53 && currentPage < maxPage) {
            mapUtil.setPlayerPage(player.getUniqueId(), currentPage + 1);
        } else if (slot == 49) {
            player.closeInventory();
            needsRedraw = false;
        } else if (slot == 51) {
            mapUtil.toggleCompactViewState(player.getUniqueId());
            mapUtil.setPlayerPage(player.getUniqueId(), 1);
        } else if (slot == 52) {
            mapUtil.toggleLoreState(player.getUniqueId());
        } else if (slot == 47) {
            mapUtil.toggleSoundState(player.getUniqueId());
        } else if (slot == 48) {
            mapUtil.toggleVanillaToStash(player.getUniqueId());
        } else {
            needsRedraw = false;
        }

        if (needsRedraw) {
            final int finalPageToOpen = mapUtil.getPlayerPage(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mapUtil.isSoundToggleOn(player.getUniqueId())) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
                openCraftGUI(player, finalPageToOpen);
            });
        }
    }

    private void setNavigationButtons(Inventory gui, int currentPage, Player player) {
        UUID uuid = player.getUniqueId();
        boolean isCompactView = mapUtil.isCompactViewEnabled(player.getUniqueId());
        int maxPage;
        if (isCompactView) {
            maxPage = (int) Math.ceil((double) allEnabledRecipes.size() / 45.0);
            if (maxPage == 0) maxPage = 1;
        } else {
            maxPage = loadedItems.keySet().stream().max(Integer::compareTo).orElse(1);
        }

        if (currentPage > 1) {
            gui.setItem(45, createNavItem(Material.ARROW, ChatColor.YELLOW + "前のページへ", Collections.emptyList()));
        }
        if (currentPage < maxPage) {
            gui.setItem(53, createNavItem(Material.ARROW, ChatColor.GREEN + "次のページへ", Collections.emptyList()));
        }
        gui.setItem(49, createNavItem(Material.BARRIER, ChatColor.RED + "閉じる", Collections.emptyList()));

        boolean loreOn = mapUtil.isLoreToggledOn(uuid);
        gui.setItem(52, createNavItem(loreOn ? Material.LIME_DYE : Material.GRAY_DYE,
                ChatColor.GREEN + "説明文表示",Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (loreOn ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));
        gui.setItem(51, createNavItem(isCompactView ? Material.WATER_BUCKET : Material.BUCKET, ChatColor.GREEN + "表示モード", Collections.singletonList(ChatColor.GRAY + "現在のモード: " + (isCompactView ? ChatColor.AQUA + "コンパクト" : ChatColor.GRAY + "デフォルト"))));

        boolean soundOn = mapUtil.isSoundToggleOn(uuid);
        gui.setItem(47, createNavItem(soundOn ? Material.JUKEBOX : Material.NOTE_BLOCK, ChatColor.GREEN + "サウンド設定", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (soundOn ? ChatColor.AQUA + "ON" : ChatColor.RED + "OFF"))));

        boolean vanillaToStash = mapUtil.isVanillaToStash(uuid);
        gui.setItem(48, createNavItem(vanillaToStash ? Material.ENDER_CHEST : Material.CHEST, ChatColor.GREEN + "バニラアイテム付与方法", Collections.singletonList(ChatColor.GRAY + "現在の設定: " + (vanillaToStash ? ChatColor.LIGHT_PURPLE + "Stash送り" : ChatColor.AQUA + "直接付与"))));
    }

    private ItemStack createNavItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}