package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;

public class GuiUtil {

    private final InventoryUtil inventoryUtil;
    private final MythicItemUtil mythicItemUtil;
    private final Map<String, List<String>> loadedLores;
    private final MapUtil mapUtil;

    public GuiUtil(InventoryUtil inventoryUtil, MythicItemUtil mythicItemUtil, Map<String, List<String>> loadedLores, MapUtil mapUtil) {
        this.inventoryUtil = inventoryUtil;
        this.mythicItemUtil = mythicItemUtil;
        this.loadedLores = loadedLores;
        this.mapUtil = mapUtil;
    }

    public ItemStack createBaseRecipeItem(RecipeData recipeData) {
        ItemStack item = new ItemStack(recipeData.getGuiIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(null);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void updateLoreForPlayer(ItemStack itemStack, RecipeData recipeData, Player player) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;

        List<Component> finalLore = new ArrayList<>();
        boolean loreOn = mapUtil.isLoreToggledOn(player.getUniqueId());

        if (loreOn) {
            ItemStack baseIcon = recipeData.getGuiIcon();
            if (baseIcon.hasItemMeta() && baseIcon.getItemMeta().hasLore()) {
                finalLore.addAll(Objects.requireNonNull(baseIcon.getItemMeta().lore()));
                finalLore.add(Component.empty());
            }
        }

        String loreKey = recipeData.getLoreKey();
        for (String line : loadedLores.getOrDefault(loreKey, Collections.emptyList())) {
            finalLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }
        finalLore.add(Component.empty());

        long limitByMaterial = inventoryUtil.calculateMaxCraftableAmount(player, recipeData.getRequiredItems(), Collections.emptyList());
        long limitFinal = inventoryUtil.calculateMaxCraftableAmount(player, recipeData.getRequiredItems(), recipeData.getResultItems());

        if (limitFinal > 0) {
            finalLore.add(Component.text("✓ 変換可能です").color(NamedTextColor.GREEN));
            finalLore.add(Component.text("変換可能回数: ").color(NamedTextColor.GRAY).append(Component.text(limitFinal + "回").color(NamedTextColor.AQUA)));
        } else {
            finalLore.add(Component.text("✘ 変換できません").color(NamedTextColor.RED));
            if (limitByMaterial > 0 && limitFinal == 0) {
                finalLore.add(Component.text("✘ インベントリに空きがありません").color(NamedTextColor.YELLOW));
            } else if (limitByMaterial == 0) {
                finalLore.add(Component.text("✘ 変換に必要なアイテムが不足しています").color(NamedTextColor.RED));
            }
        }

        finalLore.add(Component.empty());
        finalLore.add(Component.text("変換に必要なアイテム: ").color(NamedTextColor.GRAY));

        for (CraftingMaterial required : recipeData.getRequiredItems()) {
            long ownedAmount = inventoryUtil.countItems(player, required);
            String checkMarkStr = ownedAmount >= required.amount() ? "✓ " : "✘ ";
            NamedTextColor checkMarkColor = ownedAmount >= required.amount() ? NamedTextColor.GREEN : NamedTextColor.RED;
            Component displayName = LegacyComponentSerializer.legacyAmpersand().deserialize(mythicItemUtil.resolveDisplayName(required, player)).color(NamedTextColor.WHITE);
            Component countMessage = createCountMessage(ownedAmount, required.amount());

            finalLore.add(Component.text(checkMarkStr).color(checkMarkColor).append(displayName).append(Component.text(" x" + required.amount()).color(NamedTextColor.GRAY)).append(countMessage));

            if (loreOn && required.isMythicItem()) {
                List<String> requiredItemLore = mythicItemUtil.getLoreFromMMID(required.mmid());
                if (requiredItemLore != null && !requiredItemLore.isEmpty()) {
                    for (String loreLine : requiredItemLore) {
                        finalLore.add(Component.text("  » ").color(NamedTextColor.DARK_GRAY).append(LegacyComponentSerializer.legacyAmpersand().deserialize(loreLine)));
                    }
                }
            }
        }

        if (mapUtil.isShowResultItems(player.getUniqueId())) {
            finalLore.add(Component.empty());
            finalLore.add(Component.text("変換後のアイテム: ").color(NamedTextColor.GRAY));
            for (CraftingMaterial result : recipeData.getResultItems()) {
                Component checkMark = Component.text("✓ ").color(NamedTextColor.GREEN);
                Component displayName = LegacyComponentSerializer.legacyAmpersand().deserialize(mythicItemUtil.resolveDisplayName(result, player)).color(NamedTextColor.WHITE);
                finalLore.add(checkMark.append(displayName).append(Component.text(" x" + result.amount()).color(NamedTextColor.GRAY)));
                if (loreOn && result.isMythicItem()) {
                    List<String> resultItemLore = mythicItemUtil.getLoreFromMMID(result.mmid());
                    if (resultItemLore != null && !resultItemLore.isEmpty()) {
                        for (String loreLine : resultItemLore) {
                            finalLore.add(Component.text("  » ").color(NamedTextColor.DARK_GRAY).append(LegacyComponentSerializer.legacyAmpersand().deserialize(loreLine)));
                        }
                    }
                }
            }
        }

        finalLore.add(Component.empty());
        finalLore.add(Component.text("レシピID: ").color(NamedTextColor.GRAY).append(Component.text(recipeData.getId()).color(NamedTextColor.BLUE)));

        meta.lore(finalLore);
        itemStack.setItemMeta(meta);
    }

    private Component createCountMessage(long ownedAmount, int requiredAmount) {
        if (ownedAmount >= requiredAmount) {
            return Component.text(" (" + ownedAmount + "個所持)").color(NamedTextColor.AQUA);
        } else if (ownedAmount > 0) {
            long needed = requiredAmount - ownedAmount;
            return Component.text(" (" + needed + "個不足)").color(NamedTextColor.YELLOW);
        } else {
            return Component.text(" (所持していません)").color(NamedTextColor.RED);
        }
    }
}