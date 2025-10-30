package net.azisaba.craftgui.logging;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.util.InventoryUtil;
import net.azisaba.craftgui.util.MythicItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

public class FileLogger {

    private final CraftGUI plugin;
    private final File logDirectory;
    private final MythicItemUtil mythicItemUtil;
    private final InventoryUtil inventoryUtil;

    public FileLogger(CraftGUI plugin, MythicItemUtil mythicItemUtil, InventoryUtil inventoryUtil) {
        this.plugin = plugin;
        this.mythicItemUtil = mythicItemUtil;
        this.inventoryUtil = inventoryUtil;
        this.logDirectory = new File(plugin.getDataFolder(), "logs");
        setupDirectory();
    }

    private void setupDirectory() {
        if (!logDirectory.exists()) {
            if (logDirectory.mkdirs()) {
                plugin.getLogger().info("ログディレクトリを作成しました: " + logDirectory.getPath());
            } else {
                plugin.getLogger().severe("ログディレクトリの作成に失敗しました．");
            }
        }
    }

    public void saveLog(String message) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(logDirectory, date + ".log");

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            writer.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            plugin.getLogger().severe("ログファイルへの書き込みに失敗しました: " + e.getMessage());
        }
    }

    public void logCraft(Player player, RecipeData recipe, int craftAmount) {
        try {
            String requiredString = recipe.getRequiredItems().stream()
                    .map(m -> {
                        String name = ChatColor.stripColor(mythicItemUtil.resolveDisplayName(m, player));
                        int consumedAmount = m.getAmount() * craftAmount;
                        long remainingAmount = inventoryUtil.countItems(player,     m);
                        return String.format("%s(x%d)[残り: %d]", name, consumedAmount, remainingAmount);
                    })
                    .collect(Collectors.joining(", "));

            String resultString = recipe.getResultItems().stream()
                    .map(m -> {
                        String name = ChatColor.stripColor(mythicItemUtil.resolveDisplayName(m, player));
                        int givenAmount = m.getAmount() * craftAmount;
                        return String.format("%s(x%d)", name, givenAmount);
                    })
                    .collect(Collectors.joining(", "));

            String logMessage = String.format("%sが%sを%sに変換しました", player.getName(), requiredString, resultString);
            saveLog(logMessage);

        } catch (Exception e) {
            plugin.getLogger().warning("クラフトログの保存に失敗しました: " + e.getMessage());
        }
    }
}
