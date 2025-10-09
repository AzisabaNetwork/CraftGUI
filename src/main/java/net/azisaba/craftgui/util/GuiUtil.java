package net.azisaba.craftgui.util;

import net.azisaba.craftgui.data.CraftingMaterial;
import net.azisaba.craftgui.data.RecipeData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        ItemStack item = recipeData.getGuiIcon().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(null);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void updateLoreForPlayer(ItemStack itemStack, RecipeData recipeData, Player player) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;

        List<String> finalLore = new ArrayList<>();
        boolean loreOn = mapUtil.isLoreToggledOn(player.getUniqueId());

        if (loreOn) {
            ItemStack baseIcon = recipeData.getGuiIcon();
            if (baseIcon.hasItemMeta() && baseIcon.getItemMeta().hasLore()) {
                finalLore.addAll(baseIcon.getItemMeta().getLore());
                finalLore.add("");
            }
        }

        String loreKey = recipeData.getLoreKey();
        finalLore.addAll(loadedLores.getOrDefault(loreKey, Collections.emptyList()));
        finalLore.add("");

        long maxCraftable = inventoryUtil.calculateMaxCraftableAmount(player, recipeData.getRequiredItems());

        if (maxCraftable > 0) {
            finalLore.add(ChatColor.GREEN + "✓ 変換可能です");
        } else {
            finalLore.add(ChatColor.RED + "✘ 変換できません");
        }

        if (maxCraftable > 0) {
            finalLore.add(ChatColor.GRAY + "変換可能回数: " + ChatColor.AQUA + maxCraftable + "回");
        }
        finalLore.add("");
        finalLore.add(ChatColor.GRAY + "変換に必要なアイテム: ");

        for (CraftingMaterial required : recipeData.getRequiredItems()) {
            long ownedAmount = inventoryUtil.countItems(player, required);
            String checkMark = ownedAmount >= required.getAmount() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✘ ";
            String displayName = mythicItemUtil.resolveDisplayName(required, player);
            String countMessage = createCountMessage(ownedAmount, required.getAmount());

            finalLore.add(checkMark + ChatColor.WHITE + displayName + " x" + required.getAmount() + countMessage);

            if (loreOn && required.isMythicItem()) {
                List<String> requiredItemLore = mythicItemUtil.getLoreFromMMID(required.getMmid());
                if (requiredItemLore != null && !requiredItemLore.isEmpty()) {
                    for (String loreLine : requiredItemLore) {
                        finalLore.add(ChatColor.DARK_GRAY + "  » " + ChatColor.RESET + loreLine);
                    }
                }
            }
        }

        meta.setLore(finalLore);
        itemStack.setItemMeta(meta);
    }

    private String createCountMessage(long ownedAmount, int requiredAmount) {
        if (ownedAmount >= requiredAmount) {
            return ChatColor.AQUA + " (" + ownedAmount + "個所持)";
        } else if (ownedAmount > 0) {
            long needed = requiredAmount - ownedAmount;
            return ChatColor.YELLOW + " (" + needed + "個不足)";
        } else {
            return ChatColor.RED + " (所持していません)";
        }
    }
}