package net.azisaba.craftgui.mythic.v4;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitItemStack;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import net.azisaba.craftgui.mythic.MythicAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MythicMobsV4Adapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        try {
            return MythicMobs.inst() != null && MythicMobs.inst().getItemManager() != null;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public ItemStack getItemStack(String mmid) {
        if (mmid == null || mmid.isEmpty()) return null;
        try {
            ItemManager manager = MythicMobs.inst().getItemManager();
            if (manager == null) return null;
            Optional<MythicItem> opt = manager.getItem(mmid);
            if (opt.isPresent()) {
                ItemStack item = ((BukkitItemStack) opt.get().generateItemStack(1)).build();
                if (item != null) {
                    item.setAmount(1);
                    return item;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public String getDisplayName(String mmid) {
        if (mmid == null || mmid.isEmpty()) return mmid;
        try {
            ItemManager manager = MythicMobs.inst().getItemManager();
            if (manager != null) {
                Optional<MythicItem> opt = manager.getItem(mmid);
                if (opt.isPresent()) {
                    String displayName = opt.get().getDisplayName();
                    if (displayName != null && !displayName.isEmpty()) {
                        return ChatColor.translateAlternateColorCodes('&', displayName);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return mmid;
    }

    @Override
    public List<String> getLore(String mmid) {
        ItemStack item = getItemStack(mmid);
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> list = new ArrayList<>();
            for (String line : item.getItemMeta().getLore()) {
                list.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            return Collections.unmodifiableList(list);
        }
        return Collections.emptyList();
    }

    @Override
    public String getMMIDFromItem(ItemStack item) {
        if (net.azisaba.craftgui.util.ItemUtil.isAir(item)) return null;
        try {
            io.lumine.xikage.mythicmobs.util.jnbt.CompoundTag tag = MythicMobs.inst().getVolatileCodeHandler().getItemHandler().getNBTData(item);
            if (tag != null && tag.getValue().containsKey("MYTHIC_TYPE")) {
                return ((io.lumine.xikage.mythicmobs.util.jnbt.StringTag) tag.getValue().get("MYTHIC_TYPE")).getValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public Map<String, ItemStack> getAllItems() {
        Map<String, ItemStack> map = new HashMap<>();
        try {
            ItemManager manager = MythicMobs.inst().getItemManager();
            if (manager != null) {
                for (MythicItem mythicItem : manager.getItems()) {
                    String mmid = mythicItem.getInternalName();
                    ItemStack built = ((BukkitItemStack) mythicItem.generateItemStack(1)).build();
                    if (!net.azisaba.craftgui.util.ItemUtil.isAir(built)) {
                        built.setAmount(1);
                        map.put(mmid, built);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    @Override
    public void registerReloadListener(Plugin plugin, Runnable onReload) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onReload(MythicReloadedEvent event) {
                if (onReload != null) {
                    onReload.run();
                }
            }
        }, plugin);
    }
}
