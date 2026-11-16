package net.azisaba.craftgui.platform.legacy

import net.azisaba.craftgui.platform.AbstractPlatformAdapter
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ItemMeta
import java.lang.reflect.Method

class LegacyPlatformAdapter : AbstractPlatformAdapter() {

    override fun getTopInventory(view: InventoryView?): Inventory? {
        if (view == null) return null
        GET_TOP_INVENTORY_METHOD?.let { method ->
            try {
                return method.invoke(view) as Inventory?
            } catch (_: Throwable) {
            }
        }
        return view.topInventory
    }

    override fun getCustomModelData(meta: ItemMeta?): Int? {
        if (meta == null) return null
        val hasMethod = HAS_CUSTOM_MODEL_DATA_METHOD ?: return null
        val getMethod = GET_CUSTOM_MODEL_DATA_METHOD ?: return null
        return try {
            if (hasMethod.invoke(meta) as Boolean) getMethod.invoke(meta) as Int? else null
        } catch (_: Throwable) {
            null
        }
    }

    override fun setCustomModelData(meta: ItemMeta?, customModelData: Int?) {
        if (meta == null) return
        val setMethod = SET_CUSTOM_MODEL_DATA_METHOD ?: return
        try {
            setMethod.invoke(meta, customModelData)
        } catch (_: Throwable) {
        }
    }

    override fun applyCleanFlags(meta: ItemMeta?) {
        if (meta == null) return
        meta.lore = null
        try {
            meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_POTION_EFFECTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ENCHANTS,
            )
        } catch (_: Throwable) {
        }
    }

    private companion object {
        val GET_TOP_INVENTORY_METHOD: Method? = findMethod(InventoryView::class.java, "getTopInventory")

        // 1.13 には CustomModelData が存在しない
        val HAS_CUSTOM_MODEL_DATA_METHOD: Method? = findMethod(ItemMeta::class.java, "hasCustomModelData")
        val GET_CUSTOM_MODEL_DATA_METHOD: Method? = findMethod(ItemMeta::class.java, "getCustomModelData")
        val SET_CUSTOM_MODEL_DATA_METHOD: Method? =
            findMethod(ItemMeta::class.java, "setCustomModelData", Int::class.javaObjectType)

        fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
            runCatching { clazz.getMethod(name, *parameterTypes) }.getOrNull()
    }
}
