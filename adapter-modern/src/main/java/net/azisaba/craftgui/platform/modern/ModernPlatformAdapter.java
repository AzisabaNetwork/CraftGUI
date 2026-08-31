package net.azisaba.craftgui.platform.modern;

import net.azisaba.craftgui.platform.PlatformAdapter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ModernPlatformAdapter implements PlatformAdapter {

    @Override
    public Inventory getTopInventory(InventoryView view) {
        if (view == null) return null;
        return view.getTopInventory();
    }

    @Override
    public Integer getCustomModelData(ItemMeta meta) {
        if (meta == null) return null;
        try {
            return meta.hasCustomModelData() ? meta.getCustomModelData() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void setCustomModelData(ItemMeta meta, Integer customModelData) {
        if (meta == null) return;
        try {
            meta.setCustomModelData(customModelData);
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
            // Modern ItemFlags
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
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
