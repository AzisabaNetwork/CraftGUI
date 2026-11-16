package net.azisaba.craftgui.data

import net.azisaba.craftgui.CraftGUI
import net.azisaba.craftgui.platform.PlatformAdapter
import net.azisaba.craftgui.util.ItemUtil
import net.azisaba.craftgui.util.MythicItemUtil
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack

class RecipeLoader(
    private val plugin: CraftGUI,
    private val mythicItemUtil: MythicItemUtil,
    private val platformAdapter: PlatformAdapter,
) {

    private val _errorDetails = ArrayList<String>()
    val errorDetails: List<String> get() = _errorDetails

    fun loadAllItems(config: FileConfiguration): MutableMap<Int, MutableMap<Int, RecipeData>> {
        _errorDetails.clear()
        val itemsByPage = LinkedHashMap<Int, MutableMap<Int, RecipeData>>()
        val itemsSection = config.getConfigurationSection("Items")
        if (itemsSection == null) {
            plugin.logger.warning("config.ymlにItemsセクションがありません．")
            return itemsByPage
        }

        for (pageKey in itemsSection.getKeys(false)) {
            if (!pageKey.lowercase().startsWith("page")) {
                addError(pageKey, "N/A", "ページキーは'page'で始まる必要があります．")
                continue
            }
            val page = pageKey.substring(4).toIntOrNull()
            if (page == null) {
                addError(pageKey, "N/A", "ページ番号が数字ではありません．")
                continue
            }
            itemsSection.getConfigurationSection(pageKey)?.let { itemsByPage[page] = loadItemsForPage(it, pageKey) }
        }
        return itemsByPage
    }

    private fun loadItemsForPage(categorySection: ConfigurationSection, categoryName: String): MutableMap<Int, RecipeData> {
        val categoryItems = LinkedHashMap<Int, RecipeData>()
        for (slotKey in categorySection.getKeys(false)) {
            val slot = slotKey.toIntOrNull()
            if (slot == null) {
                addError(categoryName, slotKey, "スロット番号が数字ではありません．")
                continue
            }
            val itemSection = categorySection.getConfigurationSection(slotKey) ?: continue
            parseRecipeData(itemSection, categoryName, slotKey)?.let { categoryItems[slot] = it }
        }
        return categoryItems
    }

    fun parseRecipeData(itemSection: ConfigurationSection, categoryName: String, slotKey: String): RecipeData? {
        val id = itemSection.getString("id")
        if (id.isNullOrEmpty()) {
            addError(categoryName, slotKey, "idが設定されていません．")
            return null
        }

        try {
            val enabled = itemSection.getBoolean("enabled", true)
            var craftable = itemSection.getBoolean("craftable", true)

            var guiIcon: ItemStack? = null
            val firstItemMap = itemSection.getList("resultItems")?.firstOrNull() as? Map<*, *>
            if (firstItemMap != null) {
                val resultMmid = firstItemMap["mmid"] as String?
                val resultMaterialStr = firstItemMap["material"] as String?
                if (!resultMmid.isNullOrEmpty()) {
                    guiIcon = createMythicItem(resultMmid, categoryName, slotKey)
                } else if (!resultMaterialStr.isNullOrEmpty()) {
                    guiIcon = createVanillaItem(resultMaterialStr, categoryName, slotKey)
                }
            }

            if (guiIcon == null) {
                addError(categoryName, slotKey, "'resultItems'が正しく設定されていないため，GUIアイコンを生成できませんでした．")
                return null
            }

            var hasError = guiIcon.type == Material.BARRIER
            val loreKey = itemSection.getString("lore", "commonLore") ?: "commonLore"

            guiIcon.itemMeta?.let { meta ->
                if (itemSection.contains("displayName")) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemSection.getString("displayName", "") ?: ""))
                }
                guiIcon.itemMeta = meta
            }

            val errorCountBefore = _errorDetails.size
            val resultItems = parseMaterialsList(itemSection.getList("resultItems"), "resultItems", categoryName, slotKey)
            val requiredBranches = parseRequiredBranches(itemSection.getList("requiredItems"), categoryName, slotKey)
            if (_errorDetails.size > errorCountBefore) {
                hasError = true
            }

            if (hasError && !itemSection.isSet("craftable")) {
                craftable = false
                plugin.logger.info("[レシピ: $categoryName.$slotKey.$id] 不明なアイテムが含まれているため，'craftable'を自動的に'false'に設定しました: ")
            }

            return RecipeData(id, enabled, craftable, guiIcon, loreKey, resultItems, requiredBranches)
        } catch (e: Exception) {
            addError(categoryName, slotKey, "予期せエラーが発生しました: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    private fun createMythicItem(mmid: String, pageKey: String, slotKey: String): ItemStack =
        mythicItemUtil.getItemStackFromMMID(mmid) ?: run {
            addError(pageKey, slotKey, "指定されたMythicMobsアイテムが見つかりません: $mmid")
            createErrorItem("不明なMMID: $mmid")
        }

    private fun createVanillaItem(materialStr: String, pageKey: String, slotKey: String): ItemStack = try {
        ItemStack(Material.valueOf(materialStr.uppercase()))
    } catch (e: IllegalArgumentException) {
        addError(pageKey, slotKey, "無効なアイテムIDです: $materialStr")
        createErrorItem("無効なアイテムID: $materialStr")
    }

    private fun createErrorItem(message: String): ItemStack {
        val item = ItemStack(Material.BARRIER)
        item.itemMeta?.let { meta ->
            meta.setDisplayName("${ChatColor.RED}$message")
            item.itemMeta = meta
        }
        return item
    }

    private fun parseRequiredBranches(rawList: List<*>?, pageKey: String, slotKey: String): List<RecipeBranch> {
        val branches = ArrayList<RecipeBranch>()
        if (rawList.isNullOrEmpty()) return branches

        fun isBranchMap(obj: Any?) = obj is Map<*, *> && obj.containsKey("branch")
        val hasBranches = rawList.any { isBranchMap(it) || it is List<*> }

        if (!hasBranches) {
            val materials = parseMaterialsList(rawList, "requiredItems", pageKey, slotKey)
            if (materials.isNotEmpty()) branches.add(RecipeBranch(materials))
            return branches
        }

        rawList.forEachIndexed { i, obj ->
            val branchList: List<*>? = when {
                isBranchMap(obj) -> (obj as Map<*, *>)["branch"] as List<*>?
                obj is List<*> -> obj
                else -> {
                    addError(pageKey, slotKey, "requiredItems[$i] は分岐として正しく設定されていません")
                    return@forEachIndexed
                }
            }
            val materials = parseMaterialsList(branchList, "requiredItems[$i].branch", pageKey, slotKey)
            if (materials.isNotEmpty()) branches.add(RecipeBranch(materials))
        }
        return branches
    }

    private fun parseMaterialsList(rawList: List<*>?, listName: String, pageKey: String, slotKey: String): List<CraftingMaterial> {
        val materials = ArrayList<CraftingMaterial>()
        if (rawList.isNullOrEmpty()) return materials

        rawList.forEachIndexed { i, obj ->
            if (obj !is Map<*, *>) {
                addError(pageKey, slotKey, "$listName[$i] の形式が不正です")
                return@forEachIndexed
            }
            try {
                materials.add(parseSingleMaterial(obj))
            } catch (e: IllegalArgumentException) {
                addError(pageKey, slotKey, "$listName[$i] の解析中にエラーが発生しました: ${e.message}")
            } catch (e: ClassCastException) {
                addError(pageKey, slotKey, "$listName[$i] の解析中にエラーが発生しました: ${e.message}")
            }
        }
        return materials
    }

    private fun parseSingleMaterial(map: Map<*, *>): CraftingMaterial {
        val mmid = map["mmid"] as String?
        val materialStr = map["material"] as String?
        var material: Material? = null
        var isMythic = false

        var displayName = map["displayName"] as String?
        val itemStackData = getItemStackData(map)

        when {
            !mmid.isNullOrBlank() -> {
                isMythic = true
                val fetchedDisplayName = mythicItemUtil.getDisplayNameFromMMID(mmid)
                if (fetchedDisplayName != mmid) {
                    displayName = fetchedDisplayName
                }
            }
            !materialStr.isNullOrBlank() -> {
                material = try {
                    Material.valueOf(materialStr.trim().uppercase())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("アイテムID'$materialStr'は不明なIDです．")
                }
            }
            !itemStackData.isNullOrEmpty() -> {
                val itemStack = platformAdapter.deserializeItemStack(itemStackData)
                if (itemStack == null || ItemUtil.isAir(itemStack)) {
                    throw IllegalArgumentException("'itemStack' からアイテムを復元できませんでした")
                }
                material = itemStack.type
                val itemMeta = itemStack.itemMeta
                if (displayName == null && itemMeta != null && itemMeta.hasDisplayName()) {
                    displayName = itemMeta.displayName
                }
            }
            else -> isMythic = true
        }

        val amount = (map["amount"] ?: 1) as Int
        if (amount <= 0) {
            throw IllegalArgumentException("'amount'は1以上の整数で指定してください．")
        }

        @Suppress("UNCHECKED_CAST")
        val lore = map["lore"] as List<String>?

        return CraftingMaterial(isMythic, mmid, material, amount, displayName, lore, itemStackData)
    }

    private fun getItemStackData(map: Map<*, *>): Map<String, Any?>? {
        val raw = map["itemStack"] as? Map<*, *> ?: return null
        return raw.entries.associateTo(LinkedHashMap()) { (k, v) -> k.toString() to v }
    }

    fun loadLores(config: FileConfiguration): Map<String, List<String>> {
        val lores = HashMap<String, List<String>>()
        val loresSection = config.getConfigurationSection("Lores")
        if (loresSection == null) {
            plugin.logger.warning("config.ymlにLoresセクションがありません．")
            return lores
        }
        for (loreKey in loresSection.getKeys(false)) {
            lores[loreKey] = loresSection.getStringList(loreKey).map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        return lores
    }

    private fun addError(pageKey: String, slotKey: String, message: String) {
        val errorMessage = "[ページ: $pageKey, スロット: $slotKey] $message"
        _errorDetails.add(errorMessage)
        plugin.logger.warning(errorMessage)
    }
}
