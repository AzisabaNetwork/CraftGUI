package net.azisaba.craftgui.util

import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.mythic.MythicAdapter
import net.azisaba.craftgui.platform.PlatformAdapter
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class MythicItemUtil(
    private val plugin: Plugin,
    private val itemNameUtil: ItemNameUtil,
    private val mythicAdapter: MythicAdapter?,
    private val platformAdapter: PlatformAdapter,
) {

    @Volatile private var itemStackCache: Map<String, ItemStack> = emptyMap()
    @Volatile private var displayNameCache: Map<String, String> = emptyMap()
    @Volatile private var loreCache: Map<String, List<String>> = emptyMap()
    @Volatile private var signatureToMmidCache: Map<ItemSignature, String> = emptyMap()

    private val availableAdapter: MythicAdapter?
        get() = mythicAdapter?.takeIf { it.isAvailable() }

    fun rebuildCache() {
        try {
            val adapter = availableAdapter
            if (adapter == null) {
                clearCache()
                plugin.logger.warning("MythicMobs is not available. Mythic item cache was cleared.")
                return
            }

            val newItemStackCache = HashMap<String, ItemStack>()
            val newDisplayNameCache = HashMap<String, String>()
            val newLoreCache = HashMap<String, List<String>>()
            val newSignatureToMmidCache = HashMap<ItemSignature, String>()

            for ((mmid, builtItem) in adapter.getAllItems()) {
                if (ItemUtil.isAir(builtItem)) continue

                val normalizedItem = builtItem.clone().apply { amount = 1 }
                newItemStackCache[mmid] = normalizedItem

                val displayName = adapter.getDisplayName(mmid)
                if (!displayName.isNullOrEmpty()) {
                    newDisplayNameCache[mmid] = displayName
                }

                newLoreCache[mmid] = adapter.getLore(mmid)
                newSignatureToMmidCache.putIfAbsent(createSignature(normalizedItem), mmid)
            }

            itemStackCache = newItemStackCache
            displayNameCache = newDisplayNameCache
            loreCache = newLoreCache
            signatureToMmidCache = newSignatureToMmidCache

            plugin.logger.info("Rebuilt Mythic item cache: ${newItemStackCache.size} items.")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to rebuild Mythic item cache", e)
        }
    }

    private fun clearCache() {
        itemStackCache = emptyMap()
        displayNameCache = emptyMap()
        loreCache = emptyMap()
        signatureToMmidCache = emptyMap()
    }

    fun getMMIDFromNBT(item: ItemStack?): String? = availableAdapter?.getMMIDFromItem(item)

    fun getItemStackFromMMID(mmid: String?): ItemStack? {
        itemStackCache[mmid]?.let { return it.clone() }
        return availableAdapter?.getItemStack(mmid)
    }

    fun getDisplayNameFromMMID(mmid: String?): String? {
        val cached = displayNameCache[mmid]
        if (!cached.isNullOrEmpty()) return cached

        availableAdapter?.let { return it.getDisplayName(mmid) }
        return mmid ?: "不明なMMID"
    }

    fun getLoreFromMMID(mmid: String?): List<String> {
        loreCache[mmid]?.let { return it }
        return availableAdapter?.getLore(mmid) ?: emptyList()
    }

    fun resolveDisplayName(material: CraftingMaterial, player: Player): String? {
        val customName = material.displayName
        if (!customName.isNullOrEmpty()) {
            return ChatColor.translateAlternateColorCodes('&', customName)
        }
        if (material.isMythicItem) {
            return getDisplayNameFromMMID(material.mmid)
        }
        return material.material?.let { itemNameUtil.getName(it, player) }
    }

    fun findMMIDAsync(item: ItemStack?): CompletableFuture<String?> {
        if (item == null || ItemUtil.isAir(item)) {
            return CompletableFuture.completedFuture(null)
        }

        getMMIDFromNBT(item)?.let { return CompletableFuture.completedFuture(it) }
        signatureToMmidCache[createSignature(item)]?.let { return CompletableFuture.completedFuture(it) }

        return CompletableFuture.supplyAsync { findMMIDByFullScan(item) }
    }

    private fun findMMIDByFullScan(item: ItemStack): String? {
        try {
            val adapter = availableAdapter ?: return null
            return adapter.getAllItems().entries.firstOrNull { isSimilar(item, it.value) }?.key
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Async Mythic item full scan failed", e)
        }
        return null
    }

    private fun createSignature(item: ItemStack?): ItemSignature {
        if (item == null) {
            return ItemSignature(Material.AIR, "", emptyList(), null)
        }

        val meta = if (item.hasItemMeta()) item.itemMeta else null
        val displayName = if (meta != null && meta.hasDisplayName()) meta.displayName else ""
        val lore = if (meta != null && meta.hasLore()) meta.lore.orEmpty().toList() else emptyList()
        val customModelData = meta?.let { platformAdapter.getCustomModelData(it) }
        return ItemSignature(item.type, displayName, lore, customModelData)
    }

    private fun isSimilar(stack1: ItemStack, stack2: ItemStack): Boolean =
        createSignature(stack1) == createSignature(stack2)

    private data class ItemSignature(
        val material: Material,
        val displayName: String?,
        val lore: List<String>,
        val customModelData: Int?,
    )
}
