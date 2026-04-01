package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.gui.CraftGuiHolder;
import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiCloseListener implements Listener {

    private final MapUtil mapUtil;

    public GuiCloseListener(MapUtil mapUtil) {
        this.mapUtil = mapUtil;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            InventoryHolder holder = event.getView().getTopInventory().getHolder();
            if (!(holder instanceof CraftGuiHolder)) {
                return;
            }
            Player player = (Player) event.getPlayer();
            mapUtil.removePlayer(player.getUniqueId());
        }
    }
}
