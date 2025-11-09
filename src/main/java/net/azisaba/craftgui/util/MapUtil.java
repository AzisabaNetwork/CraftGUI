package net.azisaba.craftgui.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MapUtil {

    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, Boolean> loreToggleState = new HashMap<>();
    private final Map<UUID, Boolean> compactViewToggleState = new HashMap<>();
    private final Map<UUID, Boolean> soundToggleState = new HashMap<>();
    private final Map<UUID, Boolean> showResultItemsState = new HashMap<>();

    public int getPlayerPage(UUID uuid) {
        return playerPages.getOrDefault(uuid, 1);
    }
    public void setPlayerPage(UUID uuid, int page) {
        playerPages.put(uuid, page);
    }
    public boolean isLoreToggledOn(UUID uuid) {
        return loreToggleState.getOrDefault(uuid, true);
    }
    public boolean isCompactViewEnabled(UUID uuid) {
        return compactViewToggleState.getOrDefault(uuid, false);
    }
    public boolean isSoundToggleOn(UUID uuid) {
        return soundToggleState.getOrDefault(uuid, true);
    }
    public boolean isShowResultItems(UUID uuid) {
        return showResultItemsState.getOrDefault(uuid, true);
    }
    public void setSoundToggleState(UUID uuid, boolean isOn) {
        soundToggleState.put(uuid, isOn);
    }
    public void setShowResultItems(UUID uuid, boolean show) {
        showResultItemsState.put(uuid, show);
    }
    public void toggleLoreState(UUID uuid) {
        boolean currentState = isLoreToggledOn(uuid);
        loreToggleState.put(uuid, !currentState);
    }
    public void toggleCompactViewState(UUID uuid) {
        boolean currentState = isCompactViewEnabled(uuid);
        compactViewToggleState.put(uuid, !currentState);
    }
    public void toggleSoundState(UUID uuid) {
        boolean currentState = isSoundToggleOn(uuid);
        soundToggleState.put(uuid, !currentState);
    }
    public void toggleShowResultItems(UUID uuid) {
        showResultItemsState.put(uuid, !isShowResultItems(uuid));
    }
    public void removePlayer(UUID uuid) {
        playerPages.remove(uuid);
        loreToggleState.remove(uuid);
        compactViewToggleState.remove(uuid);
        soundToggleState.remove(uuid);
        showResultItemsState.remove(uuid);
    }
}