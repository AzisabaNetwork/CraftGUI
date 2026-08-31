package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.gui.CraftGuiHolder;
import net.azisaba.craftgui.platform.PlatformAdapter;
import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiCloseListener implements Listener {

    private final MapUtil mapUtil;
    private final PlatformAdapter platformAdapter;

    public GuiCloseListener(MapUtil mapUtil, PlatformAdapter platformAdapter) {
        this.mapUtil = mapUtil;
        this.platformAdapter = platformAdapter;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Inventory topInventory = platformAdapter.getTopInventory(event.getView());
            if (topInventory == null) return;
            InventoryHolder holder = topInventory.getHolder();
            if (!(holder instanceof CraftGuiHolder)) {
                return;
            }
            Player player = (Player) event.getPlayer();
            mapUtil.removePlayer(player.getUniqueId());
        }
    }
}
