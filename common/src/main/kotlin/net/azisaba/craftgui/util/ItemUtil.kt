package net.azisaba.craftgui.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object ItemUtil {

    private val AIR_NAMES = setOf("CAVE_AIR", "VOID_AIR", "LEGACY_AIR")

    @JvmStatic
    fun isAir(item: ItemStack?): Boolean = item == null || isAir(item.type)

    @JvmStatic
    fun isAir(material: Material?): Boolean =
        material == null || material == Material.AIR || material.name in AIR_NAMES

    @JvmStatic
    fun getMaterialSafe(name: String?, fallback: Material): Material =
        name?.let { Material.matchMaterial(it) } ?: fallback
}
