package net.azisaba.craftgui.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.utils.StorageBox;
import xyz.acrylicstyle.storageBox.utils.StorageBoxUtils;

import java.util.Map;

public class StorageBoxUtil {

    private static boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("StorageBox");
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
            e.printStackTrace();
        }
        return false;
    }

    public static ItemStack getStoredItem(ItemStack stack) {
        if (stack == null || !isEnabled()) return null;
        try {
            StorageBox box = StorageBox.getStorageBox(stack);
            if (box != null) {
                return box.getComponentItemStack();
            }
        } catch (NoClassDefFoundError | RuntimeException ignored) {}
        return null;
    }

    public static long getStoredAmount(ItemStack stack) {
        if (stack == null || !isEnabled()) return 0;
        try {
            StorageBox box = StorageBox.getStorageBox(stack);
            if (box != null) {
                return box.getAmount();
            }
        } catch (NoClassDefFoundError | RuntimeException ignored) {}
        return 0;
    }

    public static ItemStack setStoredAmount(ItemStack stack, long newAmount) {
        if (!isEnabled()) return stack;
        try {
            StorageBox box = StorageBox.getStorageBox(stack);
            if (box != null) {
                box.setAmount(newAmount);
                return box.getItemStack();
            }
        } catch (NoClassDefFoundError | RuntimeException ignored) {}
        return stack;
    }
}
