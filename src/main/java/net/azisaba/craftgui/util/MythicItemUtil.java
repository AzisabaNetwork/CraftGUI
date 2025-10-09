package net.azisaba.craftgui.util;

import de.tr7zw.changeme.nbtapi.NBTItem;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.data.CraftingMaterial;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MythicItemUtil {

    private final ItemNameUtil itemNameUtil;

    public MythicItemUtil(ItemNameUtil itemNameUtil) {
        this.itemNameUtil = itemNameUtil;
    }

    private Optional<MythicItem> getMythicItem(String mmid) {
        if (mmid == null || mmid.isEmpty()) {
            return Optional.empty();
        }
        return MythicMobs.inst().getItemManager().getItem(mmid);
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

    public List<String> resolveLore(CraftingMaterial material) {
        if (material.isMythicItem()) {
            return getLoreFromMMID(material.getMmid());
        }
        return Collections.emptyList();
    }
}
