package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RegisterData;
import net.azisaba.craftgui.util.InventoryUtil;
import net.azisaba.craftgui.util.MythicItemUtil;
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
import java.util.stream.IntStream;

public class RegisterGuiManager implements Listener {

    private final CraftGUI plugin;
    private final MythicItemUtil mythicItemUtil;
    private final RecipeConfigManager recipeConfigManager;
    private final InventoryUtil inventoryUtil;
    private final Map<UUID, RegisterData> openRegisterGuis = new HashMap<>();

    private static final int[] REQUIRED_SLOTS = IntStream.range(0, 27).toArray();
    private static final int[] SEPARATOR_SLOTS = IntStream.range(27, 36).toArray();
    private static final int[] RESULT_SLOTS = IntStream.range(36, 54).toArray();
    private static final int SAVE_BUTTON_SLOT = 31;
    private static final String GUI_TITLE_PREFIX = "CraftGUI Register: ";

    public RegisterGuiManager(CraftGUI plugin, MythicItemUtil mythicItemUtil, RecipeConfigManager recipeConfigManager, InventoryUtil inventoryUtil) {
        this.plugin = plugin;
        this.mythicItemUtil = mythicItemUtil;
        this.recipeConfigManager = recipeConfigManager;
        this.inventoryUtil = inventoryUtil;
    }

    public void createAndOpenGui(Player player, int page, int slot, String recipeId, RecipeData existingRecipe) {
        String title = GUI_TITLE_PREFIX + recipeId;
        Inventory gui = Bukkit.createInventory(player, 54, title);

        ItemStack separator = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int s : SEPARATOR_SLOTS) {
            gui.setItem(s, separator);
        }

        ItemStack saveButton = createGuiItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "✓ レシピを保存",
                Arrays.asList(
                        ChatColor.GRAY + "ページ: " + page,
                        ChatColor.GRAY + "スロット: " + slot,
                        ChatColor.GRAY + "ID: " + recipeId,
                        "",
                        ChatColor.YELLOW + "クリックしてレシピを保存します"
                ));
        gui.setItem(SAVE_BUTTON_SLOT, saveButton);

        if (existingRecipe != null) {
            placeItemsInGrid(gui, existingRecipe.getRequiredItems(), REQUIRED_SLOTS);
            placeItemsInGrid(gui, existingRecipe.getResultItems(), RESULT_SLOTS);
        }

        player.openInventory(gui);
        openRegisterGuis.put(player.getUniqueId(), new RegisterData(page, slot, recipeId, title));
    }

    private void placeItemsInGrid(Inventory gui, List<CraftingMaterial> materials, int[] slots) {
        if (materials == null || materials.isEmpty() || inventoryUtil == null) {
            return;
        }

        int slotIndex = 0;
        try {
            for (CraftingMaterial material : materials) {
                ItemStack item = inventoryUtil.getItemStackFromMaterial(material);
                if (item == null || item.getType().isAir()) continue;

                int amountToPlace = material.getAmount();
                int maxStackSize = item.getType().getMaxStackSize();

                while (amountToPlace > 0 && slotIndex < slots.length) {
                    int currentStackSize = Math.min(amountToPlace, maxStackSize);
                    item.setAmount(currentStackSize);
                    gui.setItem(slots[slotIndex], item.clone());

                    amountToPlace -= currentStackSize;
                    slotIndex++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "GUIへの既存アイテム配置中にエラーが発生しました。", e);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        if (!openRegisterGuis.containsKey(uuid)) return;

        RegisterData data = openRegisterGuis.get(uuid);
        if (!event.getView().getTitle().equals(data.guiTitle)) return;

        event.setCancelled(false);
        int rawSlot = event.getRawSlot();

        if (rawSlot == SAVE_BUTTON_SLOT) {
            event.setCancelled(true);
            saveRecipe(player, event.getInventory(), data);
            player.closeInventory();
        } else if (Arrays.stream(SEPARATOR_SLOTS).anyMatch(s -> s == rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (openRegisterGuis.containsKey(uuid)) {
            RegisterData data = openRegisterGuis.get(uuid);
            if (event.getView().getTitle().equals(data.guiTitle)) {
                openRegisterGuis.remove(uuid);
            }
        }
    }

    private void saveRecipe(Player player, Inventory inv, RegisterData data) {
        FileConfiguration config = recipeConfigManager.getConfig();
        String path = "Items.page" + data.page + "." + data.slot;
        String pageKey = "page" + data.page;
        String slotKey = String.valueOf(data.slot);

        List<Map<String, Object>> requiredItemsConfig = aggregateItems(inv, REQUIRED_SLOTS);
        List<Map<String, Object>> resultItemsConfig = aggregateItems(inv, RESULT_SLOTS);

        config.set(path + ".id", data.recipeId);
        config.set(path + ".enabled", true);
        config.set(path + ".craftable", true);
        config.set(path + ".requiredItems", requiredItemsConfig);
        config.set(path + ".resultItems", resultItemsConfig);

        recipeConfigManager.saveConfig();
        plugin.sendMessage(player, "&aレシピ'" + data.recipeId + "'を" + path + "に保存しました．");

        ConfigurationSection itemSection = config.getConfigurationSection(path);
        if (itemSection == null) {
            plugin.sendMessage(player, ChatColor.RED + "エラー: 保存したレシピをrecipe.ymlから再取得できませんでした．");
            plugin.performSafeReload(player);
            return;
        }

        RecipeData newRecipe = plugin.getRecipeLoader().parseRecipeData(itemSection, pageKey, slotKey);

        if (newRecipe != null) {
            plugin.hotUpdateRecipe(newRecipe, data.page, data.slot);
            plugin.sendMessage(player, "&aレシピのアップデートが完了しました。");
        } else {
            plugin.sendMessage(player, ChatColor.RED + "エラー: 保存したレシピの解析に失敗しました．安全なリロードを実行します...");
            plugin.performSafeReload(player);
        }
    }

    private List<Map<String, Object>> aggregateItems(Inventory inv, int[] slots) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, ItemStack> itemTemplates = new HashMap<>();

        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            String key = getItemKey(item);
            counts.put(key, counts.getOrDefault(key, 0) + item.getAmount());
            itemTemplates.putIfAbsent(key, item);
        }

        List<Map<String, Object>> configList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String key = entry.getKey();
            int amount = entry.getValue();
            ItemStack template = itemTemplates.get(key);

            Map<String, Object> itemMap = convertItemStackToConfigMap(template);
            itemMap.put("amount", amount);
            configList.add(itemMap);
        }
        return configList;
    }

    private Map<String, Object> convertItemStackToConfigMap(ItemStack item) {
        Map<String, Object> map = new HashMap<>();

        String mmid = mythicItemUtil.getMythicType(item);
        if (mmid != null) {
            map.put("mmid", mmid);
            return map;
        }

        if (isPureVanilla(item)) {
            map.put("material", item.getType().name());
            return map;
        }

        mmid = mythicItemUtil.findMythicIdByItemStack(item);
        if (mmid != null) {
            map.put("mmid", mmid);
        } else {
            map.put("material", item.getType().name());
        }
        return map;
    }

    private String getItemKey(ItemStack item) {
        String mmid = mythicItemUtil.getMythicType(item);
        if (mmid != null) {
            return "mmid:" + mmid;
        }
        if (isPureVanilla(item)) {
            return "material:" + item.getType().name();
        }

        mmid = mythicItemUtil.findMythicIdByItemStack(item);
        if (mmid != null) {
            return "mmid:" + mmid;
        } else {
            return "material:" + item.getType().name();
        }
    }

    private boolean isPureVanilla(ItemStack item) {
        if (!item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.hasCustomModelData();
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
