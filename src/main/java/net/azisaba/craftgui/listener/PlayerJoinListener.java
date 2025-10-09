package net.azisaba.craftgui.listener;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final CraftGUI plugin;
    private final MapUtil mapUtil;

    public PlayerJoinListener(CraftGUI plugin, MapUtil mapUtil) {
        this.plugin = plugin;
        this.mapUtil = mapUtil;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().loadPlayerData(player.getUniqueId(), mapUtil);
    }
}