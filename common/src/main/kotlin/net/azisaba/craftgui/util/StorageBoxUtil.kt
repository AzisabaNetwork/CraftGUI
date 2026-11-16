package net.azisaba.craftgui.util

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import xyz.acrylicstyle.storageBox.utils.StorageBoxItemInfo
import xyz.acrylicstyle.storageBox.utils.StorageBoxUtils

/**
 * StorageBox 連携。StorageBox が未導入・非互換でも例外を外に出さない。
 */
object StorageBoxUtil {

    private fun isEnabled(): Boolean = try {
        Bukkit.getPluginManager().isPluginEnabled("StorageBox")
    } catch (_: Throwable) {
        false
    }

    /** StorageBox が存在しない/非互換な場合に起きるエラーだけを握りつぶす */
    private inline fun <T> safely(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: NoClassDefFoundError) {
        fallback
    } catch (_: NoSuchMethodError) {
        fallback
    } catch (_: RuntimeException) {
        fallback
    }

    @JvmStatic
    fun getItemInfo(stack: ItemStack?): StorageBoxItemInfo? {
        if (stack == null || !isEnabled()) return null
        return safely(null) { StorageBoxUtils.getStorageBoxItemInfo(stack) }
    }

    @JvmStatic
    fun tryAddItemToStorageBox(inventory: Inventory, stack: ItemStack): Boolean {
        if (!isEnabled()) return false
        return safely(false) {
            val entry = StorageBoxUtils.getStorageBoxForType(inventory, stack) ?: return@safely false
            val box = entry.value
            box.amount = box.amount + stack.amount
            inventory.setItem(entry.key, box.itemStack)
            true
        }
    }

    @JvmStatic
    fun getStoredItem(stack: ItemStack?): ItemStack? = getItemInfo(stack)?.contentItemStack

    @JvmStatic
    fun getStoredAmount(stack: ItemStack?): Long = getItemInfo(stack)?.amount ?: 0L

    @JvmStatic
    fun setStoredAmount(stack: ItemStack?, newAmount: Long): ItemStack? {
        if (stack == null || !isEnabled()) return stack
        return safely(stack) { StorageBoxUtils.setAmount(stack, newAmount) ?: stack }
    }

    @JvmStatic
    fun takeFromSlot(inventory: Inventory?, slot: Int, amount: Long): Long {
        if (inventory == null || amount <= 0 || !isEnabled()) return 0
        return safely(0L) { StorageBoxUtils.take(inventory, slot, amount) }
    }
}
