package net.azisaba.craftgui.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class ItemNameUtil {

    private final AssetDownloadUtil assetDownloadUtil;

    public ItemNameUtil(AssetDownloadUtil assetDownloadUtil) {
        this.assetDownloadUtil = assetDownloadUtil;
    }

    public String getName(Material material, Player player) {
        String locale = player.getLocale().toLowerCase();
        Map<String, String> langMap = assetDownloadUtil.getTranslations(locale);

        if (langMap == null) {
            langMap = assetDownloadUtil.getTranslations("en_us");
        }

        String keyPath = material.getKey().getKey();
        String translationKey = (material.isBlock() ? "block.minecraft." : "item.minecraft.") + keyPath;

        String result = (langMap != null) ? langMap.get(translationKey) : null;

        return result != null ? result : getDefaultName(material);
    }

    private String getDefaultName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        String[] words = name.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            words[i] = words[i].substring(0, 1).toUpperCase() + words[i].substring(1);
        }
        return String.join(" ", words);
    }
}
