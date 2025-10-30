package net.azisaba.craftgui.util;

import de.tr7zw.changeme.nbtapi.NBTItem;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.CraftingMaterial;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MythicItemUtil {

    private final CraftGUI plugin;
    private final ItemNameUtil itemNameUtil;

    public MythicItemUtil(CraftGUI plugin, ItemNameUtil itemNameUtil) {
        this.plugin = plugin;
        this.itemNameUtil = itemNameUtil;
    }

    private Optional<MythicItem> getMythicItem(String mmid) {
        if (mmid == null || mmid.isEmpty()) {
            return Optional.empty();
        }
        try {
            MythicMobs mythicMobs = MythicMobs.inst();
            if (mythicMobs == null || mythicMobs.getItemManager() == null) {
                plugin.getLogger().log(Level.SEVERE, "MythicMobsが見つかりませんでした．");
                return Optional.empty();
            }
            return mythicMobs.getItemManager().getItem(mmid);
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().log(Level.SEVERE, "MythicMobsのAPIバージョンが一致していません．");
            return Optional.empty();
        } catch (NullPointerException e) {
            plugin.getLogger().log(Level.SEVERE, "アイテムの検索中に予期せぬエラーが発生しました．", e);
            return Optional.empty();
        }
    }

    public String getMythicType(ItemStack stack) {
        return Optional.ofNullable(stack)
                .filter(item -> !item.getType().isAir())
                .map(NBTItem::new)
                .flatMap(nbti -> nbti.hasKey("MYTHIC_TYPE")
                        ? Optional.ofNullable(nbti.getString("MYTHIC_TYPE"))
                        : Optional.empty())
                .orElse(null);
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
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return ChatColor.translateAlternateColorCodes('&', displayName);
                    }
                    return mmid;
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

    public String findMythicIdByItemStack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        try {
            MythicMobs mythicMobs = MythicMobs.inst();
            if (mythicMobs == null) return null;
            ItemManager itemManager = mythicMobs.getItemManager();
            if (itemManager == null) return null;

            Collection<MythicItem> allItems = itemManager.getItems();
            for (MythicItem mythicItem : allItems) {
                ItemStack mmStack = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                NBTItem nbt = new NBTItem(mmStack, true);
                if (nbt.hasKey("MYTHIC_TYPE")) {
                    nbt.removeKey("MYTHIC_TYPE");
                    nbt.applyNBT(mmStack);
                }
                if (item.isSimilar(mmStack)) {
                    return mythicItem.getInternalName();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "MythicItemの逆引き検索中にエラーが発生しました: ", e);
        }
        return null;
    }
}
