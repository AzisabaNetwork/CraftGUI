package net.azisaba.craftgui.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ItemNameUtil {

    public String getName(Material material, Player player) {
        if (material == null || material.isAir()) {
            return "Air";
        }
        Component translatable = Component.translatable(material.translationKey());
        Component rendered = TranslatableComponentRenderer.usingTranslationSource(GlobalTranslator.translator())
                .render(translatable, player.locale());
        String result = PlainTextComponentSerializer.plainText().serialize(rendered);
        return (result.isEmpty() || result.equals(material.translationKey()))
                ? getDefaultName(material)
                : result;
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