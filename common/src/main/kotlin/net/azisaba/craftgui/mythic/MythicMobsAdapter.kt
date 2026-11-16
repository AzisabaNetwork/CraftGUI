package net.azisaba.craftgui.mythic

import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicReloadedEvent
import io.lumine.mythic.core.items.ItemExecutor
import io.lumine.mythic.core.items.MythicItem
import net.azisaba.craftgui.util.ItemUtil
import net.azisaba.craftgui.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

class MythicMobsAdapter : MythicAdapter {

    private val itemManager: ItemExecutor?
        get() = MythicBukkit.inst()?.itemManager

    override fun isAvailable(): Boolean = try {
        itemManager != null
    } catch (_: Throwable) {
        false
    }

    private fun findItem(mmid: String?): MythicItem? {
        if (mmid.isNullOrEmpty()) return null
        return itemManager?.getItem(mmid)?.orElse(null)
    }

    private fun build(mythicItem: MythicItem): ItemStack? =
        BukkitAdapter.adapt(mythicItem.generateItemStack(1))?.apply { amount = 1 }

    override fun getItemStack(mmid: String?): ItemStack? = try {
        findItem(mmid)?.let { build(it) }
    } catch (_: Throwable) {
        null
    }

    override fun getDisplayName(mmid: String?): Component? = try {
        findItem(mmid)?.displayName?.takeIf { it.isNotEmpty() }?.let { Text.legacy(it) }
    } catch (_: Throwable) {
        null
    }

    override fun getLore(mmid: String?): List<Component> {
        val meta = getItemStack(mmid)?.takeIf { it.hasItemMeta() }?.itemMeta ?: return emptyList()
        return meta.lore().orEmpty()
    }

    override fun getMMIDFromItem(item: ItemStack?): String? {
        if (item == null || ItemUtil.isAir(item)) return null
        return try {
            itemManager?.getMythicTypeFromItem(item)
        } catch (_: Throwable) {
            null
        }
    }

    override fun getAllItems(): Map<String, ItemStack> {
        val map = HashMap<String, ItemStack>()
        try {
            itemManager?.items?.forEach { mythicItem ->
                val built = build(mythicItem)
                if (built != null && !ItemUtil.isAir(built)) {
                    map[mythicItem.internalName] = built
                }
            }
        } catch (_: Throwable) {
        }
        return map
    }

    override fun registerReloadListener(plugin: Plugin, onReload: Runnable) {
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onReload(@Suppress("UNUSED_PARAMETER") event: MythicReloadedEvent) {
                onReload.run()
            }
        }, plugin)
    }
}
