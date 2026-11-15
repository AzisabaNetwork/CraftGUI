package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeBranch;
import net.azisaba.craftgui.platform.PlatformAdapter;
import net.azisaba.itemstash.ItemStash;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xyz.acrylicstyle.storageBox.utils.StorageBoxItemInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class InventoryUtil {

    private final MythicItemUtil mythicItemUtil;
    private final MapUtil mapUtil;
    private final PlatformAdapter platformAdapter;

    public InventoryUtil(MythicItemUtil mythicItemUtil, MapUtil mapUtil, PlatformAdapter platformAdapter) {
        this.mythicItemUtil = mythicItemUtil;
        this.mapUtil = mapUtil;
        this.platformAdapter = platformAdapter;
    }

    public InventorySnapshot createSnapshot(Player player) {
        return new InventorySnapshot(player, mapUtil.isStashEnabled(player.getUniqueId()));
    }

    public final class InventorySnapshot {
        private final boolean stashEnabled;
        private final List<ItemStack> inventoryItems = new ArrayList<>();
        private final List<StorageBoxItemInfo> storageBoxes = new ArrayList<>();
        private final ItemStack[] storageContents;
        private final Map<ItemStack, String> mmidCache = new IdentityHashMap<>();
        private final Map<CraftingMaterial, ItemStack> sampleCache = new IdentityHashMap<>();
        private final Map<String, Long> simpleCountCache = new HashMap<>();
        private final Map<CraftingMaterial, Long> dataCountCache = new IdentityHashMap<>();
        private final Map<CraftingMaterial, Long> resultCapacityCache = new IdentityHashMap<>();

        private InventorySnapshot(Player player, boolean stashEnabled) {
            this.stashEnabled = stashEnabled;
            PlayerInventory inv = player.getInventory();
            for (ItemStack stack : inv.getContents()) {
                if (ItemUtil.isAir(stack)) continue;
                StorageBoxItemInfo info = StorageBoxUtil.getItemInfo(stack);
                if (info != null) {
                    if (info.getType() != null) {
                        storageBoxes.add(info);
                    }
                } else {
                    inventoryItems.add(stack);
                }
            }
            this.storageContents = inv.getStorageContents();
        }

        private String getMMID(ItemStack item) {
            if (mmidCache.containsKey(item)) {
                return mmidCache.get(item);
            }
            String mmid = mythicItemUtil.getMMIDFromNBT(item);
            mmidCache.put(item, mmid);
            return mmid;
        }

        private ItemStack getSample(CraftingMaterial material) {
            if (sampleCache.containsKey(material)) {
                return sampleCache.get(material);
            }
            ItemStack sample = getItemStackFromMaterial(material);
            sampleCache.put(material, sample);
            return sample;
        }
    }

    public boolean matches(ItemStack inventoryItem, CraftingMaterial requiredMaterial) {
        if (ItemUtil.isAir(inventoryItem)) {
            return false;
        }
        if (requiredMaterial.isMythicItem()) {
            String inventoryItemMMID = mythicItemUtil.getMMIDFromNBT(inventoryItem);
            if (inventoryItemMMID != null && requiredMaterial.getMmid().equals(inventoryItemMMID)) {
                return true;
            }
            ItemStack sampleItem = mythicItemUtil.getItemStackFromMMID(requiredMaterial.getMmid());
            return isSimilar(inventoryItem, sampleItem);
        } else if (requiredMaterial.hasItemStackData()) {
            ItemStack sampleItem = getItemStackFromMaterial(requiredMaterial);
            return isSimilar(inventoryItem, sampleItem);
        } else {
            boolean hasCustomName = inventoryItem.hasItemMeta() && inventoryItem.getItemMeta().hasDisplayName();
            return inventoryItem.getType() == requiredMaterial.getMaterial() && !hasCustomName;
        }
    }

    private boolean matches(InventorySnapshot snapshot, ItemStack inventoryItem, CraftingMaterial requiredMaterial) {
        if (ItemUtil.isAir(inventoryItem)) {
            return false;
        }
        if (requiredMaterial.isMythicItem()) {
            String inventoryItemMMID = snapshot.getMMID(inventoryItem);
            if (inventoryItemMMID != null && requiredMaterial.getMmid().equals(inventoryItemMMID)) {
                return true;
            }
            ItemStack sampleItem = snapshot.getSample(requiredMaterial);
            return isSimilar(inventoryItem, sampleItem);
        } else if (requiredMaterial.hasItemStackData()) {
            ItemStack sampleItem = snapshot.getSample(requiredMaterial);
            return isSimilar(inventoryItem, sampleItem);
        } else {
            boolean hasCustomName = inventoryItem.hasItemMeta() && inventoryItem.getItemMeta().hasDisplayName();
            return inventoryItem.getType() == requiredMaterial.getMaterial() && !hasCustomName;
        }
    }

    private boolean matchesStorageBox(InventorySnapshot snapshot, StorageBoxItemInfo info, CraftingMaterial requiredMaterial) {
        if (info.isEmpty() || info.getType() == null) {
            return false;
        }
        if (requiredMaterial.isMythicItem()) {
            ItemStack sample = snapshot.getSample(requiredMaterial);
            if (sample == null || info.getType() != sample.getType()) {
                return false;
            }
            ItemStack stored = info.getContentItemStack();
            return stored != null && matches(snapshot, stored, requiredMaterial);
        } else if (requiredMaterial.hasItemStackData()) {
            ItemStack sample = snapshot.getSample(requiredMaterial);
            if (sample == null || info.getType() != sample.getType()) {
                return false;
            }
            ItemStack stored = info.getContentItemStack();
            return stored != null && isSimilar(stored, sample);
        } else {
            if (info.getType() != requiredMaterial.getMaterial()) {
                return false;
            }
            ItemStack stored = info.getContentItemStack();
            return stored != null && !(stored.hasItemMeta() && stored.getItemMeta().hasDisplayName());
        }
    }

    public long countItems(Player player, CraftingMaterial material) {
        return countItems(createSnapshot(player), material);
    }

    public long countItems(InventorySnapshot snapshot, CraftingMaterial material) {
        if (material.hasItemStackData()) {
            Long cached = snapshot.dataCountCache.get(material);
            if (cached != null) return cached;
            long count = computeCountItems(snapshot, material);
            snapshot.dataCountCache.put(material, count);
            return count;
        }
        String key = material.isMythicItem()
                ? "M:" + material.getMmid()
                : "P:" + (material.getMaterial() != null ? material.getMaterial().name() : "null");
        Long cached = snapshot.simpleCountCache.get(key);
        if (cached != null) return cached;
        long count = computeCountItems(snapshot, material);
        snapshot.simpleCountCache.put(key, count);
        return count;
    }

    private long computeCountItems(InventorySnapshot snapshot, CraftingMaterial material) {
        long count = 0;
        for (StorageBoxItemInfo info : snapshot.storageBoxes) {
            if (matchesStorageBox(snapshot, info, material)) {
                count += info.getAmount();
            }
        }
        for (ItemStack stack : snapshot.inventoryItems) {
            if (matches(snapshot, stack, material)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public long calculateMaxCraftableAmount(Player player, List<RecipeBranch> requiredBranches, List<CraftingMaterial> resultItems) {
        return calculateMaxCraftableAmount(createSnapshot(player), requiredBranches, resultItems);
    }

    public long calculateMaxCraftableAmount(InventorySnapshot snapshot, List<RecipeBranch> requiredBranches, List<CraftingMaterial> resultItems) {
        long maxCraftableByBranches = calculateMaxCraftableByMaterial(snapshot, requiredBranches);
        if (maxCraftableByBranches <= 0) return 0;

        long maxCraftableByInventory = calculateMaxCraftableByInventory(snapshot, resultItems);
        return Math.min(maxCraftableByBranches, maxCraftableByInventory);
    }

    public long calculateMaxCraftableByMaterial(InventorySnapshot snapshot, List<RecipeBranch> requiredBranches) {
        if (requiredBranches == null || requiredBranches.isEmpty()) return 0;

        long maxCraftableByBranches = 0;
        for (RecipeBranch branch : requiredBranches) {
            long branchMax = Long.MAX_VALUE;
            for (CraftingMaterial item : branch.getMaterials()) {
                if (item.getAmount() <= 0) continue;
                long ownedAmount = countItems(snapshot, item);
                branchMax = Math.min(branchMax, ownedAmount / item.getAmount());
                if (branchMax == 0) break;
            }
            maxCraftableByBranches = Math.max(maxCraftableByBranches, branchMax);
        }
        return maxCraftableByBranches;
    }

    public long calculateMaxCraftableByInventory(InventorySnapshot snapshot, List<CraftingMaterial> resultItems) {
        if (resultItems == null || resultItems.isEmpty() || snapshot.stashEnabled) {
            return Long.MAX_VALUE;
        }

        long maxCraftableByInventory = Long.MAX_VALUE;
        for (CraftingMaterial result : resultItems) {
            if (result.getAmount() <= 0) continue;
            Long cachedCapacity = snapshot.resultCapacityCache.get(result);
            long capacity;
            if (cachedCapacity != null) {
                capacity = cachedCapacity;
            } else {
                capacity = computeResultCapacity(snapshot, result);
                snapshot.resultCapacityCache.put(result, capacity);
            }
            if (capacity != Long.MAX_VALUE) {
                maxCraftableByInventory = Math.min(maxCraftableByInventory, capacity / result.getAmount());
                if (maxCraftableByInventory == 0) return 0;
            }
        }
        return maxCraftableByInventory;
    }

    private long computeResultCapacity(InventorySnapshot snapshot, CraftingMaterial result) {
        ItemStack sampleStack = snapshot.getSample(result);
        if (ItemUtil.isAir(sampleStack)) return Long.MAX_VALUE;
        Material sampleType = sampleStack.getType();
        for (StorageBoxItemInfo info : snapshot.storageBoxes) {
            if (info.getType() != sampleType) continue;
            ItemStack stored = info.getContentItemStack();
            if (stored != null && isSimilar(stored, sampleStack)) {
                return Long.MAX_VALUE;
            }
        }
        long maxStackSize = sampleStack.getMaxStackSize();
        long freeSpace = 0;
        for (ItemStack invItem : snapshot.storageContents) {
            if (ItemUtil.isAir(invItem)) {
                freeSpace += maxStackSize;
            } else if (isSimilar(invItem, sampleStack)) {
                freeSpace += Math.max(0, maxStackSize - invItem.getAmount());
            }
        }
        return freeSpace;
    }

    public RecipeBranch getBestBranch(Player player, List<RecipeBranch> requiredBranches, int craftAmount) {
        InventorySnapshot snapshot = createSnapshot(player);
        for (RecipeBranch branch : requiredBranches) {
            long branchMax = Long.MAX_VALUE;
            for (CraftingMaterial item : branch.getMaterials()) {
                if (item.getAmount() <= 0) continue;
                long ownedAmount = countItems(snapshot, item);
                branchMax = Math.min(branchMax, ownedAmount / item.getAmount());
            }
            if (branchMax >= craftAmount) {
                return branch;
            }
        }
        return requiredBranches.isEmpty() ? null : requiredBranches.get(0);
    }

    public void removeItems(Player player, CraftingMaterial material, int amount) {
        PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            if (remaining <= 0) break;
            ItemStack currentItem = inv.getItem(i);
            if (ItemUtil.isAir(currentItem)) continue;
            StorageBoxItemInfo info = StorageBoxUtil.getItemInfo(currentItem);
            if (info != null) {
                if (!info.isEmpty() && info.getType() != null) {
                    ItemStack storedItem = info.getContentItemStack();
                    if (storedItem != null && matches(storedItem, material)) {
                        long taken = StorageBoxUtil.takeFromSlot(inv, i, remaining);
                        remaining -= (int) taken;
                    }
                }
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
            ItemStack giveItem;
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
        if (material == null) return null;
        ItemStack item;
        if (material.isMythicItem()) {
            item = mythicItemUtil.getItemStackFromMMID(material.getMmid());
        } else if (material.hasItemStackData()) {
            item = platformAdapter.deserializeItemStack(material.getItemStackData());
        } else {
            item = new ItemStack(material.getMaterial());
        }
        return item;
    }

    public boolean isSimilar(ItemStack baseItem, ItemStack internalItem) {
        return platformAdapter.isSimilar(baseItem, internalItem);
    }
}
