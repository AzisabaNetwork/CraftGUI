package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EditGuiManager implements Listener {

    private final CraftGUI plugin;
    private final RecipeConfigManager recipeConfigManager;
    private final RegisterGuiManager registerGuiManager;

    public static final String EDIT_GUI_TITLE_PREFIX = "CraftGUI Edit - Page ";
    private final Map<UUID, Integer> openEditGuis = new HashMap<>();
    private final Map<UUID, List<RecipeInfo>> playerEditList = new HashMap<>();

    public EditGuiManager(CraftGUI plugin, RecipeConfigManager recipeConfigManager, RegisterGuiManager registerGuiManager) {
        this.plugin = plugin;
        this.recipeConfigManager = recipeConfigManager;
        this.registerGuiManager = registerGuiManager;
    }

    public void openEditGui(Player player, int page) {
        List<RecipeInfo> allRecipes = loadAllRecipeInfo();
        if (allRecipes.isEmpty()) {
            plugin.sendMessage(player, ChatColor.RED + "編集可能なレシピがrecipes.ymlに見つかりません．");
            return;
        }

        playerEditList.put(player.getUniqueId(), allRecipes);
        openEditGuiInternal(player, page, allRecipes);
    }

    private void openEditGuiInternal(Player player, int page, List<RecipeInfo> allRecipes) {
        String title = EDIT_GUI_TITLE_PREFIX + page;
        Inventory gui = Bukkit.createInventory(player, 54, title);

        int itemsPerPage = 45;
        int startIndex = (page - 1) * itemsPerPage;
        int maxPage = (int) Math.ceil((double) allRecipes.size() / itemsPerPage);
        if (maxPage == 0) maxPage = 1;

        if (startIndex >= allRecipes.size() && page > 1) {
            openEditGuiInternal(player, maxPage, allRecipes);
            return;
        }

        for (int i = 0; i < itemsPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex >= allRecipes.size()) break;

            RecipeInfo info = allRecipes.get(recipeIndex);
            RecipeData recipeData = plugin.getRecipeById(info.id);

            ItemStack item;
            if (recipeData != null && recipeData.getGuiIcon() != null) {
                item = recipeData.getGuiIcon().clone();
            } else {
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                if(meta != null) {
                    meta.setDisplayName(ChatColor.RED + "[ロードエラー or 無効]");
                    item.setItemMeta(meta);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(item.getType());
            }

            meta.setDisplayName(ChatColor.YELLOW + "[編集] " + ChatColor.RESET + (meta.hasDisplayName() ? meta.getDisplayName() : info.id));
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "ID: " + info.id);
            lore.add(ChatColor.GRAY + "場所: page" + info.page + "." + info.slot);
            if (!info.isEnabled) {
                lore.add(ChatColor.RED + "✘ enabled: false");
            }
            if (!info.isCraftable) {
                lore.add(ChatColor.RED + "✘ craftable: false");
            }
            lore.add(ChatColor.GREEN + "クリックしてこのレシピを編集します");
            meta.setLore(lore);
            item.setItemMeta(meta);

            gui.setItem(i, item);
        }

        if (page > 1) gui.setItem(45, createNavItem(Material.ARROW, ChatColor.YELLOW + "前のページへ"));
        if (page < maxPage) gui.setItem(53, createNavItem(Material.ARROW, ChatColor.GREEN + "次のページへ"));
        gui.setItem(49, createNavItem(Material.BARRIER, ChatColor.RED + "閉じる"));

        player.openInventory(gui);
        openEditGuis.put(player.getUniqueId(), page);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(EDIT_GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        if (!openEditGuis.containsKey(uuid)) return;

        int slot = event.getRawSlot();
        int currentPage = openEditGuis.get(uuid);
        List<RecipeInfo> allRecipes = playerEditList.get(uuid);
        if (allRecipes == null) return;

        if (slot == 45 && currentPage > 1) {
            openEditGuiInternal(player, currentPage - 1, allRecipes);
            return;
        }
        if (slot == 53) {
            int maxPage = (int) Math.ceil((double) allRecipes.size() / 45.0);
            if (currentPage < maxPage) {
                openEditGuiInternal(player, currentPage + 1, allRecipes);
            }
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < 45) {
            int recipeIndex = (currentPage - 1) * 45 + slot;
            if (recipeIndex >= 0 && recipeIndex < allRecipes.size()) {
                RecipeInfo info = allRecipes.get(recipeIndex);
                RecipeData recipeData = plugin.getRecipeById(info.id);

                if (recipeData == null) {
                    plugin.sendMessage(player, ChatColor.RED + "エラー: このレシピは現在メモリにロードされていません．");
                    plugin.sendMessage(player, ChatColor.GRAY + "編集するには，一時的に recipes.ymlでenabled: trueにしてリロードしてください．");
                    return;
                }

                player.closeInventory();
                registerGuiManager.createAndOpenGui(player, info.page, info.slot, info.id, recipeData);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith(EDIT_GUI_TITLE_PREFIX)) {
            UUID uuid = event.getPlayer().getUniqueId();
            openEditGuis.remove(uuid);
            playerEditList.remove(uuid);
        }
    }

    private List<RecipeInfo> loadAllRecipeInfo() {
        List<RecipeInfo> list = new ArrayList<>();
        FileConfiguration config = recipeConfigManager.getConfig();
        ConfigurationSection itemsSection = config.getConfigurationSection("Items");
        if (itemsSection == null) {
            return list;
        }

        for (String pageKey : itemsSection.getKeys(false)) {
            int page = safeParseInt(pageKey.substring(4));
            if (page <= 0) continue;
            ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
            if (pageSection == null) continue;

            for (String slotKey : pageSection.getKeys(false)) {
                int slot = safeParseInt(slotKey);
                if (slot < 0) continue;

                String id = pageSection.getString(slotKey + ".id");
                if (id != null && !id.isEmpty()) {
                    boolean enabled = pageSection.getBoolean(slotKey + ".enabled", true);
                    boolean craftable = pageSection.getBoolean(slotKey + ".craftable", true);
                    list.add(new RecipeInfo(id, page, slot, enabled, craftable));
                }
            }
        }
        list.sort(Comparator.comparing(info -> info.id));
        return list;
    }

    private int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if(meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
