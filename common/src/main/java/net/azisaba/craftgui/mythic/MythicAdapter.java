package net.azisaba.craftgui.mythic;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

public interface MythicAdapter {

    boolean isAvailable();

    ItemStack getItemStack(String mmid);

    String getDisplayName(String mmid);

    List<String> getLore(String mmid);

    String getMMIDFromItem(ItemStack item);

    Map<String, ItemStack> getAllItems();

    void registerReloadListener(Plugin plugin, Runnable onReload);
}
