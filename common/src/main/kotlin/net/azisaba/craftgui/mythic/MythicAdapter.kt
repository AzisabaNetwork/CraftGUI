package net.azisaba.craftgui.mythic

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

interface MythicAdapter {

    fun isAvailable(): Boolean

    fun getItemStack(mmid: String?): ItemStack?

    /** MythicMobs 側に表示名が設定されていなければ null */
    fun getDisplayName(mmid: String?): Component?

    fun getLore(mmid: String?): List<Component>

    fun getMMIDFromItem(item: ItemStack?): String?

    fun getAllItems(): Map<String, ItemStack>

    fun registerReloadListener(plugin: Plugin, onReload: Runnable)
}

class DummyMythicAdapter : MythicAdapter {
    override fun isAvailable(): Boolean = false
    override fun getItemStack(mmid: String?): ItemStack? = null
    override fun getDisplayName(mmid: String?): Component? = null
    override fun getLore(mmid: String?): List<Component> = emptyList()
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

        // MythicMobs 5 系のみ対応 (MythicMobs クラスが無い環境でもロードできるようリフレクションで生成する)
        return try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            Class.forName("net.azisaba.craftgui.mythic.MythicMobsAdapter")
                .getDeclaredConstructor().newInstance() as MythicAdapter
        } catch (_: Throwable) {
            DummyMythicAdapter()
        }
    }
}
