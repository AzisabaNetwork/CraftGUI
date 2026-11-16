package net.azisaba.craftgui.platform

import org.bukkit.inventory.ItemStack

/**
 * Legacy / Modern アダプタ共通の ItemStack 比較・シリアライズ処理。
 */
abstract class AbstractPlatformAdapter : PlatformAdapter {

    override fun isSimilar(first: ItemStack?, second: ItemStack?): Boolean {
        if (first == null || second == null) return false
        if (first.type != second.type) return false
        try {
            if (first.isSimilar(second)) return true
        } catch (_: Throwable) {
        }
        return compareSerializedItems(first, second) || compareByItemMeta(first, second)
    }

    override fun serializeItemStack(item: ItemStack?): Map<String, Any?> =
        item?.let { LinkedHashMap<String, Any?>(it.serialize()) } ?: emptyMap()

    override fun deserializeItemStack(data: Map<String, Any?>?): ItemStack? {
        if (data.isNullOrEmpty()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            ItemStack.deserialize(LinkedHashMap(data) as Map<String, Any>)
        } catch (_: Throwable) {
            null
        }
    }

    private fun compareSerializedItems(first: ItemStack, second: ItemStack): Boolean =
        normalizeSerializedValue(first.serialize()) == normalizeSerializedValue(second.serialize())

    private fun compareByItemMeta(first: ItemStack, second: ItemStack): Boolean {
        val firstMeta = if (first.hasItemMeta()) first.itemMeta else null
        val secondMeta = if (second.hasItemMeta()) second.itemMeta else null
        if (firstMeta == null || secondMeta == null) {
            return firstMeta == null && secondMeta == null
        }

        return (if (firstMeta.hasDisplayName()) firstMeta.displayName else null) ==
            (if (secondMeta.hasDisplayName()) secondMeta.displayName else null) &&
            (if (firstMeta.hasLore()) firstMeta.lore else null) == (if (secondMeta.hasLore()) secondMeta.lore else null) &&
            getCustomModelData(firstMeta) == getCustomModelData(secondMeta) &&
            firstMeta.enchants == secondMeta.enchants &&
            firstMeta.isUnbreakable == secondMeta.isUnbreakable &&
            firstMeta.itemFlags == secondMeta.itemFlags
    }

    /** amount と null 値を取り除いて再帰的に正規化する */
    private fun normalizeSerializedValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> {
            val normalized = LinkedHashMap<String, Any?>()
            for ((key, nestedValue) in value) {
                if (key == null || nestedValue == null) continue
                val keyString = key.toString()
                if (keyString == "amount") continue
                normalized[keyString] = normalizeSerializedValue(nestedValue)
            }
            normalized
        }
        is List<*> -> value.filterNotNull().map { normalizeSerializedValue(it) }
        else -> value
    }
}
