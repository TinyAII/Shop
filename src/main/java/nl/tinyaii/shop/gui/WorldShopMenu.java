package nl.tinyaii.shop.gui;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.shop.Listing;
import nl.tinyaii.shop.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 世界商店页：玩家挂售商品网格（45格分页），lore 显示卖家/单价/库存。
 */
public class WorldShopMenu {

    private final ShopPlugin plugin;
    private final Player player;
    private final int page;

    public WorldShopMenu(ShopPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
    }

    public void open() {
        List<Listing> all = plugin.getWorldShopManager().all();
        List<Listing> list = new ArrayList<>();
        for (Listing l : all) if (l.getStock() > 0) list.add(l);   // 售罄不显示
        int pages = Math.max(1, (list.size() + 44) / 45);
        int p = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(
                new MenuHolder(MenuHolder.Type.WORLD, "world", "", p, pages), 54,
                ChatColor.DARK_GRAY + "世界商店");

        for (int i = 0; i < 45; i++) {
            if (p * 45 + i >= list.size()) break;
            Listing l = list.get(p * 45 + i);
            ItemStack icon = l.getTemplate();

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (!meta.hasDisplayName()) {
                    meta.setDisplayName(ChatColor.GRAY + pretty(l.getMaterial().name()));
                }
                lore.add(Messages.color("&8&m                        "));
                lore.add(Messages.color("&7单价: &e" + Messages.fmt(l.getUnitPrice())
                        + " " + plugin.getMessages().currencyName()));
                lore.add(Messages.color("&7库存: &f" + l.getStock()));
                lore.add(Messages.color("&7卖家: &b" + (l.getOwnerName().isEmpty() ? "?" : l.getOwnerName())));
                boolean mine = l.getOwner().equals(player.getUniqueId());
                lore.add("");
                if (mine) {
                    lore.add(Messages.color("&6你的挂售单 &8| &7下架: /商店 下架 " + l.getId()));
                } else if (l.getStock() > 0) {
                    lore.add(Messages.color("&a左键买1 &8| &aShift买全部"));
                } else {
                    lore.add(Messages.color("&c已售罄"));
                }
                // 隐形单号标记
                lore.add(ChatColor.BLACK.toString() + ChatColor.DARK_GRAY + "@" + l.getId());
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }

        ItemStack filler = MainMenu.named(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (p > 0) inv.setItem(45, MainMenu.named(Material.ARROW, "&e上一页", new ArrayList<>()));
        if (p < pages - 1) inv.setItem(53, MainMenu.named(Material.ARROW, "&e下一页", new ArrayList<>()));
        inv.setItem(49, MainMenu.named(Material.BARRIER, "&c返回主菜单", new ArrayList<>()));
        EcoBridgeBalance(inv);
        player.openInventory(inv);
    }

    private void EcoBridgeBalance(Inventory inv) {
        var eco = plugin.getEcoBridge();
        if (eco.isAvailable()) {
            inv.setItem(47, MainMenu.named(Material.EMERALD,
                    "&a余额: &e" + Messages.fmt(eco.getBalance(player.getUniqueId())) + " " + eco.getCurrencyName(),
                    new ArrayList<>()));
        }
    }

    static String pretty(String material) {
        StringBuilder sb = new StringBuilder();
        for (String s : material.toLowerCase().split("_")) {
            if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
