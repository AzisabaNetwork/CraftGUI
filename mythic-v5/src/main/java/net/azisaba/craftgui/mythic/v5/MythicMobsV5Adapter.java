package net.azisaba.craftgui.mythic.v5;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import io.lumine.mythic.core.items.MythicItem;
import net.azisaba.craftgui.mythic.MythicAdapter;
import net.azisaba.craftgui.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class MythicMobsV5Adapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        try {
            return MythicBukkit.inst() != null && MythicBukkit.inst().getItemManager() != null;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public ItemStack getItemStack(String mmid) {
        if (mmid == null || mmid.isEmpty()) return null;
        try {
            if (MythicBukkit.inst() == null || MythicBukkit.inst().getItemManager() == null) return null;
            Optional<MythicItem> opt = MythicBukkit.inst().getItemManager().getItem(mmid);
            if (opt.isPresent()) {
                ItemStack item = BukkitAdapter.adapt(opt.get().generateItemStack(1));
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
            if (MythicBukkit.inst() != null && MythicBukkit.inst().getItemManager() != null) {
                Optional<MythicItem> opt = MythicBukkit.inst().getItemManager().getItem(mmid);
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
        if (ItemUtil.isAir(item)) return null;
        try {
            if (MythicBukkit.inst() != null && MythicBukkit.inst().getItemManager() != null) {
                return MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public Map<String, ItemStack> getAllItems() {
        Map<String, ItemStack> map = new HashMap<>();
        try {
            if (MythicBukkit.inst() != null && MythicBukkit.inst().getItemManager() != null) {
                for (MythicItem mythicItem : MythicBukkit.inst().getItemManager().getItems()) {
                    String mmid = mythicItem.getInternalName();
                    ItemStack built = BukkitAdapter.adapt(mythicItem.generateItemStack(1));
                    if (!ItemUtil.isAir(built)) {
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
