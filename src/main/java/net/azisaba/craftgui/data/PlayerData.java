package net.azisaba.craftgui.data;

public class PlayerData {

    private boolean soundOn = true;
    private boolean vanillaItemsToStash = true;
    private boolean showResultItems = true;

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
}
