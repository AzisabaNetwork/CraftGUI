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
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class RegisterGuiManager implements Listener {

    private final CraftGUI plugin;
    private final MythicItemUtil mythicItemUtil;
    private final RecipeConfigManager recipeConfigManager;
    private final InventoryUtil inventoryUtil;
    private final Map<UUID, RegisterData> openRegisterGuis = new HashMap<>();
    private final Set<UUID> processing = Collections.synchronizedSet(new HashSet<>());

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
            if (processing.contains(uuid)) return;
            handleAsyncSave(player, event.getInventory(), data);
            player.closeInventory();
        } else if (Arrays.stream(SEPARATOR_SLOTS).anyMatch(s -> s == rawSlot)) {
            event.setCancelled(true);
        }
    }

    private void handleAsyncSave(Player player, Inventory inv, RegisterData data) {
        UUID uuid = player.getUniqueId();
        processing.add(uuid);
        player.sendMessage(ChatColor.YELLOW + "レシピを解析しています... (非同期)");
        List<ItemStack> reqSnapshots = getSnapshots(inv, REQUIRED_SLOTS);
        List<ItemStack> resSnapshots = getSnapshots(inv, RESULT_SLOTS);
        CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> reqConfig = analyzeItems(reqSnapshots);
            List<Map<String, Object>> resConfig = analyzeItems(resSnapshots);
            return new AbstractMap.SimpleEntry<>(reqConfig, resConfig);
        }).thenAccept(pair -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    saveToConfig(player, data, pair.getKey(), pair.getValue());
                } finally {
                    processing.remove(uuid);
                    openRegisterGuis.remove(uuid);
                }
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "保存中にエラーが発生しました。");
                ex.printStackTrace();
                processing.remove(uuid);
            });
            return null;
        });
    }

    private List<Map<String, Object>> analyzeItems(List<ItemStack> snapshots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : snapshots) {
            String mmid = mythicItemUtil.findMMIDAsync(item).join();
            String key;
            if (mmid != null) {
                key = "mmid:" + mmid;
            } else if (isPureVanilla(item)) {
                key = "material:" + item.getType().name();
            } else {
                key = "material:" + item.getType().name();
            }
            counts.put(key, counts.getOrDefault(key, 0) + item.getAmount());
        }
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> itemMap = new HashMap<>();
            String key = entry.getKey();
            if (key.startsWith("mmid:")) {
                itemMap.put("mmid", key.substring(5));
            } else {
                itemMap.put("material", key.substring(9));
            }
            itemMap.put("amount", entry.getValue());
            resultList.add(itemMap);
        }
        return resultList;
    }

    private boolean isPureVanilla(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && !meta.hasCustomModelData();
    }

    private List<ItemStack> getSnapshots(Inventory inv, int[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (int s : slots) {
            ItemStack item = inv.getItem(s);
            if (item != null && !item.getType().isAir()) list.add(item.clone());
        }
        return list;
    }

    private void saveToConfig(Player player, RegisterData data, List<Map<String, Object>> req, List<Map<String, Object>> res) {
        FileConfiguration config = recipeConfigManager.getConfig();
        String path = "Items.page" + data.page + "." + data.slot;
        config.set(path + ".id", data.recipeId);
        config.set(path + ".enabled", true);
        config.set(path + ".craftable", true);
        config.set(path + ".requiredItems", req);
        config.set(path + ".resultItems", res);
        recipeConfigManager.saveConfig();
        plugin.sendMessage(player, "&aレシピを保存し，Hot Updateを適用しました．");
        plugin.performSafeReload(null);
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
