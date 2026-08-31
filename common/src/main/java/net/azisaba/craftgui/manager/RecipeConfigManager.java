package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RecipeConfigManager {

    private final CraftGUI plugin;
    private final File recipesFile;
    private FileConfiguration recipesConfig;

    public RecipeConfigManager(CraftGUI plugin) {
        this.plugin = plugin;
        this.recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
    }

    public FileConfiguration getConfig() {
        if (this.recipesConfig == null) {
            reloadConfig();
        }
        return this.recipesConfig;
    }

    public void reloadConfig() {
        if (!recipesFile.exists()) {
            plugin.getLogger().info("recipes.ymlが見つかりませんでした．デフォルトファイルを作成します...");
            saveDefaultConfig();
        }
        this.recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);

        try (InputStream defConfigStream = plugin.getResource("recipes.yml")) {
            if (defConfigStream != null) {
                try (InputStreamReader reader = new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)) {
                    YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                    this.recipesConfig.setDefaults(defConfig);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "recipes.ymlの読み込みに失敗しました: ", e);
        }
    }

    public void saveConfig() {
        if (this.recipesConfig == null || this.recipesFile == null) {
            return;
        }
        try {
            getConfig().save(recipesFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "recipes.ymlの保存に失敗しました: " + recipesFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (!recipesFile.exists()) {
            plugin.saveResource("recipes.yml", false);
        }
    }

    public int getHighestPageNumber() {
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("Items");
        if (itemsSection == null) {
            return 1;
        }
        return itemsSection.getKeys(false).stream()
                .filter(key -> key.toLowerCase().startsWith("page"))
                .map(key -> key.substring(4))
                .mapToInt(this::safeParseInt)
                .max()
                .orElse(1);
    }

    public int findNextFreeSlot(int page) {
        ConfigurationSection pageSection = getConfig().getConfigurationSection("Items.page" + page);
        if (pageSection == null) {
            return 0;
        }
        Set<Integer> usedSlots = pageSection.getKeys(false).stream()
                .mapToInt(this::safeParseInt)
                .filter(slot -> slot >= 0 && slot < 45)
                .boxed()
                .collect(Collectors.toSet());
        for (int i = 0; i < 45; i++) {
            if (!usedSlots.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public String findExistingRecipeById(String recipeId) {
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("Items");
        if (itemsSection == null) {
            return null;
        }
        for (String pageKey : itemsSection.getKeys(false)) {
            ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
            if (pageSection == null) continue;

            for (String slotKey : pageSection.getKeys(false)) {
                String id = pageSection.getString(slotKey + ".id");
                if (recipeId.equalsIgnoreCase(id)) {
                    return pageKey + "." + slotKey;
                }
            }
        }
        return null;
    }

    private int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
