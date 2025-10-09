package net.azisaba.craftgui.data;

public class PlayerData {

    private boolean soundOn = true;
    private boolean vanillaItemsToStash = true;

    public boolean isSoundOn() {
        return soundOn;
    }

    public void setSoundOn(boolean soundOn) {
        this.soundOn = soundOn;
    }

    public boolean isVanillaItemsToStash() {
        return vanillaItemsToStash;
    }

    public void setVanillaItemsToStash(boolean vanillaItemsToStash) {
        this.vanillaItemsToStash = vanillaItemsToStash;
    }
}
