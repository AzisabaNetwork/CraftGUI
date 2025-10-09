package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GuiCloseListener implements Listener {

    private final MapUtil mapUtil;

    public GuiCloseListener(MapUtil mapUtil) {
        this.mapUtil = mapUtil;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            if (!event.getView().getTitle().startsWith("CraftGUI")) {
                return;
            }
            Player player = (Player) event.getPlayer();
            mapUtil.removePlayer(player.getUniqueId());
        }
    }
}
