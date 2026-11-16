package net.azisaba.craftgui.api

import net.azisaba.craftgui.util.MapUtil
import org.bukkit.entity.Player
import java.util.UUID

class CraftGUIAPIImpl(private val mapUtil: MapUtil) : CraftGUIAPI {

    override fun getUserPreference(player: Player): UserPreference = getUserPreference(player.uniqueId)

    override fun getUserPreference(uuid: UUID): UserPreference = UserPreferenceImpl(uuid, mapUtil)

    private class UserPreferenceImpl(override val uniqueId: UUID, private val mapUtil: MapUtil) : UserPreference {

        override var isSoundEnabled: Boolean
            get() = mapUtil.isSoundToggleOn(uniqueId)
            set(value) = mapUtil.setSoundToggleState(uniqueId, value)

        override var isShowResultItems: Boolean
            get() = mapUtil.isShowResultItems(uniqueId)
            set(value) = mapUtil.setShowResultItems(uniqueId, value)

        override var isCraftableOnly: Boolean
            get() = mapUtil.isCraftableOnlyEnabled(uniqueId)
            set(value) = mapUtil.setCraftableOnlyState(uniqueId, value)

        override var isStashEnabled: Boolean
            get() = mapUtil.isStashEnabled(uniqueId)
            set(value) = mapUtil.setStashEnabled(uniqueId, value)
    }
}
