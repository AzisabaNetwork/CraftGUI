package net.azisaba.craftgui.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.azisaba.craftgui.CraftGUI;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class AssetDownloadUtil {

    private final CraftGUI plugin;
    private final Map<String, Map<String, String>> translations = new HashMap<>();
    private final List<String> successfulLoads = new ArrayList<>();
    private final List<String> failedLoads = new ArrayList<>();
    private final File cacheDirectory;

    public AssetDownloadUtil(CraftGUI plugin) {
        this.plugin = plugin;
        this.cacheDirectory = new File(plugin.getDataFolder(), "lang_cache");
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdir();
        }
    }

    public void downloadAndLoadLanguages(List<String> languages) {
        translations.clear();
        successfulLoads.clear();
        failedLoads.clear();

        if (languages == null || languages.isEmpty()) {
            plugin.getLogger().warning("ダウンロードする言語がconfig.ymlで指定されていません．");
            return;
        }

        String baseUrl = plugin.getConfig().getString("jsonUrl");
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        for (String langCode : languages) {
            File cacheFile = new File(cacheDirectory, langCode + ".json");
            try {
                Map<String, String> langMap;
                if (cacheFile.exists()) {
                    try (Reader reader = new FileReader(cacheFile)) {
                        langMap = gson.fromJson(reader, mapType);
                        plugin.getLogger().info(langCode + ".jsonを読み込みました．");
                    }
                } else {
                    URL url = new URL(baseUrl + langCode + ".json");
                    try (InputStream in = url.openStream()) {
                        Files.copy(in, cacheFile.toPath());
                    }
                    try (Reader reader = new FileReader(cacheFile)) {
                        langMap = gson.fromJson(reader, mapType);
                        plugin.getLogger().info(langCode + ".json をダウンロードしました．");
                    }
                }

                translations.put(langCode.toLowerCase(), langMap);
                successfulLoads.add(langCode + ".json");

            } catch (Exception e) {
                failedLoads.add(langCode + ".json " + e.getClass().getSimpleName());
                plugin.getLogger().log(Level.WARNING, langCode + ".jsonの処理に失敗しました: ", e);
            }
        }
    }

    public Map<String, String> getTranslations(String langCode) { return translations.get(langCode.toLowerCase()); }
    public List<String> getSuccessfulLoads() { return successfulLoads; }
    public List<String> getFailedLoads() { return failedLoads; }
}
