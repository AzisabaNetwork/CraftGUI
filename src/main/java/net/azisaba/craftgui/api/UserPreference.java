package net.azisaba.craftgui.api;

import java.util.UUID;

public interface UserPreference {

    UUID getUniqueId();

    boolean isSoundEnabled();

    boolean isShowResultItems();

    boolean isCraftableOnly();
}
