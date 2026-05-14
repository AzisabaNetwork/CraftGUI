package net.azisaba.craftgui.data;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.util.MythicItemUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;

public class RecipeLoader {

    private final CraftGUI plugin;
    private final MythicItemUtil mythicItemUtil;
    private final List<String> errorDetails = new ArrayList<>();

    public RecipeLoader(CraftGUI plugin, MythicItemUtil mythicItemUtil) {
        this.plugin = plugin;
        this.mythicItemUtil = mythicItemUtil;
    }

    public Map<Integer, Map<Integer, RecipeData>> loadAllItems(FileConfiguration config) {
        errorDetails.clear();
        Map<Integer, Map<Integer, RecipeData>> itemsByPage = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("Items");

        if (itemsSection == null) {
            plugin.getLogger().warning("config.ymlにItemsセクションがありません．");
            return itemsByPage;
        }

        for (String pageKey : itemsSection.getKeys(false)) {
            try {
                if (!pageKey.toLowerCase().startsWith("page")) {
                    addError(pageKey, "N/A", "ページキーは'page'で始まる必要があります．");
                    continue;
                }
                int page = Integer.parseInt(pageKey.substring(4));
                ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
                if (pageSection != null) {
                    itemsByPage.put(page, loadItemsForPage(pageSection, pageKey));
                }
            } catch (NumberFormatException e) {
                addError(pageKey, "N/A", "ページ番号が数字ではありません．");
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
                addError(categoryName, slotKey, "スロット番号が数字ではありません．");
            }
        }
        return categoryItems;
    }

    public RecipeData parseRecipeData(ConfigurationSection itemSection, String categoryName, String slotKey) {
        String id = itemSection.getString("id");
        if (id == null || id.isEmpty()) {
            addError(categoryName, slotKey, "idが設定されていません．");
            return null;
        }

        try {
            boolean enabled = itemSection.getBoolean("enabled", true);
            boolean craftable = itemSection.getBoolean("craftable", true);

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
                addError(categoryName, slotKey, "GUIアイコンを生成できませんでした．");
                return null;
            }

            String loreKey = itemSection.getString("lore", "commonLore");

            ItemMeta meta = guiIcon.getItemMeta();
            if (meta != null && itemSection.contains("displayName")) {
                meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(itemSection.getString("displayName", "")));
                guiIcon.setItemMeta(meta);
            }

            List<CraftingMaterial> resultItems = parseMaterialsList(itemSection.getList("resultItems"), "resultItems", categoryName, slotKey);
            List<CraftingMaterial> requiredItems = parseMaterialsList(itemSection.getList("requiredItems"), "requiredItems", categoryName, slotKey);

            return new RecipeData(id, enabled, craftable, guiIcon, loreKey, resultItems, requiredItems);

        } catch (Exception e) {
            addError(categoryName, slotKey, "解析エラー: " + e.getMessage());
        }
        return null;
    }

    private ItemStack createMythicItem(String mmid, String pageKey, String slotKey) {
        ItemStack item = mythicItemUtil.getItemStackFromMMID(mmid);
        if (item != null && !item.getType().isAir()) {
            return item;
        }
        return createErrorItem("不明なMMID: " + mmid);
    }

    private ItemStack createVanillaItem(String materialStr, String pageKey, String slotKey) {
        try {
            Material material = Material.valueOf(materialStr.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            return createErrorItem("無効なID: " + materialStr);
        }
    }

    private ItemStack createErrorItem(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(message).color(NamedTextColor.RED));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<CraftingMaterial> parseMaterialsList(List<?> rawList, String listName, String pageKey, String slotKey) {
        List<CraftingMaterial> materials = new ArrayList<>();
        if (rawList == null) return materials;

        for (Object obj : rawList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) obj;
                try {
                    materials.add(parseSingleMaterial(itemMap));
                } catch (Exception e) {
                    addError(pageKey, slotKey, "アイテム解析失敗: " + e.getMessage());
                }
            }
        }
        return materials;
    }

    private CraftingMaterial parseSingleMaterial(Map<String, Object> map) {
        String mmid = (String) map.get("mmid");
        String materialStr = (String) map.get("material");
        Material material = null;
        boolean isMythic = false;
        String displayName = (String) map.get("displayName");

        @SuppressWarnings("unchecked")
        Map<String, Object> itemStackData = (Map<String, Object>) map.get("itemStack");

        if (mmid != null && !mmid.trim().isEmpty()) {
            isMythic = true;
        } else if (materialStr != null && !materialStr.trim().isEmpty()) {
            material = Material.valueOf(materialStr.trim().toUpperCase());
        } else if (itemStackData != null) {
            ItemStack temp = ItemStack.deserialize(itemStackData);
            material = temp.getType();
        }

        int amount = ((Number) map.getOrDefault("amount", 1)).intValue();
        @SuppressWarnings("unchecked")
        List<String> lore = (List<String>) map.get("lore");

        return new CraftingMaterial(isMythic, mmid, material, amount, displayName, lore, itemStackData);
    }

    public Map<String, List<String>> loadLores(FileConfiguration config) {
        Map<String, List<String>> lores = new HashMap<>();
        ConfigurationSection loresSection = config.getConfigurationSection("Lores");
        if (loresSection != null) {
            for (String loreKey : loresSection.getKeys(false)) {
                lores.put(loreKey, loresSection.getStringList(loreKey));
            }
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