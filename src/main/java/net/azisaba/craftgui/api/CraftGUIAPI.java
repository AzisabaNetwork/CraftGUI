package net.azisaba.craftgui.api;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface CraftGUIAPI {

    UserPreference getUserPreference(Player player);

    UserPreference getUserPreference(UUID uuid);
}
