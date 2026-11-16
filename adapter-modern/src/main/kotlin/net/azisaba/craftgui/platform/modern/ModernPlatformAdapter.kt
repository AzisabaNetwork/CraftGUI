package net.azisaba.craftgui.platform.modern

import net.azisaba.craftgui.platform.AbstractPlatformAdapter
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ItemMeta

class ModernPlatformAdapter : AbstractPlatformAdapter() {

    override fun getTopInventory(view: InventoryView?): Inventory? = view?.topInventory

    @Suppress("DEPRECATION")
    override fun getCustomModelData(meta: ItemMeta?): Int? {
        if (meta == null) return null
        return try {
            if (meta.hasCustomModelData()) meta.customModelData else null
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    override fun setCustomModelData(meta: ItemMeta?, customModelData: Int?) {
        if (meta == null) return
        try {
            meta.setCustomModelData(customModelData)
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    override fun applyCleanFlags(meta: ItemMeta?) {
        if (meta == null) return
        meta.lore = null
        try {
            ItemFlag.entries.forEach { meta.addItemFlags(it) }
        } catch (_: Throwable) {
        }
    }
}
