package net.azisaba.craftgui.util

import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

object ItemUtil {

    @JvmStatic
    fun isAir(item: ItemStack?): Boolean = item == null || item.type.isAir

    /**
     * CustomModelData コンポーネント (1.21.4+) の比較用の値。設定されていなければ null。
     */
    @JvmStatic
    fun getCustomModelData(meta: ItemMeta?): Any? {
        if (meta == null || !meta.hasCustomModelDataComponent()) return null
        val component = meta.customModelDataComponent
        return listOf(component.floats, component.flags, component.strings, component.colors)
    }

    /** アイテムの類似性比較。isSimilar で一致しない場合は amount を除いたシリアライズ結果やメタ情報で比較する。 */
    @JvmStatic
    fun isSimilar(first: ItemStack?, second: ItemStack?): Boolean {
        if (first == null || second == null) return false
        if (first.type != second.type) return false
        if (first.isSimilar(second)) return true
        return compareSerializedItems(first, second) || compareByItemMeta(first, second)
    }

    @JvmStatic
    fun serialize(item: ItemStack?): Map<String, Any?> =
        item?.let { LinkedHashMap<String, Any?>(it.serialize()) } ?: emptyMap()

    @JvmStatic
    fun deserialize(data: Map<String, Any?>?): ItemStack? {
        if (data.isNullOrEmpty()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            ItemStack.deserialize(LinkedHashMap(data) as Map<String, Any>)
        } catch (_: Exception) {
            null
        }
    }

    /** レシピGUI用に，属性・エンチャント・耐久値などの不要な表示を隠す */
    @JvmStatic
    fun applyCleanFlags(meta: ItemMeta) {
        meta.lore(null)
        meta.addItemFlags(*ItemFlag.entries.toTypedArray())
    }

    private fun compareSerializedItems(first: ItemStack, second: ItemStack): Boolean =
        normalizeSerializedValue(first.serialize()) == normalizeSerializedValue(second.serialize())

    private fun compareByItemMeta(first: ItemStack, second: ItemStack): Boolean {
        val firstMeta = if (first.hasItemMeta()) first.itemMeta else null
        val secondMeta = if (second.hasItemMeta()) second.itemMeta else null
        if (firstMeta == null || secondMeta == null) {
            return firstMeta == null && secondMeta == null
        }

        return firstMeta.displayName() == secondMeta.displayName() &&
            firstMeta.lore() == secondMeta.lore() &&
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
