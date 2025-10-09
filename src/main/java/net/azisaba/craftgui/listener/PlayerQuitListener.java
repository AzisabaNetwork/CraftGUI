package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final CraftGUI plugin;
    private final MapUtil mapUtil;

    public PlayerQuitListener(CraftGUI plugin, MapUtil mapUtil) {
        this.plugin = plugin;
        this.mapUtil = mapUtil;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId(), mapUtil);
        mapUtil.removePlayer(event.getPlayer().getUniqueId());
    }
}