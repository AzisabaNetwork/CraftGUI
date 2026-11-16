package net.azisaba.craftgui.util

import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.mythic.MythicAdapter
import net.kyori.adventure.text.Component
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
) {

    @Volatile private var itemStackCache: Map<String, ItemStack> = emptyMap()
    @Volatile private var displayNameCache: Map<String, Component> = emptyMap()
    @Volatile private var loreCache: Map<String, List<Component>> = emptyMap()
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
            val newDisplayNameCache = HashMap<String, Component>()
            val newLoreCache = HashMap<String, List<Component>>()
            val newSignatureToMmidCache = HashMap<ItemSignature, String>()

            for ((mmid, builtItem) in adapter.getAllItems()) {
                if (ItemUtil.isAir(builtItem)) continue

                val normalizedItem = builtItem.clone().apply { amount = 1 }
                newItemStackCache[mmid] = normalizedItem
                adapter.getDisplayName(mmid)?.let { newDisplayNameCache[mmid] = it }
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

    /** MythicMobs 側で表示名が設定されていれば返す */
    fun findMythicDisplayName(mmid: String?): Component? =
        displayNameCache[mmid] ?: availableAdapter?.getDisplayName(mmid)

    fun getLoreFromMMID(mmid: String?): List<Component> {
        loreCache[mmid]?.let { return it }
        return availableAdapter?.getLore(mmid) ?: emptyList()
    }

    /**
     * GUI・チャット表示用の名前。
     * MythicMobs アイテムは MythicMobs 側の表示名を優先し，
     * バニラアイテムはクライアントの言語で表示される翻訳コンポーネントを使う。
     */
    fun resolveDisplayName(material: CraftingMaterial): Component {
        val customName = material.displayName?.takeIf { it.isNotEmpty() }?.let { Text.legacy(it) }
        if (material.isMythicItem) {
            return findMythicDisplayName(material.mmid) ?: customName ?: Component.text(material.mmid ?: "不明なMMID")
        }
        return customName ?: material.material?.let { Component.translatable(it) } ?: Component.text("不明なアイテム")
    }

    /** ログ出力用のプレーンテキスト名。バニラアイテムはダウンロード済みの言語ファイルから解決する。 */
    fun resolvePlainName(material: CraftingMaterial, player: Player): String {
        val vanilla = material.material
        if (!material.isMythicItem && material.displayName.isNullOrEmpty() && vanilla != null) {
            return itemNameUtil.getName(vanilla, player)
        }
        return Text.plain(resolveDisplayName(material))
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
            return ItemSignature(Material.AIR, null, emptyList(), null)
        }
        val meta = if (item.hasItemMeta()) item.itemMeta else null
        return ItemSignature(
            item.type,
            meta?.displayName(),
            meta?.lore().orEmpty(),
            ItemUtil.getCustomModelData(meta),
        )
    }

    private fun isSimilar(stack1: ItemStack, stack2: ItemStack): Boolean =
        createSignature(stack1) == createSignature(stack2)

    private data class ItemSignature(
        val material: Material,
        val displayName: Component?,
        val lore: List<Component>,
        val customModelData: Any?,
    )
}
