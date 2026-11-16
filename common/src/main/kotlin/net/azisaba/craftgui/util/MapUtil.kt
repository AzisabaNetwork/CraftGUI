package net.azisaba.craftgui.util

import java.util.UUID

class MapUtil {

    private val playerPages = HashMap<UUID, Int>()
    private val loreToggleState = HashMap<UUID, Boolean>()
    private val compactViewToggleState = HashMap<UUID, Boolean>()
    private val soundToggleState = HashMap<UUID, Boolean>()
    private val showResultItemsState = HashMap<UUID, Boolean>()
    private val stashEnabledState = HashMap<UUID, Boolean>()
    private val craftableOnlyState = HashMap<UUID, Boolean>()

    fun getPlayerPage(uuid: UUID): Int = playerPages[uuid] ?: 1
    fun setPlayerPage(uuid: UUID, page: Int) {
        playerPages[uuid] = page
    }

    fun isLoreToggledOn(uuid: UUID): Boolean = loreToggleState[uuid] ?: true
    fun isCompactViewEnabled(uuid: UUID): Boolean = compactViewToggleState[uuid] ?: false
    fun isSoundToggleOn(uuid: UUID): Boolean = soundToggleState[uuid] ?: true
    fun isShowResultItems(uuid: UUID): Boolean = showResultItemsState[uuid] ?: true
    fun isStashEnabled(uuid: UUID): Boolean = stashEnabledState[uuid] ?: false
    fun isCraftableOnlyEnabled(uuid: UUID): Boolean = craftableOnlyState[uuid] ?: false

    fun setSoundToggleState(uuid: UUID, isOn: Boolean) {
        soundToggleState[uuid] = isOn
    }

    fun setShowResultItems(uuid: UUID, show: Boolean) {
        showResultItemsState[uuid] = show
    }

    fun setStashEnabled(uuid: UUID, enabled: Boolean) {
        stashEnabledState[uuid] = enabled
    }

    fun setCraftableOnlyState(uuid: UUID, enabled: Boolean) {
        craftableOnlyState[uuid] = enabled
    }

    fun toggleLoreState(uuid: UUID) {
        loreToggleState[uuid] = !isLoreToggledOn(uuid)
    }

    fun toggleCompactViewState(uuid: UUID) {
        compactViewToggleState[uuid] = !isCompactViewEnabled(uuid)
    }

    fun toggleSoundState(uuid: UUID) {
        soundToggleState[uuid] = !isSoundToggleOn(uuid)
    }

    fun toggleShowResultItems(uuid: UUID) {
        showResultItemsState[uuid] = !isShowResultItems(uuid)
    }

    fun toggleStashEnabled(uuid: UUID) {
        stashEnabledState[uuid] = !isStashEnabled(uuid)
    }

    fun toggleCraftableOnlyState(uuid: UUID) {
        craftableOnlyState[uuid] = !isCraftableOnlyEnabled(uuid)
    }

    fun removePlayer(uuid: UUID) {
        playerPages.remove(uuid)
        loreToggleState.remove(uuid)
        compactViewToggleState.remove(uuid)
        soundToggleState.remove(uuid)
        showResultItemsState.remove(uuid)
        stashEnabledState.remove(uuid)
        craftableOnlyState.remove(uuid)
    }
}
