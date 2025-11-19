package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.itemstash.ItemStash;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
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
            ItemStack storedItem = StorageBoxUtil.getStoredItem(stack);
            if (storedItem != null && matches(storedItem, material)) {
                count += StorageBoxUtil.getStoredAmount(stack);
                continue;
            }
            if (matches(stack, material)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public long calculateMaxCraftableAmount(Player player, List<CraftingMaterial> requiredItems, List<CraftingMaterial> resultItems) {
        if (requiredItems.isEmpty()) return 0;

        long maxCraftableByMaterial = Long.MAX_VALUE;
        for (CraftingMaterial item : requiredItems) {
            if (item.getAmount() <= 0) continue;
            long ownedAmount = countItems(player, item);
            maxCraftableByMaterial = Math.min(maxCraftableByMaterial, ownedAmount / item.getAmount());
        }

        if (resultItems == null || resultItems.isEmpty()) {
            return maxCraftableByMaterial;
        }

        if (mapUtil.isStashEnabled(player.getUniqueId())) {
            return maxCraftableByMaterial;
        }

        long maxCraftableByInventory = Long.MAX_VALUE;
        for (CraftingMaterial result : resultItems) {
            if (result.getAmount() <= 0) continue;

            ItemStack sampleStack = getItemStackFromMaterial(result);
            if (sampleStack == null || sampleStack.getType().isAir()) continue;

            boolean hasBox = false;
            for(ItemStack invItem : player.getInventory().getContents()) {
                ItemStack stored = StorageBoxUtil.getStoredItem(invItem);
                if(stored != null && stored.isSimilar(sampleStack)) {
                    hasBox = true;
                    break;
                }
            }
            if (hasBox) continue;

            long maxStackSize = sampleStack.getMaxStackSize();
            long freeSpace = 0;

            for (ItemStack invItem : player.getInventory().getStorageContents()) {
                if (invItem == null || invItem.getType().isAir()) {
                    freeSpace += maxStackSize;
                } else {
                    if (isSimilarSafe(invItem, sampleStack)) {
                        freeSpace += Math.max(0, maxStackSize - invItem.getAmount());
                    }
                }
            }
            maxCraftableByInventory = Math.min(maxCraftableByInventory, freeSpace / result.getAmount());
        }
        return Math.min(maxCraftableByMaterial, maxCraftableByInventory);
    }

    public void removeItems(Player player, CraftingMaterial material, int amount) {
        PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            if (remaining <= 0) break;
            ItemStack currentItem = inv.getItem(i);
            if (currentItem == null || currentItem.getType().isAir()) continue;
            ItemStack storedItem = StorageBoxUtil.getStoredItem(currentItem);
            if (storedItem != null && matches(storedItem, material)) {
                long boxAmount = StorageBoxUtil.getStoredAmount(currentItem);
                long removeAmount = Math.min(boxAmount, remaining);
                ItemStack updatedBox = StorageBoxUtil.setStoredAmount(currentItem, boxAmount - removeAmount);
                if (updatedBox != null) {
                    inv.setItem(i, updatedBox);
                }
                remaining -= (int) removeAmount;
                continue;
            }
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
        boolean isStashEnabled = mapUtil.isStashEnabled(player.getUniqueId());
        for (CraftingMaterial result : resultItems) {
            int totalAmount = result.getAmount() * craftAmount;
            if (totalAmount <= 0) continue;
            ItemStack giveItem = null;
            if (result.isMythicItem()) {
                giveItem = mythicItemUtil.getItemStackFromMMID(result.getMmid());
            } else {
                giveItem = new ItemStack(result.getMaterial());
            }

            if (giveItem == null) continue;
            giveItem.setAmount(totalAmount);

            if (isStashEnabled) {
                ItemStash.getInstance().addItemToStash(player.getUniqueId(), giveItem);
            } else {
                boolean addedToBox = StorageBoxUtil.tryAddItemToStorageBox(player.getInventory(), giveItem);
                if (!addedToBox) {
                    HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(giveItem);
                    if (!leftOver.isEmpty()) {
                        leftOver.values().forEach(item ->
                                ItemStash.getInstance().addItemToStash(player.getUniqueId(), item)
                        );
                        player.sendMessage(org.bukkit.ChatColor.YELLOW + "インベントリに入りきらなかったアイテムをStashに保管しました．");
                    }
                }
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

    private boolean isSimilarSafe(ItemStack baseItem, ItemStack internalItem) {
        if (baseItem == null || internalItem == null) return false;
        try {
            return baseItem.isSimilar(internalItem);
        } catch (Exception e) {
            if (baseItem.getType() != internalItem.getType()) return false;
            if (baseItem.getDurability() != internalItem.getDurability()) return false;
            boolean hasBaseItemMeta = baseItem.hasItemMeta();
            boolean hasInternalItemMeta = internalItem.hasItemMeta();
            if (hasBaseItemMeta != hasInternalItemMeta) return false;
            if (!hasBaseItemMeta) return true;
            ItemMeta baseItemMeta = baseItem.getItemMeta();
            ItemMeta internalItemMeta = internalItem.getItemMeta();
            String name1 = baseItemMeta.hasDisplayName() ? baseItemMeta.getDisplayName() : "";
            String name2 = internalItemMeta.hasDisplayName() ? internalItemMeta.getDisplayName() : "";
            if (!name1.equals(name2)) return false;
            List<String> l1 = baseItemMeta.hasLore() ? baseItemMeta.getLore() : Collections.emptyList();
            List<String> l2 = internalItemMeta.hasLore() ? internalItemMeta.getLore() : Collections.emptyList();
            if (!l1.equals(l2)) return false;
            if (baseItemMeta.hasCustomModelData() != internalItemMeta.hasCustomModelData()) return false;
            if (baseItemMeta.hasCustomModelData() && baseItemMeta.getCustomModelData() != internalItemMeta.getCustomModelData()) return false;
            if (!baseItemMeta.getEnchants().equals(internalItemMeta.getEnchants())) return false;
            if (baseItemMeta.isUnbreakable() != internalItemMeta.isUnbreakable()) return false;
            if (!baseItemMeta.getItemFlags().equals(internalItemMeta.getItemFlags())) return false;
            return true;
        }
    }
}