package net.azisaba.craftgui.util

import org.bukkit.Material
import org.bukkit.entity.Player

class ItemNameUtil(private val assetDownloadUtil: AssetDownloadUtil) {

    fun getName(material: Material, player: Player): String {
        val langMap = assetDownloadUtil.getTranslations(player.locale().toString().lowercase())
            ?: assetDownloadUtil.getTranslations("en_us")

        val prefix = if (material.isBlock) "block.minecraft." else "item.minecraft."
        val translationKey = prefix + material.key.key

        return langMap?.get(translationKey) ?: getDefaultName(material)
    }

    private fun getDefaultName(material: Material): String =
        material.name.replace('_', ' ').lowercase()
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
