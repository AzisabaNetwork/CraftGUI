package net.azisaba.craftgui.util;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.minecraft.server.v1_15_R1.NBTTagCompound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MythicItemUtil implements Listener {

    private final CraftGUI plugin;
    private final ItemNameUtil itemNameUtil;

    private volatile Map<String, ItemStack> itemStackCache = Collections.emptyMap();
    private volatile Map<String, String> displayNameCache = Collections.emptyMap();
    private volatile Map<String, List<String>> loreCache = Collections.emptyMap();
    private volatile Map<ItemSignature, String> signatureToMmidCache = Collections.emptyMap();

    public MythicItemUtil(CraftGUI plugin, ItemNameUtil itemNameUtil) {
        this.plugin = plugin;
        this.itemNameUtil = itemNameUtil;
    }

    public void rebuildCache() {
        try {
            MythicMobs mythicMobs = MythicMobs.inst();
            if (mythicMobs == null) {
                clearCache();
                plugin.getLogger().warning("MythicMobs is not available. Mythic item cache was cleared.");
                return;
            }

            ItemManager manager = mythicMobs.getItemManager();
            if (manager == null) {
                clearCache();
                plugin.getLogger().warning("MythicMobs ItemManager is not available. Mythic item cache was cleared.");
                return;
            }

            Map<String, ItemStack> newItemStackCache = new HashMap<>();
            Map<String, String> newDisplayNameCache = new HashMap<>();
            Map<String, List<String>> newLoreCache = new HashMap<>();
            Map<ItemSignature, String> newSignatureToMmidCache = new HashMap<>();

            Collection<MythicItem> allItems = manager.getItems();
            for (MythicItem mythicItem : allItems) {
                String mmid = mythicItem.getInternalName();
                ItemStack builtItem = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                if (builtItem == null || builtItem.getType().isAir()) {
                    continue;
                }

                ItemStack normalizedItem = builtItem.clone();
                normalizedItem.setAmount(1);
                newItemStackCache.put(mmid, normalizedItem);

                String displayName = mythicItem.getDisplayName();
                if (displayName != null && !displayName.isEmpty()) {
                    newDisplayNameCache.put(mmid, ChatColor.translateAlternateColorCodes('&', displayName));
                }

                List<String> lore = extractLore(normalizedItem);
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
        try {
            net.minecraft.server.v1_15_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
            if (!nmsItem.hasTag()) {
                return null;
            }
            NBTTagCompound tag = nmsItem.getTag();
            if (tag == null || !tag.hasKey("MYTHIC_TYPE")) {
                return null;
            }
            return tag.getString("MYTHIC_TYPE");
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<MythicItem> getMythicItem(String mmid) {
        if (mmid == null || mmid.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (MythicMobs.inst() == null) {
                return Optional.empty();
            }
            return MythicMobs.inst().getItemManager().getItem(mmid);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ItemStack getItemStackFromMMID(String mmid) {
        ItemStack cached = itemStackCache.get(mmid);
        if (cached != null) {
            return cached.clone();
        }

        return getMythicItem(mmid)
                .map(mythicItem -> {
                    ItemStack item = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                    if (item == null) {
                        return null;
                    }
                    item.setAmount(1);
                    return item;
                })
                .orElse(null);
    }

    public String getDisplayNameFromMMID(String mmid) {
        String cached = displayNameCache.get(mmid);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        return getMythicItem(mmid)
                .map(item -> {
                    String displayName = item.getDisplayName();
                    return (displayName != null && !displayName.isEmpty()) ? ChatColor.translateAlternateColorCodes('&', displayName) : null;
                })
                .orElse(mmid != null ? mmid : "不明なMMID");
    }

    public List<String> getLoreFromMMID(String mmid) {
        List<String> cached = loreCache.get(mmid);
        if (cached != null) {
            return cached;
        }

        return getMythicItem(mmid)
                .map(mythicItem -> {
                    ItemStack itemStack = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                    return extractLore(itemStack);
                })
                .orElse(Collections.emptyList());
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
        if (item == null || item.getType().isAir()) {
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
            MythicMobs mm = MythicMobs.inst();
            if (mm == null) {
                return null;
            }
            ItemManager manager = mm.getItemManager();
            if (manager == null) {
                return null;
            }

            for (MythicItem mythicItem : manager.getItems()) {
                ItemStack mmItem = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                if (isSimilar(item, mmItem)) {
                    return mythicItem.getInternalName();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Async Mythic item full scan failed", e);
        }
        return null;
    }

    @EventHandler
    public void onMythicReload(MythicReloadedEvent event) {
        rebuildCache();
    }

    private List<String> extractLore(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta() || !itemStack.getItemMeta().hasLore()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(itemStack.getItemMeta().getLore().stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList())));
    }

    private ItemSignature createSignature(ItemStack item) {
        if (item == null) {
            return new ItemSignature(Material.AIR, "", Collections.emptyList(), null);
        }

        boolean hasMeta = item.hasItemMeta();
        ItemMeta meta = hasMeta ? item.getItemMeta() : null;
        String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : "";
        List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : Collections.emptyList();
        Integer customModelData = (meta != null && meta.hasCustomModelData()) ? meta.getCustomModelData() : null;
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
