package net.azisaba.craftgui.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.utils.StorageBox;
import xyz.acrylicstyle.storageBox.utils.StorageBoxItemInfo;
import xyz.acrylicstyle.storageBox.utils.StorageBoxUtils;

import java.util.Map;

public class StorageBoxUtil {

    private static boolean isEnabled() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("StorageBox");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static StorageBoxItemInfo getItemInfo(ItemStack stack) {
        if (stack == null || !isEnabled()) return null;
        try {
            return StorageBoxUtils.getStorageBoxItemInfo(stack);
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean tryAddItemToStorageBox(Inventory inventory, ItemStack stack) {
        if (!isEnabled()) return false;
        try {
            Map.Entry<Integer, StorageBox> entry = StorageBoxUtils.getStorageBoxForType(inventory, stack);
            if (entry != null) {
                StorageBox box = entry.getValue();
                box.setAmount(box.getAmount() + stack.getAmount());
                inventory.setItem(entry.getKey(), box.getItemStack());
                return true;
            }
        } catch (NoClassDefFoundError | RuntimeException e) {
            // ignored
        }
        return false;
    }

    public static ItemStack getStoredItem(ItemStack stack) {
        StorageBoxItemInfo info = getItemInfo(stack);
        return info != null ? info.getContentItemStack() : null;
    }

    public static long getStoredAmount(ItemStack stack) {
        StorageBoxItemInfo info = getItemInfo(stack);
        return info != null ? info.getAmount() : 0;
    }

    public static ItemStack setStoredAmount(ItemStack stack, long newAmount) {
        if (stack == null || !isEnabled()) return stack;
        try {
            ItemStack updated = StorageBoxUtils.setAmount(stack, newAmount);
            return updated != null ? updated : stack;
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {}
        return stack;
    }

    public static long takeFromSlot(Inventory inventory, int slot, long amount) {
        if (inventory == null || amount <= 0 || !isEnabled()) return 0;
        try {
            return StorageBoxUtils.take(inventory, slot, amount);
        } catch (NoClassDefFoundError | NoSuchMethodError | RuntimeException ignored) {}
        return 0;
    }
}
