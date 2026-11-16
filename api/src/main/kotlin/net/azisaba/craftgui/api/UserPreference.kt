package net.azisaba.craftgui.api

import java.util.UUID

/**
 * Java からは従来どおり getUniqueId() / isSoundEnabled() / setSoundEnabled(boolean) などで参照できる。
 */
interface UserPreference {

    val uniqueId: UUID

    var isSoundEnabled: Boolean

    var isShowResultItems: Boolean

    var isCraftableOnly: Boolean

    var isStashEnabled: Boolean
}
