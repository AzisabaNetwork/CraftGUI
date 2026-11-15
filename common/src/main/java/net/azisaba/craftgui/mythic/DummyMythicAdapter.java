package net.azisaba.craftgui.mythic;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DummyMythicAdapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ItemStack getItemStack(String mmid) {
        return null;
    }

    @Override
    public String getDisplayName(String mmid) {
        return mmid;
    }

    @Override
    public List<String> getLore(String mmid) {
        return Collections.emptyList();
    }

    @Override
    public String getMMIDFromItem(ItemStack item) {
        return null;
    }

    @Override
    public Map<String, ItemStack> getAllItems() {
        return Collections.emptyMap();
    }

    @Override
    public void registerReloadListener(Plugin plugin, Runnable onReload) {
        // no-op
    }
}
