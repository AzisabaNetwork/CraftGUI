package net.azisaba.craftgui.platform.legacy;

import net.azisaba.craftgui.platform.PlatformAdapter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.*;

public class LegacyPlatformAdapter implements PlatformAdapter {

    private static final Method GET_TOP_INVENTORY_METHOD;
    private static final Method HAS_CUSTOM_MODEL_DATA_METHOD;
    private static final Method GET_CUSTOM_MODEL_DATA_METHOD;
    private static final Method SET_CUSTOM_MODEL_DATA_METHOD;

    static {
        Method getTopInventory = null;
        try {
            getTopInventory = InventoryView.class.getMethod("getTopInventory");
        } catch (Throwable ignored) {
        }
        GET_TOP_INVENTORY_METHOD = getTopInventory;

        Method hasCmd = null;
        Method getCmd = null;
        Method setCmd = null;
        try {
            hasCmd = ItemMeta.class.getMethod("hasCustomModelData");
            getCmd = ItemMeta.class.getMethod("getCustomModelData");
            setCmd = ItemMeta.class.getMethod("setCustomModelData", Integer.class);
        } catch (Throwable ignored) {
            // 1.13 does not have CustomModelData
        }
        HAS_CUSTOM_MODEL_DATA_METHOD = hasCmd;
        GET_CUSTOM_MODEL_DATA_METHOD = getCmd;
        SET_CUSTOM_MODEL_DATA_METHOD = setCmd;
    }

    @Override
    public Inventory getTopInventory(InventoryView view) {
        if (view == null) return null;
        if (GET_TOP_INVENTORY_METHOD != null) {
            try {
                return (Inventory) GET_TOP_INVENTORY_METHOD.invoke(view);
            } catch (Throwable ignored) {
            }
        }
        return view.getTopInventory();
    }

    @Override
    public Integer getCustomModelData(ItemMeta meta) {
        if (meta == null || HAS_CUSTOM_MODEL_DATA_METHOD == null || GET_CUSTOM_MODEL_DATA_METHOD == null) {
            return null;
        }
        try {
            boolean has = (boolean) HAS_CUSTOM_MODEL_DATA_METHOD.invoke(meta);
            if (has) {
                return (Integer) GET_CUSTOM_MODEL_DATA_METHOD.invoke(meta);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void setCustomModelData(ItemMeta meta, Integer customModelData) {
        if (meta == null || SET_CUSTOM_MODEL_DATA_METHOD == null) {
            return;
        }
        try {
            SET_CUSTOM_MODEL_DATA_METHOD.invoke(meta, customModelData);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isSimilar(ItemStack first, ItemStack second) {
        if (first == null || second == null) return false;
        if (first.getType() != second.getType()) return false;
        try {
            if (first.isSimilar(second)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return compareSerializedItems(first, second) || compareByItemMeta(first, second);
    }

    @Override
    public Map<String, Object> serializeItemStack(ItemStack item) {
        if (item == null) return Collections.emptyMap();
        return new LinkedHashMap<>(item.serialize());
    }

    @Override
    public ItemStack deserializeItemStack(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return ItemStack.deserialize(new LinkedHashMap<>(data));
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public void applyCleanFlags(ItemMeta meta) {
        if (meta == null) return;
        meta.setLore(null);
        try {
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_POTION_EFFECTS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS
            );
        } catch (Throwable ignored) {
        }
    }

    private boolean compareSerializedItems(ItemStack first, ItemStack second) {
        if (first.getType() != second.getType()) {
            return false;
        }
        return normalizeSerializedValue(first.serialize()).equals(normalizeSerializedValue(second.serialize()));
    }

    private boolean compareByItemMeta(ItemStack first, ItemStack second) {
        if (first.getType() != second.getType()) {
            return false;
        }

        ItemMeta firstMeta = first.hasItemMeta() ? first.getItemMeta() : null;
        ItemMeta secondMeta = second.hasItemMeta() ? second.getItemMeta() : null;

        if (firstMeta == null || secondMeta == null) {
            return firstMeta == null && secondMeta == null;
        }

        return Objects.equals(firstMeta.hasDisplayName() ? firstMeta.getDisplayName() : null, secondMeta.hasDisplayName() ? secondMeta.getDisplayName() : null)
                && Objects.equals(firstMeta.hasLore() ? firstMeta.getLore() : null, secondMeta.hasLore() ? secondMeta.getLore() : null)
                && Objects.equals(getCustomModelData(firstMeta), getCustomModelData(secondMeta))
                && Objects.equals(firstMeta.getEnchants(), secondMeta.getEnchants())
                && firstMeta.isUnbreakable() == secondMeta.isUnbreakable()
                && Objects.equals(firstMeta.getItemFlags(), secondMeta.getItemFlags());
    }

    @SuppressWarnings("unchecked")
    private Object normalizeSerializedValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, nestedValue) -> {
                if (key == null || nestedValue == null) {
                    return;
                }
                if ("amount".equals(String.valueOf(key))) {
                    return;
                }
                normalized.put(String.valueOf(key), normalizeSerializedValue(nestedValue));
            });
            return normalized;
        }
        if (value instanceof List) {
            List<Object> normalized = new ArrayList<>();
            for (Object entry : (List<Object>) value) {
                if (entry != null) {
                    normalized.add(normalizeSerializedValue(entry));
                }
            }
            return normalized;
        }
        return value;
    }
}
