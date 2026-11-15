package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.platform.PlatformAdapter;
import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GuiCloseListener implements Listener {

    private final MapUtil mapUtil;
    private final PlatformAdapter platformAdapter;

    public GuiCloseListener(MapUtil mapUtil, PlatformAdapter platformAdapter) {
        this.mapUtil = mapUtil;
        this.platformAdapter = platformAdapter;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Player settings and page state are persisted for the session and cleaned up in PlayerQuitListener.
    }
}
