package net.azisaba.craftgui.manager;

import net.azisaba.craftgui.CraftGUI;
import net.azisaba.craftgui.data.RecipeData;
import net.azisaba.craftgui.data.RecipeInfo;
import net.azisaba.craftgui.gui.EditGuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;

public class EditGuiManager implements Listener {

    private final CraftGUI plugin;
    private final RecipeConfigManager recipeConfigManager;
    private final RegisterGuiManager registerGuiManager;

    private final Map<UUID, Integer> openEditGuis = new HashMap<>();
    private final Map<UUID, List<RecipeInfo>> playerEditList = new HashMap<>();
    private final Set<UUID> isNavigating = new HashSet<>();

    public EditGuiManager(CraftGUI plugin, RecipeConfigManager recipeConfigManager, RegisterGuiManager registerGuiManager) {
        this.plugin = plugin;
        this.recipeConfigManager = recipeConfigManager;
        this.registerGuiManager = registerGuiManager;
    }

    public void openEditGui(Player player, int page) {
        List<RecipeInfo> allRecipes = loadAllRecipeInfo();
        if (allRecipes.isEmpty()) {
            plugin.sendMessage(player, Component.text("編集可能なレシピがrecipes.ymlに見つかりません。").color(NamedTextColor.RED));
            return;
        }

        playerEditList.put(player.getUniqueId(), allRecipes);
        openEditGuiInternal(player, page, allRecipes);
    }

    private void openEditGuiInternal(Player player, int page, List<RecipeInfo> allRecipes) {
        String title = "CraftGUI Edit - Page " + page;
        EditGuiHolder holder = new EditGuiHolder(page);
        Inventory gui = Bukkit.createInventory(holder, 54, Component.text(title));
        holder.setInventory(gui);

        int itemsPerPage = 45;
        int startIndex = (page - 1) * itemsPerPage;
        int maxPage = (int) Math.ceil((double) allRecipes.size() / itemsPerPage);
        if (maxPage == 0) {
            maxPage = 1;
        }

        if (startIndex >= allRecipes.size() && page > 1) {
            openEditGuiInternal(player, maxPage, allRecipes);
            return;
        }

        for (int i = 0; i < itemsPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex >= allRecipes.size()) {
                break;
            }

            RecipeInfo info = allRecipes.get(recipeIndex);
            RecipeData recipeData = plugin.getRecipeById(info.id());

            ItemStack item;
            if (recipeData != null && recipeData.getGuiIcon() != null) {
                item = new ItemStack(recipeData.getGuiIcon());
            } else {
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("[ロードエラー or 無効]").color(NamedTextColor.RED));
                    item.setItemMeta(meta);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(item.getType());
            }

            if (meta != null) {
                String plainName = info.id();
                if (meta.hasDisplayName()) {
                    plainName = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(meta.displayName()));
                }
                meta.displayName(Component.text("[編集] ").color(NamedTextColor.YELLOW).append(Component.text(plainName).color(NamedTextColor.WHITE)));
                List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("ID: " + info.id()).color(NamedTextColor.GRAY));
                lore.add(Component.text("場所: page" + info.page() + "." + info.slot()).color(NamedTextColor.GRAY));
                if (!info.isEnabled()) {
                    lore.add(Component.text("enabled: false").color(NamedTextColor.RED));
                }
                if (!info.isCraftable()) {
                    lore.add(Component.text("craftable: false").color(NamedTextColor.RED));
                }
                lore.add(Component.text("クリックしてこのレシピを編集").color(NamedTextColor.GREEN));
                meta.lore(lore);
                item.setItemMeta(meta);
            }

            gui.setItem(i, item);
        }

        if (page > 1) {
            gui.setItem(45, createNavItem(Material.ARROW, Component.text("前のページへ").color(NamedTextColor.YELLOW)));
        }
        if (page < maxPage) {
            gui.setItem(53, createNavItem(Material.ARROW, Component.text("次のページへ").color(NamedTextColor.GREEN)));
        }
        gui.setItem(49, createNavItem(Material.BARRIER, Component.text("閉じる").color(NamedTextColor.RED)));

        isNavigating.add(player.getUniqueId());
        player.openInventory(gui);
        openEditGuis.put(player.getUniqueId(), page);
        playerEditList.put(player.getUniqueId(), allRecipes);
        Bukkit.getScheduler().runTask(plugin, () -> isNavigating.remove(player.getUniqueId()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof EditGuiHolder)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        if (isNavigating.contains(uuid) || !openEditGuis.containsKey(uuid)) {
            return;
        }

        int slot = event.getRawSlot();
        int currentPage = openEditGuis.get(uuid);
        List<RecipeInfo> allRecipes = playerEditList.get(uuid);
        if (allRecipes == null) {
            player.closeInventory();
            plugin.sendMessage(player, Component.text("セッションが切れました。もう一度/craftgui editを実行してください。").color(NamedTextColor.RED));
            return;
        }

        if (slot == 45 && currentPage > 1) {
            openEditGuiInternal(player, currentPage - 1, allRecipes);
            return;
        }
        if (slot == 53) {
            int maxPage = (int) Math.ceil((double) allRecipes.size() / 45.0);
            if (currentPage < maxPage) {
                openEditGuiInternal(player, currentPage + 1, allRecipes);
            }
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < 45) {
            int recipeIndex = (currentPage - 1) * 45 + slot;
            if (recipeIndex >= 0 && recipeIndex < allRecipes.size()) {
                RecipeInfo info = allRecipes.get(recipeIndex);
                RecipeData recipeData = plugin.getRecipeById(info.id());

                if (recipeData == null) {
                    plugin.sendMessage(player, Component.text("エラー: このレシピは現在メモリにロードされていません。").color(NamedTextColor.RED));
                    plugin.sendMessage(player, Component.text("編集するには、一度正常な状態に戻してから再度試してください。").color(NamedTextColor.GRAY));
                    return;
                }

                player.closeInventory();
                registerGuiManager.createAndOpenGuiFromEdit(player, info.page(), info.slot(), info.id(), recipeData, currentPage);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditGuiHolder)) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        if (isNavigating.contains(uuid)) {
            return;
        }
        openEditGuis.remove(uuid);
        playerEditList.remove(uuid);
    }

    private List<RecipeInfo> loadAllRecipeInfo() {
        List<RecipeInfo> list = new ArrayList<>();
        FileConfiguration config = recipeConfigManager.getConfig();
        ConfigurationSection itemsSection = config.getConfigurationSection("Items");
        if (itemsSection == null) {
            return list;
        }

        for (String pageKey : itemsSection.getKeys(false)) {
            int page = safeParseInt(pageKey.substring(4));
            if (page <= 0) {
                continue;
            }
            ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
            if (pageSection == null) {
                continue;
            }

            for (String slotKey : pageSection.getKeys(false)) {
                int slot = safeParseInt(slotKey);
                if (slot < 0) {
                    continue;
                }

                String id = pageSection.getString(slotKey + ".id");
                if (id != null && !id.isEmpty()) {
                    boolean enabled = pageSection.getBoolean(slotKey + ".enabled", true);
                    boolean craftable = pageSection.getBoolean(slotKey + ".craftable", true);
                    list.add(new RecipeInfo(id, page, slot, enabled, craftable));
                }
            }
        }
        list.sort(Comparator.comparing(RecipeInfo::id));
        return list;
    }

    private int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ItemStack createNavItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}