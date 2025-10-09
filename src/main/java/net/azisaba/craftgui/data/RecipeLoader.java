package net.azisaba.craftgui.data;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.util.MythicItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;


public class RecipeLoader {

    private final CraftGUI plugin;
    private final MythicItemUtil mythicItemUtil;
    private final List<String> errorDetails = new ArrayList<>();
    private Map<String, List<String>> loresCache = new HashMap<>();

    public RecipeLoader(CraftGUI plugin, MythicItemUtil mythicItemUtil) {
        this.plugin = plugin;
        this.mythicItemUtil = mythicItemUtil;
    }

    public Map<Integer, Map<Integer, RecipeData>> loadAllItems(FileConfiguration config) {
        errorDetails.clear();
        this.loresCache = loadLores(config);
        Map<Integer, Map<Integer, RecipeData>> itemsByPage = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("Items");

        if (itemsSection == null) {
            plugin.getLogger().warning("config.ymlにItemsセクションがありません");
            return itemsByPage;
        }

        for (String pageKey : itemsSection.getKeys(false)) {
            try {
                if (!pageKey.toLowerCase().startsWith("page")) {
                    addError(pageKey, "N/A", "ページキーは'page'で始まる必要があります");
                    continue;
                }
                int page = Integer.parseInt(pageKey.substring(4));
                ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
                if (pageSection != null) {
                    itemsByPage.put(page, loadItemsForPage(pageSection, pageKey));
                }
            } catch (NumberFormatException e) {
                addError(pageKey, "N/A", "ページ番号が数字ではありません");
            }
        }
        return itemsByPage;
    }

    private Map<Integer, RecipeData> loadItemsForPage(ConfigurationSection categorySection, String categoryName) {
        Map<Integer, RecipeData> categoryItems = new LinkedHashMap<>();
        for (String slotKey : categorySection.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                ConfigurationSection itemSection = categorySection.getConfigurationSection(slotKey);
                if (itemSection != null) {
                    RecipeData recipeData = parseRecipeData(itemSection, categoryName, slotKey);
                    if (recipeData != null) {
                        categoryItems.put(slot, recipeData);
                    }
                }
            } catch (NumberFormatException e) {
                addError(categoryName, slotKey, "スロット番号が数字ではありません");
            }
        }
        return categoryItems;
    }

    private RecipeData parseRecipeData(ConfigurationSection itemSection, String categoryName, String slotKey) {
        String id = itemSection.getString("id");
        if (id == null || id.isEmpty()) {
            addError(categoryName, slotKey, "idが設定されていません");
            return null;
        }

        try {
            boolean enabled = itemSection.getBoolean("enabled", true);

            ItemStack guiIcon = null;

            List<?> resultItemsList = itemSection.getList("resultItems");
            if (resultItemsList != null && !resultItemsList.isEmpty()) {
                Object firstItemObj = resultItemsList.get(0);
                if (firstItemObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> firstItemMap = (Map<String, Object>) firstItemObj;
                    String resultMmid = (String) firstItemMap.get("mmid");
                    String resultMaterialStr = (String) firstItemMap.get("material");

                    if (resultMmid != null && !resultMmid.isEmpty()) {
                        guiIcon = createMythicItem(resultMmid, categoryName, slotKey);
                    } else if (resultMaterialStr != null && !resultMaterialStr.isEmpty()) {
                        guiIcon = createVanillaItem(resultMaterialStr, categoryName, slotKey);
                    }
                }
            }

            if (guiIcon == null) {
                addError(categoryName, slotKey, "'resultItems'が正しく設定されていないため，GUIアイコンを生成できませんでした");
                return null;
            }

            String loreKey = itemSection.getString("lore", "CommonLore");

            ItemMeta meta = guiIcon.getItemMeta();
            if (meta != null) {
                if (itemSection.contains("displayName")) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemSection.getString("displayName", "")));
                }
                guiIcon.setItemMeta(meta);
            }

            List<CraftingMaterial> resultItems = parseMaterialsList(itemSection.getList("resultItems"), "resultItems", categoryName, slotKey);
            List<CraftingMaterial> requiredItems = parseMaterialsList(itemSection.getList("requiredItems"), "requiredItems", categoryName, slotKey);

            return new RecipeData(id, enabled, guiIcon, loreKey, resultItems, requiredItems);

        } catch (Exception e) {
            addError(categoryName, slotKey, "不明なエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private ItemStack createMythicItem(String mmid, String pageKey, String slotKey) {
        Optional<MythicItem> mythicItemOpt = MythicMobs.inst().getItemManager().getItem(mmid);
        if (mythicItemOpt.isPresent()) {
            return ((BukkitItemStack) mythicItemOpt.get().generateItemStack(1)).build();
        } else {
            addError(pageKey, slotKey, "指定されたMythicMobsアイテムが見つかりません: " + mmid);
            return createErrorItem("不明なMMID: " + mmid);
        }
    }

    private ItemStack createVanillaItem(String materialStr, String pageKey, String slotKey) {
        try {
            Material material = Material.valueOf(materialStr.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            addError(pageKey, slotKey, "無効なマテリアル名です: " + materialStr);
            return createErrorItem("無効なMaterial: " + materialStr);
        }
    }

    private ItemStack createErrorItem(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + message);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<CraftingMaterial> parseMaterialsList(List<?> rawList, String listName, String pageKey, String slotKey) {
        List<CraftingMaterial> materials = new ArrayList<>();
        if (rawList == null || rawList.isEmpty()) {
            return materials;
        }
        for (int i = 0; i < rawList.size(); i++) {
            Object obj = rawList.get(i);
            if (!(obj instanceof Map)) {
                addError(pageKey, slotKey, String.format("%s[%d] の形式が不正です", listName, i));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) obj;
            try {
                materials.add(parseSingleMaterial(itemMap));
            } catch (IllegalArgumentException | ClassCastException e) {
                addError(pageKey, slotKey, String.format("%s[%d] の解析中にエラー: %s", listName, i, e.getMessage()));
            }
        }
        return materials;
    }

    private CraftingMaterial parseSingleMaterial(Map<String, Object> map) throws IllegalArgumentException {
        String mmid = (String) map.get("mmid");
        String materialStr = (String) map.get("material");
        Material material = null;
        boolean isMythic = false;

        String displayName = (String) map.get("displayName");

        if (mmid != null && !mmid.trim().isEmpty()) {
            isMythic = true;
            String fetchedDisplayName = mythicItemUtil.getDisplayNameFromMMID(mmid);

            if (!fetchedDisplayName.equals(mmid)) {
                displayName = fetchedDisplayName;
            }

        } else if (materialStr != null && !materialStr.trim().isEmpty()) {
            try {
                material = Material.valueOf(materialStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("マテリアル名'" + materialStr + "'は不正です");
            }
        } else {
            isMythic = true;
        }

        int amount = (int) map.getOrDefault("amount", 1);
        if (amount <= 0) {
            throw new IllegalArgumentException("'amount'は1以上の整数である必要があります");
        }

        @SuppressWarnings("unchecked")
        List<String> lore = (List<String>) map.get("lore");

        return new CraftingMaterial(isMythic, mmid, material, amount, displayName, lore);
    }

    public Map<String, List<String>> loadLores(FileConfiguration config) {
        Map<String, List<String>> lores = new HashMap<>();
        ConfigurationSection loresSection = config.getConfigurationSection("Lores");
        if (loresSection == null) {
            plugin.getLogger().warning("config.ymlにLoresセクションがありません");
            return lores;
        }
        for (String loreKey : loresSection.getKeys(false)) {
            List<String> coloredLoreLines = new ArrayList<>();
            for (String line : loresSection.getStringList(loreKey)) {
                coloredLoreLines.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            lores.put(loreKey, coloredLoreLines);
        }
        return lores;
    }

    private void addError(String pageKey, String slotKey, String message) {
        String errorMessage = String.format("[ページ: %s, スロット: %s] %s", pageKey, slotKey, message);
        errorDetails.add(errorMessage);
        plugin.getLogger().warning(errorMessage);
    }

    public List<String> getErrorDetails() {
        return errorDetails;
    }
}