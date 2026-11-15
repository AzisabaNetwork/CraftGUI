package net.azisaba.craftgui.api;

import java.util.UUID;

public interface UserPreference {

    UUID getUniqueId();

    boolean isSoundEnabled();
    void setSoundEnabled(boolean enabled);

    boolean isShowResultItems();
    void setShowResultItems(boolean enabled);

    boolean isCraftableOnly();
    void setCraftableOnly(boolean enabled);

    boolean isStashEnabled();
    void setStashEnabled(boolean enabled);
}
