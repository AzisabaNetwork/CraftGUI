package net.azisaba.craftgui.platform;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public interface PlatformAdapter {

    /**
     * IncompatibleClassChangeError (1.20.4以前のabstract class vs 1.21以降のinterface) を回避して
     * InventoryView から TopInventory を取得します。
     */
    Inventory getTopInventory(InventoryView view);

    /**
     * 1.13 に存在しない CustomModelData を安全に取得します。
     */
    Integer getCustomModelData(ItemMeta meta);

    /**
     * 1.13 に存在しない CustomModelData を安全に設定します。
     */
    void setCustomModelData(ItemMeta meta, Integer customModelData);

    /**
     * アイテムの類似性比較 (NBT / Data Components 互換)
     */
    boolean isSimilar(ItemStack first, ItemStack second);

    /**
     * ItemStack をシリアライズ (Data Components / 旧NBT 互換)
     */
    Map<String, Object> serializeItemStack(ItemStack item);

    /**
     * シリアライズデータから ItemStack を復元
     */
    ItemStack deserializeItemStack(Map<String, Object> data);

    /**
     * レシピGUI用のクリーンフラグ（属性・エンチャント・耐久値などの不要表示非表示化）を適用
     */
    void applyCleanFlags(ItemMeta meta);
}
