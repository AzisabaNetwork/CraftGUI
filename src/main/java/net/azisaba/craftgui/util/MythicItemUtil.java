package net.azisaba.craftgui.util;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import net.minecraft.server.v1_15_R1.NBTTagCompound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MythicItemUtil {

    private final CraftGUI plugin;
    private final ItemNameUtil itemNameUtil;

    private final Map<Material, Map<String, ItemStack>> itemCache = new HashMap<>();
    private long lastCacheUpdate = 0;

    public MythicItemUtil(CraftGUI plugin, ItemNameUtil itemNameUtil) {
        this.plugin = plugin;
        this.itemNameUtil = itemNameUtil;
    }

    public String getMMIDFromNBT(ItemStack item) {
        try {
            net.minecraft.server.v1_15_R1.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
            if (!nmsItem.hasTag()) return null;
            NBTTagCompound tag = nmsItem.getTag();
            if (tag == null || !tag.hasKey("MYTHIC_TYPE")) return null;
            return tag.getString("MYTHIC_TYPE");
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<MythicItem> getMythicItem(String mmid) {
        if (mmid == null || mmid.isEmpty()) return Optional.empty();
        try {
            if (MythicMobs.inst() == null) return Optional.empty();
            return MythicMobs.inst().getItemManager().getItem(mmid);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ItemStack getItemStackFromMMID(String mmid) {
        return getMythicItem(mmid)
                .map(mythicItem -> ((BukkitItemStack) mythicItem.generateItemStack(1)).build())
                .orElse(null);
    }

    public String getDisplayNameFromMMID(String mmid) {
        return getMythicItem(mmid)
                .map(item -> {
                    String displayName = item.getDisplayName();
                    return (displayName != null && !displayName.isEmpty()) ? ChatColor.translateAlternateColorCodes('&', displayName) : null;
                })
                .orElse(mmid != null ? mmid : "不明なMMID");
    }

    public List<String> getLoreFromMMID(String mmid) {
        return getMythicItem(mmid)
                .map(mythicItem -> {
                    ItemStack itemStack = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                    if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
                        return itemStack.getItemMeta().getLore().stream()
                                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                                .collect(Collectors.toList());
                    }
                    return Collections.<String>emptyList();
                })
                .orElse(Collections.emptyList());
    }

    public String resolveDisplayName(CraftingMaterial material, Player player) {
        if (material.isMythicItem()) {
            if (material.getDisplayName() != null && !material.getDisplayName().isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', material.getDisplayName());
            }
            return getDisplayNameFromMMID(material.getMmid());
        } else {
            return itemNameUtil.getName(material.getMaterial(), player);
        }
    }

    public CompletableFuture<String> findMMIDAsync(ItemStack item) {
        if (item == null || item.getType().isAir()) return CompletableFuture.completedFuture(null);
        String mmid = getMMIDFromNBT(item);
        if (mmid != null) return CompletableFuture.completedFuture(mmid);
        return CompletableFuture.supplyAsync(() -> {
            try {
                MythicMobs mm = MythicMobs.inst();
                if (mm == null) return null;
                ItemManager manager = mm.getItemManager();
                if (manager == null) return null;
                Collection<MythicItem> allItems = manager.getItems();
                for (MythicItem mythicItem : allItems) {
                    ItemStack mmItem = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                    if (isSimilar(item, mmItem)) {
                        return mythicItem.getInternalName();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Async MythicItem search failed", e);
            }
            return null;
        });
    }

    private boolean isSimilar(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) return false;
        if (stack1.getType() != stack2.getType()) return false;
        boolean hasMeta1 = stack1.hasItemMeta();
        boolean hasMeta2 = stack2.hasItemMeta();
        if (!hasMeta1 && !hasMeta2) return true;
        ItemMeta meta1 = stack1.getItemMeta();
        ItemMeta meta2 = stack2.getItemMeta();
        String name1 = (hasMeta1 && meta1.hasDisplayName()) ? meta1.getDisplayName() : "";
        String name2 = (hasMeta2 && meta2.hasDisplayName()) ? meta2.getDisplayName() : "";
        if (!name1.equals(name2)) return false;
        List<String> lore1 = (hasMeta1 && meta1.hasLore()) ? meta1.getLore() : Collections.emptyList();
        List<String> lore2 = (hasMeta2 && meta2.hasLore()) ? meta2.getLore() : Collections.emptyList();
        if (!lore1.equals(lore2)) return false;
        return true;
    }
}