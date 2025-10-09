package net.azisaba.craftgui.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.util.MapUtil;

import java.io.*;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataManager {

    private final CraftGUI plugin;
    private final File dataFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PlayerDataManager(CraftGUI plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void loadPlayerData(UUID uuid, MapUtil mapUtil) {
        File playerFile = new File(dataFolder, uuid.toString() + ".json");
        PlayerData data;
        if (!playerFile.exists()) {
            data = new PlayerData();
        } else {
            try (Reader reader = new FileReader(playerFile)) {
                data = gson.fromJson(reader, PlayerData.class);
                if (data == null) data = new PlayerData();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
                data = new PlayerData();
            }
        }
        mapUtil.setSoundToggleState(uuid, data.isSoundOn());
        mapUtil.setVanillaToStash(uuid, data.isVanillaItemsToStash());
        mapUtil.setShowResultItems(uuid, data.isShowResultItems());
    }

    public void savePlayerData(UUID uuid, MapUtil mapUtil) {
        PlayerData data = new PlayerData();
        data.setVanillaItemsToStash(mapUtil.isVanillaToStash(uuid));
        data.setSoundOn(mapUtil.isSoundToggleOn(uuid));
        data.setShowResultItems(mapUtil.isShowResultItems(uuid));

        File playerFile = new File(dataFolder, uuid.toString() + ".json");
        try (Writer writer = new FileWriter(playerFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "プレイヤーデータの保存に失敗しました：" + uuid, e);
        }
    }
}