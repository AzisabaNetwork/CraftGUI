package net.azisaba.craftgui.data;

public class PlayerData {

    private boolean soundOn = true;
    private boolean vanillaItemsToStash = true;
    private boolean showResultItems = true;
    private boolean craftableOnly = false;

    public boolean isSoundOn() {
        return soundOn;
    }
    public void setSoundOn(boolean soundOn) {
        this.soundOn = soundOn;
    }
    public boolean isVanillaItemsToStash() {
        return vanillaItemsToStash;
    }
    public boolean isShowResultItems() {
        return showResultItems;
    }
    public void setVanillaItemsToStash(boolean vanillaItemsToStash) {
        this.vanillaItemsToStash = vanillaItemsToStash;
    }
    public void setShowResultItems(boolean showResultItems) {
        this.showResultItems = showResultItems;
    }
    public boolean isCraftableOnly() {
        return craftableOnly;
    }
    public void setCraftableOnly(boolean craftableOnly) {
        this.craftableOnly = craftableOnly;
    }
}
