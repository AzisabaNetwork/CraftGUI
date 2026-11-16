package net.azisaba.craftgui.listener

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.util.MapUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerJoinListener(private val plugin: CraftGUI, private val mapUtil: MapUtil) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.playerDataManager.loadPlayerData(event.player.uniqueId, mapUtil)
    }
}

/** プレイヤー設定とページ状態はセッション中保持し，ログアウト時に保存・破棄する。 */
class PlayerQuitListener(private val plugin: CraftGUI, private val mapUtil: MapUtil) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        plugin.playerDataManager.savePlayerData(uuid, mapUtil)
        mapUtil.removePlayer(uuid)
    }
}
