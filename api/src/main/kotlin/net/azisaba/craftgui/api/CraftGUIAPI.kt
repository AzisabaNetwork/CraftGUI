package net.azisaba.craftgui.api

import org.bukkit.entity.Player
import java.util.UUID

interface CraftGUIAPI {

    fun getUserPreference(player: Player): UserPreference

    fun getUserPreference(uuid: UUID): UserPreference
}
