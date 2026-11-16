package net.azisaba.craftgui.util

import net.azisaba.craftgui.data.CraftingMaterial
import net.azisaba.craftgui.data.RecipeBranch
import net.azisaba.craftgui.platform.PlatformAdapter
import net.azisaba.itemstash.ItemStash
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.acrylicstyle.storageBox.utils.StorageBoxItemInfo
import java.util.IdentityHashMap

class InventoryUtil(
    private val mythicItemUtil: MythicItemUtil,
    private val mapUtil: MapUtil,
    private val platformAdapter: PlatformAdapter,
) {

    fun createSnapshot(player: Player): InventorySnapshot =
        InventorySnapshot(player, mapUtil.isStashEnabled(player.uniqueId))

    /** 1回のGUI描画中に何度も行われるインベントリ走査・MMID解決の結果をキャッシュする */
    inner class InventorySnapshot internal constructor(player: Player, internal val stashEnabled: Boolean) {
        internal val inventoryItems = ArrayList<ItemStack>()
        internal val storageBoxes = ArrayList<StorageBoxItemInfo>()
        internal val storageContents: Array<ItemStack?>
        private val mmidCache = IdentityHashMap<ItemStack, String?>()
        private val sampleCache = IdentityHashMap<CraftingMaterial, ItemStack?>()
        internal val simpleCountCache = HashMap<String, Long>()
        internal val dataCountCache = IdentityHashMap<CraftingMaterial, Long>()
        internal val resultCapacityCache = IdentityHashMap<CraftingMaterial, Long>()

        init {
            val inv = player.inventory
            for (stack in inv.contents) {
                if (stack == null || ItemUtil.isAir(stack)) continue
                val info = StorageBoxUtil.getItemInfo(stack)
                if (info != null) {
                    if (info.type != null) {
                        storageBoxes.add(info)
                    }
                } else {
                    inventoryItems.add(stack)
                }
            }
            storageContents = inv.storageContents
        }

        internal fun getMMID(item: ItemStack): String? =
            mmidCache.getOrPut(item) { mythicItemUtil.getMMIDFromNBT(item) }

        internal fun getSample(material: CraftingMaterial): ItemStack? =
            sampleCache.getOrPut(material) { getItemStackFromMaterial(material) }
    }

    fun matches(inventoryItem: ItemStack?, requiredMaterial: CraftingMaterial): Boolean =
        matchesInternal(inventoryItem, requiredMaterial, null)

    private fun matches(snapshot: InventorySnapshot, inventoryItem: ItemStack?, requiredMaterial: CraftingMaterial): Boolean =
        matchesInternal(inventoryItem, requiredMaterial, snapshot)

    private fun matchesInternal(inventoryItem: ItemStack?, requiredMaterial: CraftingMaterial, snapshot: InventorySnapshot?): Boolean {
        if (inventoryItem == null || ItemUtil.isAir(inventoryItem)) {
            return false
        }
        val sample: () -> ItemStack? = {
            if (snapshot != null) snapshot.getSample(requiredMaterial) else getItemStackFromMaterial(requiredMaterial)
        }
        return when {
            requiredMaterial.isMythicItem -> {
                val inventoryItemMMID =
                    if (snapshot != null) snapshot.getMMID(inventoryItem) else mythicItemUtil.getMMIDFromNBT(inventoryItem)
                (inventoryItemMMID != null && requiredMaterial.mmid == inventoryItemMMID) || isSimilar(inventoryItem, sample())
            }
            requiredMaterial.hasItemStackData() -> isSimilar(inventoryItem, sample())
            else -> inventoryItem.type == requiredMaterial.material && !hasCustomName(inventoryItem)
        }
    }

    private fun hasCustomName(item: ItemStack): Boolean = item.hasItemMeta() && item.itemMeta?.hasDisplayName() == true

    private fun matchesStorageBox(snapshot: InventorySnapshot, info: StorageBoxItemInfo, requiredMaterial: CraftingMaterial): Boolean {
        val type = info.type
        if (info.isEmpty || type == null) {
            return false
        }
        val stored: ItemStack? = info.contentItemStack
        return when {
            requiredMaterial.isMythicItem -> {
                val sample = snapshot.getSample(requiredMaterial)
                sample != null && type == sample.type && stored != null && matches(snapshot, stored, requiredMaterial)
            }
            requiredMaterial.hasItemStackData() -> {
                val sample = snapshot.getSample(requiredMaterial)
                sample != null && type == sample.type && stored != null && isSimilar(stored, sample)
            }
            else -> type == requiredMaterial.material && stored != null && !hasCustomName(stored)
        }
    }

    fun countItems(player: Player, material: CraftingMaterial): Long = countItems(createSnapshot(player), material)

    fun countItems(snapshot: InventorySnapshot, material: CraftingMaterial): Long {
        if (material.hasItemStackData()) {
            return snapshot.dataCountCache.getOrPut(material) { computeCountItems(snapshot, material) }
        }
        val key = if (material.isMythicItem) {
            "M:${material.mmid}"
        } else {
            "P:${material.material?.name ?: "null"}"
        }
        return snapshot.simpleCountCache.getOrPut(key) { computeCountItems(snapshot, material) }
    }

    private fun computeCountItems(snapshot: InventorySnapshot, material: CraftingMaterial): Long {
        var count = 0L
        for (info in snapshot.storageBoxes) {
            if (matchesStorageBox(snapshot, info, material)) {
                count += info.amount
            }
        }
        for (stack in snapshot.inventoryItems) {
            if (matches(snapshot, stack, material)) {
                count += stack.amount
            }
        }
        return count
    }

    fun calculateMaxCraftableAmount(player: Player, requiredBranches: List<RecipeBranch>, resultItems: List<CraftingMaterial>): Long =
        calculateMaxCraftableAmount(createSnapshot(player), requiredBranches, resultItems)

    fun calculateMaxCraftableAmount(snapshot: InventorySnapshot, requiredBranches: List<RecipeBranch>, resultItems: List<CraftingMaterial>): Long {
        val maxCraftableByBranches = calculateMaxCraftableByMaterial(snapshot, requiredBranches)
        if (maxCraftableByBranches <= 0) return 0
        return minOf(maxCraftableByBranches, calculateMaxCraftableByInventory(snapshot, resultItems))
    }

    fun calculateMaxCraftableByMaterial(snapshot: InventorySnapshot, requiredBranches: List<RecipeBranch>?): Long {
        if (requiredBranches.isNullOrEmpty()) return 0

        var maxCraftableByBranches = 0L
        for (branch in requiredBranches) {
            var branchMax = Long.MAX_VALUE
            for (item in branch.materials) {
                if (item.amount <= 0) continue
                branchMax = minOf(branchMax, countItems(snapshot, item) / item.amount)
                if (branchMax == 0L) break
            }
            maxCraftableByBranches = maxOf(maxCraftableByBranches, branchMax)
        }
        return maxCraftableByBranches
    }

    fun calculateMaxCraftableByInventory(snapshot: InventorySnapshot, resultItems: List<CraftingMaterial>?): Long {
        if (resultItems.isNullOrEmpty() || snapshot.stashEnabled) {
            return Long.MAX_VALUE
        }

        var maxCraftableByInventory = Long.MAX_VALUE
        for (result in resultItems) {
            if (result.amount <= 0) continue
            val capacity = snapshot.resultCapacityCache.getOrPut(result) { computeResultCapacity(snapshot, result) }
            if (capacity != Long.MAX_VALUE) {
                maxCraftableByInventory = minOf(maxCraftableByInventory, capacity / result.amount)
                if (maxCraftableByInventory == 0L) return 0
            }
        }
        return maxCraftableByInventory
    }

    private fun computeResultCapacity(snapshot: InventorySnapshot, result: CraftingMaterial): Long {
        val sampleStack = snapshot.getSample(result)
        if (sampleStack == null || ItemUtil.isAir(sampleStack)) return Long.MAX_VALUE
        val sampleType = sampleStack.type
        for (info in snapshot.storageBoxes) {
            if (info.type != sampleType) continue
            val stored: ItemStack? = info.contentItemStack
            if (stored != null && isSimilar(stored, sampleStack)) {
                return Long.MAX_VALUE
            }
        }
        val maxStackSize = sampleStack.maxStackSize.toLong()
        var freeSpace = 0L
        for (invItem in snapshot.storageContents) {
            if (invItem == null || ItemUtil.isAir(invItem)) {
                freeSpace += maxStackSize
            } else if (isSimilar(invItem, sampleStack)) {
                freeSpace += maxOf(0L, maxStackSize - invItem.amount)
            }
        }
        return freeSpace
    }

    fun getBestBranch(player: Player, requiredBranches: List<RecipeBranch>, craftAmount: Int): RecipeBranch? {
        val snapshot = createSnapshot(player)
        for (branch in requiredBranches) {
            var branchMax = Long.MAX_VALUE
            for (item in branch.materials) {
                if (item.amount <= 0) continue
                branchMax = minOf(branchMax, countItems(snapshot, item) / item.amount)
            }
            if (branchMax >= craftAmount) {
                return branch
            }
        }
        return requiredBranches.firstOrNull()
    }

    fun removeItems(player: Player, material: CraftingMaterial, amount: Int) {
        val inv = player.inventory
        var remaining = amount
        for (i in 0 until inv.size) {
            if (remaining <= 0) break
            val currentItem = inv.getItem(i)
            if (currentItem == null || ItemUtil.isAir(currentItem)) continue

            val info = StorageBoxUtil.getItemInfo(currentItem)
            if (info != null) {
                if (!info.isEmpty && info.type != null) {
                    val storedItem: ItemStack? = info.contentItemStack
                    if (storedItem != null && matches(storedItem, material)) {
                        remaining -= StorageBoxUtil.takeFromSlot(inv, i, remaining.toLong()).toInt()
                    }
                }
                continue
            }

            if (matches(currentItem, material)) {
                val stackAmount = currentItem.amount
                if (stackAmount <= remaining) {
                    inv.setItem(i, null)
                    remaining -= stackAmount
                } else {
                    currentItem.amount = stackAmount - remaining
                    remaining = 0
                }
            }
        }
    }

    fun giveResultItems(player: Player, resultItems: List<CraftingMaterial>, craftAmount: Int) {
        val isStashEnabled = mapUtil.isStashEnabled(player.uniqueId)
        for (result in resultItems) {
            val totalAmount = result.amount * craftAmount
            if (totalAmount <= 0) continue

            val giveItem = if (result.isMythicItem) {
                mythicItemUtil.getItemStackFromMMID(result.mmid)
            } else {
                result.material?.let { ItemStack(it) }
            } ?: continue
            giveItem.amount = totalAmount

            if (isStashEnabled) {
                ItemStash.getInstance().addItemToStash(player.uniqueId, giveItem)
                continue
            }

            if (!StorageBoxUtil.tryAddItemToStorageBox(player.inventory, giveItem)) {
                val leftOver = player.inventory.addItem(giveItem)
                if (leftOver.isNotEmpty()) {
                    leftOver.values.forEach { ItemStash.getInstance().addItemToStash(player.uniqueId, it) }
                    player.sendMessage("${ChatColor.YELLOW}インベントリに入りきらなかったアイテムをStashに保管しました．")
                }
            }
        }
    }

    fun getItemStackFromMaterial(material: CraftingMaterial?): ItemStack? = when {
        material == null -> null
        material.isMythicItem -> mythicItemUtil.getItemStackFromMMID(material.mmid)
        material.hasItemStackData() -> platformAdapter.deserializeItemStack(material.itemStackData)
        else -> material.material?.let { ItemStack(it) }
    }

    fun isSimilar(baseItem: ItemStack?, internalItem: ItemStack?): Boolean = platformAdapter.isSimilar(baseItem, internalItem)
}
