package nl.tinyaii.shop.gui;

import nl.tinyaii.shop.ShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 分类选择页：每个分类一个图标，点击进入对应商品页。
 */
public class CategoryMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "选择分类";
    private final ShopPlugin plugin;
    private final Player player;
    private final String mode;   // buy / sell

    public CategoryMenu(ShopPlugin plugin, Player player, String mode) {
        this.plugin = plugin;
        this.player = player;
        this.mode = mode;
    }

    public void open() {
        var sec = plugin.getConfig().getConfigurationSection("settings.categories");
        List<String> cats = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
        int size = Math.max(9, ((cats.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(
                new MenuHolder(MenuHolder.Type.CATEGORY, mode, "", 0, 1), size, TITLE);

        int i = 0;
        for (String cat : cats) {
            String iconStr = sec.getString(cat + ".icon", "CHEST");
            Material mat = Material.matchMaterial(iconStr == null ? "CHEST" : iconStr);
            if (mat == null) mat = Material.CHEST;
            int n = plugin.getShopManager().byCategory(cat).size();
            inv.setItem(i++, MainMenu.named(mat, "&b" + cat,
                    Arrays.asList("&7商品数: &f" + n, "",
                            "&e点击" + (mode.equals("buy") ? "购买" : "出售"))));
        }
        inv.setItem(size - 1, MainMenu.named(Material.BARRIER, "&c返回", new ArrayList<>()));
        player.openInventory(inv);
    }

    /** 由图标名反查分类名 */
    public static String findCategoryByIconName(ShopPlugin plugin, ItemStack item, String strippedName) {
        var sec = plugin.getConfig().getConfigurationSection("settings.categories");
        if (sec == null || item.getItemMeta() == null) return null;
        for (String cat : sec.getKeys(false)) {
            if (ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals(strippedName)) return cat;
        }
        return null;
    }
}
