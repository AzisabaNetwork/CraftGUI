package net.azisaba.craftgui.mythic

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

interface MythicAdapter {

    fun isAvailable(): Boolean

    fun getItemStack(mmid: String?): ItemStack?

    fun getDisplayName(mmid: String?): String?

    fun getLore(mmid: String?): List<String>

    fun getMMIDFromItem(item: ItemStack?): String?

    fun getAllItems(): Map<String, ItemStack>

    fun registerReloadListener(plugin: Plugin, onReload: Runnable)
}

class DummyMythicAdapter : MythicAdapter {
    override fun isAvailable(): Boolean = false
    override fun getItemStack(mmid: String?): ItemStack? = null
    override fun getDisplayName(mmid: String?): String? = mmid
    override fun getLore(mmid: String?): List<String> = emptyList()
    override fun getMMIDFromItem(item: ItemStack?): String? = null
    override fun getAllItems(): Map<String, ItemStack> = emptyMap()
    override fun registerReloadListener(plugin: Plugin, onReload: Runnable) = Unit
}

object MythicAdapterProvider {

    @JvmStatic
    fun createAdapter(): MythicAdapter {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return DummyMythicAdapter()
        }

        // MythicMobs v5
        instantiate("io.lumine.mythic.bukkit.MythicBukkit", "net.azisaba.craftgui.mythic.v5.MythicMobsV5Adapter")
            ?.let { return it }

        // MythicMobs v4
        instantiate("io.lumine.xikage.mythicmobs.MythicMobs", "net.azisaba.craftgui.mythic.v4.MythicMobsV4Adapter")
            ?.let { return it }

        return DummyMythicAdapter()
    }

    private fun instantiate(markerClass: String, adapterClass: String): MythicAdapter? = try {
        Class.forName(markerClass)
        Class.forName(adapterClass).getDeclaredConstructor().newInstance() as MythicAdapter
    } catch (_: Throwable) {
        null
    }
}
