package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.mythic.MythicAdapter;
import net.azisaba.craftgui.platform.PlatformAdapter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MythicItemUtil {

    private final Plugin plugin;
    private final ItemNameUtil itemNameUtil;
    private final MythicAdapter mythicAdapter;
    private final PlatformAdapter platformAdapter;

    private volatile Map<String, ItemStack> itemStackCache = Collections.emptyMap();
    private volatile Map<String, String> displayNameCache = Collections.emptyMap();
    private volatile Map<String, List<String>> loreCache = Collections.emptyMap();
    private volatile Map<ItemSignature, String> signatureToMmidCache = Collections.emptyMap();

    public MythicItemUtil(Plugin plugin, ItemNameUtil itemNameUtil, MythicAdapter mythicAdapter, PlatformAdapter platformAdapter) {
        this.plugin = plugin;
        this.itemNameUtil = itemNameUtil;
        this.mythicAdapter = mythicAdapter;
        this.platformAdapter = platformAdapter;
    }

    public void rebuildCache() {
        try {
            if (mythicAdapter == null || !mythicAdapter.isAvailable()) {
                clearCache();
                plugin.getLogger().warning("MythicMobs is not available. Mythic item cache was cleared.");
                return;
            }

            Map<String, ItemStack> allItems = mythicAdapter.getAllItems();
            Map<String, ItemStack> newItemStackCache = new HashMap<>();
            Map<String, String> newDisplayNameCache = new HashMap<>();
            Map<String, List<String>> newLoreCache = new HashMap<>();
            Map<ItemSignature, String> newSignatureToMmidCache = new HashMap<>();

            for (Map.Entry<String, ItemStack> entry : allItems.entrySet()) {
                String mmid = entry.getKey();
                ItemStack builtItem = entry.getValue();
                if (ItemUtil.isAir(builtItem)) {
                    continue;
                }

                ItemStack normalizedItem = builtItem.clone();
                normalizedItem.setAmount(1);
                newItemStackCache.put(mmid, normalizedItem);

                String displayName = mythicAdapter.getDisplayName(mmid);
                if (displayName != null && !displayName.isEmpty()) {
                    newDisplayNameCache.put(mmid, displayName);
                }

                List<String> lore = mythicAdapter.getLore(mmid);
                newLoreCache.put(mmid, lore);
                newSignatureToMmidCache.putIfAbsent(createSignature(normalizedItem), mmid);
            }

            this.itemStackCache = Collections.unmodifiableMap(newItemStackCache);
            this.displayNameCache = Collections.unmodifiableMap(newDisplayNameCache);
            this.loreCache = Collections.unmodifiableMap(newLoreCache);
            this.signatureToMmidCache = Collections.unmodifiableMap(newSignatureToMmidCache);

            plugin.getLogger().info("Rebuilt Mythic item cache: " + newItemStackCache.size() + " items.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to rebuild Mythic item cache", e);
        }
    }

    private void clearCache() {
        this.itemStackCache = Collections.emptyMap();
        this.displayNameCache = Collections.emptyMap();
        this.loreCache = Collections.emptyMap();
        this.signatureToMmidCache = Collections.emptyMap();
    }

    public String getMMIDFromNBT(ItemStack item) {
        if (mythicAdapter == null || !mythicAdapter.isAvailable()) {
            return null;
        }
        return mythicAdapter.getMMIDFromItem(item);
    }

    public ItemStack getItemStackFromMMID(String mmid) {
        ItemStack cached = itemStackCache.get(mmid);
        if (cached != null) {
            return cached.clone();
        }

        if (mythicAdapter != null && mythicAdapter.isAvailable()) {
            return mythicAdapter.getItemStack(mmid);
        }
        return null;
    }

    public String getDisplayNameFromMMID(String mmid) {
        String cached = displayNameCache.get(mmid);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        if (mythicAdapter != null && mythicAdapter.isAvailable()) {
            return mythicAdapter.getDisplayName(mmid);
        }
        return mmid != null ? mmid : "不明なMMID";
    }

    public List<String> getLoreFromMMID(String mmid) {
        List<String> cached = loreCache.get(mmid);
        if (cached != null) {
            return cached;
        }

        if (mythicAdapter != null && mythicAdapter.isAvailable()) {
            return mythicAdapter.getLore(mmid);
        }
        return Collections.emptyList();
    }

    public String resolveDisplayName(CraftingMaterial material, Player player) {
        if (material.isMythicItem()) {
            if (material.getDisplayName() != null && !material.getDisplayName().isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', material.getDisplayName());
            }
            return getDisplayNameFromMMID(material.getMmid());
        }
        if (material.getDisplayName() != null && !material.getDisplayName().isEmpty()) {
            return ChatColor.translateAlternateColorCodes('&', material.getDisplayName());
        }
        return itemNameUtil.getName(material.getMaterial(), player);
    }

    public CompletableFuture<String> findMMIDAsync(ItemStack item) {
        if (ItemUtil.isAir(item)) {
            return CompletableFuture.completedFuture(null);
        }

        String mmid = getMMIDFromNBT(item);
        if (mmid != null) {
            return CompletableFuture.completedFuture(mmid);
        }

        String cached = signatureToMmidCache.get(createSignature(item));
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> findMMIDByFullScan(item));
    }

    private String findMMIDByFullScan(ItemStack item) {
        try {
            if (mythicAdapter == null || !mythicAdapter.isAvailable()) {
                return null;
            }
            Map<String, ItemStack> allItems = mythicAdapter.getAllItems();
            for (Map.Entry<String, ItemStack> entry : allItems.entrySet()) {
                if (isSimilar(item, entry.getValue())) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Async Mythic item full scan failed", e);
        }
        return null;
    }

    private ItemSignature createSignature(ItemStack item) {
        if (item == null) {
            return new ItemSignature(Material.AIR, "", Collections.emptyList(), null);
        }

        boolean hasMeta = item.hasItemMeta();
        ItemMeta meta = hasMeta ? item.getItemMeta() : null;
        String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : "";
        List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : Collections.emptyList();
        Integer customModelData = (meta != null) ? platformAdapter.getCustomModelData(meta) : null;
        return new ItemSignature(item.getType(), displayName, lore, customModelData);
    }

    private boolean isSimilar(ItemStack stack1, ItemStack stack2) {
        return createSignature(stack1).equals(createSignature(stack2));
    }

    private static final class ItemSignature {
        private final Material material;
        private final String displayName;
        private final List<String> lore;
        private final Integer customModelData;

        private ItemSignature(Material material, String displayName, List<String> lore, Integer customModelData) {
            this.material = material;
            this.displayName = displayName;
            this.lore = Collections.unmodifiableList(new ArrayList<>(lore));
            this.customModelData = customModelData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ItemSignature)) {
                return false;
            }
            ItemSignature that = (ItemSignature) o;
            return material == that.material
                    && Objects.equals(displayName, that.displayName)
                    && Objects.equals(lore, that.lore)
                    && Objects.equals(customModelData, that.customModelData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, displayName, lore, customModelData);
        }
    }
}
