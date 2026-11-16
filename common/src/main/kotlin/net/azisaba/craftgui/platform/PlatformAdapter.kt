package net.azisaba.craftgui.platform

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

interface PlatformAdapter {

    /**
     * IncompatibleClassChangeError (1.20.4以前のabstract class vs 1.21以降のinterface) を回避して
     * InventoryView から TopInventory を取得します。
     */
    fun getTopInventory(view: InventoryView?): Inventory?

    /** 1.13 に存在しない CustomModelData を安全に取得します。 */
    fun getCustomModelData(meta: ItemMeta?): Int?

    /** 1.13 に存在しない CustomModelData を安全に設定します。 */
    fun setCustomModelData(meta: ItemMeta?, customModelData: Int?)

    /** アイテムの類似性比較 (NBT / Data Components 互換) */
    fun isSimilar(first: ItemStack?, second: ItemStack?): Boolean

    /** ItemStack をシリアライズ (Data Components / 旧NBT 互換) */
    fun serializeItemStack(item: ItemStack?): Map<String, Any?>

    /** シリアライズデータから ItemStack を復元 */
    fun deserializeItemStack(data: Map<String, Any?>?): ItemStack?

    /** レシピGUI用のクリーンフラグ（属性・エンチャント・耐久値などの不要表示非表示化）を適用 */
    fun applyCleanFlags(meta: ItemMeta?)
}

object PlatformAdapterProvider {

    @JvmStatic
    fun createAdapter(): PlatformAdapter {
        if (isModernVersion()) {
            try {
                return instantiate("net.azisaba.craftgui.platform.modern.ModernPlatformAdapter")
            } catch (_: Throwable) {
                // fallback
            }
        }
        try {
            return instantiate("net.azisaba.craftgui.platform.legacy.LegacyPlatformAdapter")
        } catch (t: Throwable) {
            throw RuntimeException("Failed to instantiate PlatformAdapter", t)
        }
    }

    private fun instantiate(className: String): PlatformAdapter =
        Class.forName(className).getDeclaredConstructor().newInstance() as PlatformAdapter

    private fun isModernVersion(): Boolean = try {
        InventoryView::class.java.isInterface || isVersionAtLeast(Bukkit.getBukkitVersion(), 1, 20, 5)
    } catch (_: Throwable) {
        false
    }

    private fun isVersionAtLeast(versionString: String, targetMajor: Int, targetMinor: Int, targetPatch: Int): Boolean = try {
        val parts = versionString.split("-")[0].split(".")
        val major = parts[0].toInt()
        val minor = parts.getOrNull(1)?.toInt() ?: 0
        val patch = parts.getOrNull(2)?.toInt() ?: 0
        when {
            major != targetMajor -> major > targetMajor
            minor != targetMinor -> minor > targetMinor
            else -> patch >= targetPatch
        }
    } catch (_: Throwable) {
        false
    }
}
