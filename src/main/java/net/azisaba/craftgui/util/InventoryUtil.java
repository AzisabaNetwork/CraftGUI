package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.itemstash.ItemStash;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

import java.util.List;

public class InventoryUtil {

    private final MythicItemUtil mythicItemUtil;
    private final MapUtil mapUtil;

    public InventoryUtil(MythicItemUtil mythicItemUtil, MapUtil mapUtil) {
        this.mythicItemUtil = mythicItemUtil;
        this.mapUtil = mapUtil;
    }

    public boolean matches(ItemStack inventoryItem, CraftingMaterial requiredMaterial) {
        if (inventoryItem == null || inventoryItem.getType().isAir()) {
            return false;
        }
        if (requiredMaterial.isMythicItem()) {
            String inventoryItemMMID = mythicItemUtil.getMythicType(inventoryItem);
            if (inventoryItemMMID != null && requiredMaterial.getMmid().equals(inventoryItemMMID)) {
                return true;
            }
            ItemStack sampleItem = mythicItemUtil.getItemStackFromMMID(requiredMaterial.getMmid());
            if (sampleItem == null) {
                return false;
            }
            if (inventoryItem.getType() != sampleItem.getType()) {
                return false;
            }
            boolean hasInvName = inventoryItem.hasItemMeta() && inventoryItem.getItemMeta().hasDisplayName();
            boolean hasSampleName = sampleItem.hasItemMeta() && sampleItem.getItemMeta().hasDisplayName();
            if (hasInvName && hasSampleName) {
                return inventoryItem.getItemMeta().getDisplayName().equals(sampleItem.getItemMeta().getDisplayName());
            }
            return !hasInvName && !hasSampleName;
        } else {
            boolean hasCustomName = inventoryItem.hasItemMeta() && inventoryItem.getItemMeta().hasDisplayName();
            return inventoryItem.getType() == requiredMaterial.getMaterial() && !hasCustomName;
        }
    }

    public long countItems(Player player, CraftingMaterial material) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            try {
                StorageBox storageBox = StorageBox.getStorageBox(stack);
                if (storageBox != null) {
                    ItemStack componentItem = storageBox.getComponentItemStack();
                    if (componentItem != null && matches(componentItem, material)) {
                        count += storageBox.getAmount();
                    }
                    continue;
                }
            } catch (NoClassDefFoundError ignored) {}
            if (matches(stack, material)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public long calculateMaxCraftableAmount(Player player, List<CraftingMaterial> requiredItems) {
        if (requiredItems.isEmpty()) return 0;
        long maxCraftable = Long.MAX_VALUE;
        for (CraftingMaterial item : requiredItems) {
            if (item.getAmount() <= 0) continue;
            long ownedAmount = countItems(player, item);
            maxCraftable = Math.min(maxCraftable, ownedAmount / item.getAmount());
        }
        return maxCraftable;
    }

    public void removeItems(Player player, CraftingMaterial material, int amount) {
        PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            if (remaining <= 0) break;
            ItemStack currentItem = inv.getItem(i);
            if (currentItem == null || currentItem.getType().isAir()) continue;
            try {
                StorageBox storageBox = StorageBox.getStorageBox(currentItem);
                if (storageBox != null) {
                    ItemStack component = storageBox.getComponentItemStack();
                    if (component != null && matches(component, material)) {
                        long boxAmount = storageBox.getAmount();
                        long removeAmount = Math.min(boxAmount, remaining);
                        storageBox.setAmount(boxAmount - removeAmount);
                        remaining -= (int) removeAmount;
                        inv.setItem(i, storageBox.getItemStack());
                    }
                    continue;
                }
            } catch (NoClassDefFoundError ignored) {}
            if (matches(currentItem, material)) {
                int stackAmount = currentItem.getAmount();
                if (stackAmount <= remaining) {
                    inv.setItem(i, null);
                    remaining -= stackAmount;
                } else {
                    currentItem.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
            }
        }
    }

    public void giveResultItems(Player player, List<CraftingMaterial> resultItems, int craftAmount) {
        for (CraftingMaterial result : resultItems) {
            int totalAmount = result.getAmount() * craftAmount;
            if (totalAmount <= 0) continue;

            if (result.isMythicItem()) {
                String command = "mlg " + player.getName() + " " + result.getMmid() + " " + totalAmount + " 1";
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else {
                ItemStack item = new ItemStack(result.getMaterial(), totalAmount);
                player.getInventory().addItem(item);
            }
        }
    }

    public ItemStack getItemStackFromMaterial(CraftingMaterial material) {
        if (material == null) {
            return null;
        }
        ItemStack item = null;
        if (material.isMythicItem() && material.getMmid() != null) {
            item = mythicItemUtil.getItemStackFromMMID(material.getMmid());
        } else if (material.getMaterial() != null) {
            item = new ItemStack(material.getMaterial());
        }
        if (item == null) {
            return null;
        }
        item.setAmount(1);
        return item;
    }
}