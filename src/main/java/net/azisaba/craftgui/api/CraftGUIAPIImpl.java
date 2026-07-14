package net.azisaba.craftgui.api;

import net.azisaba.craftgui.util.MapUtil;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CraftGUIAPIImpl implements CraftGUIAPI {

    private final MapUtil mapUtil;

    public CraftGUIAPIImpl(MapUtil mapUtil) {
        this.mapUtil = mapUtil;
    }

    @Override
    public UserPreference getUserPreference(Player player) {
        return getUserPreference(player.getUniqueId());
    }

    @Override
    public UserPreference getUserPreference(UUID uuid) {
        return new UserPreferenceImpl(uuid, mapUtil);
    }

    private static class UserPreferenceImpl implements UserPreference {

        private final UUID uuid;
        private final MapUtil mapUtil;

        public UserPreferenceImpl(UUID uuid, MapUtil mapUtil) {
            this.uuid = uuid;
            this.mapUtil = mapUtil;
        }

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public boolean isSoundEnabled() {
            return mapUtil.isSoundToggleOn(uuid);
        }

        @Override
        public void setSoundEnabled(boolean enabled) {
            mapUtil.setSoundToggleState(uuid, enabled);
        }

        @Override
        public boolean isShowResultItems() {
            return mapUtil.isShowResultItems(uuid);
        }

        @Override
        public void setShowResultItems(boolean enabled) {
            mapUtil.setShowResultItems(uuid, enabled);
        }

        @Override
        public boolean isCraftableOnly() {
            return mapUtil.isCraftableOnlyEnabled(uuid);
        }

        @Override
        public void setCraftableOnly(boolean enabled) {
            mapUtil.setCraftableOnlyState(uuid, enabled);
        }

        @Override
        public boolean isStashEnabled() {
            return mapUtil.isStashEnabled(uuid);
        }

        @Override
        public void setStashEnabled(boolean enabled) {
            mapUtil.setStashEnabled(uuid, enabled);
        }
    }
}
