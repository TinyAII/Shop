package nl.tinyaii.shop.gui;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.economy.EcoBridge;
import nl.tinyaii.shop.shop.Product;
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
import java.util.Map;

/**
 * 商品网格页：mode=buy 购买 / sell 收购。45格商品+底部导航。
 * 每个商品 lore 尾部藏商品 id（点击定位用）。
 */
public class ShopMenu {

    private final ShopPlugin plugin;
    private final Player player;
    private final String mode;      // buy / sell
    private final String category;
    private final int page;

    public ShopMenu(ShopPlugin plugin, Player player, String mode, String category, int page) {
        this.plugin = plugin;
        this.player = player;
        this.mode = mode;
        this.category = category;
        this.page = page;
    }

    public void open() {
        List<Product> list = plugin.getShopManager().viewOf(category, mode.equals("buy"));
        int pages = Math.max(1, (list.size() + 44) / 45);
        int p = Math.max(0, Math.min(page, pages - 1));

        boolean isBuy = mode.equals("buy");
        String title = ChatColor.DARK_GRAY + (isBuy ? "购买" : "收购") + ChatColor.WHITE + " - " + category;
        Inventory inv = Bukkit.createInventory(
                new MenuHolder(MenuHolder.Type.SHOP, mode, category, p, pages), 54, title);

        for (int i = 0; i < 45; i++) {
            if (p * 45 + i >= list.size()) break;
            inv.setItem(i, productIcon(list.get(p * 45 + i), isBuy));
        }

        ItemStack filler = MainMenu.named(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (p > 0) inv.setItem(45, MainMenu.named(Material.ARROW, "&e上一页", new ArrayList<>()));
        if (p < pages - 1) inv.setItem(53, MainMenu.named(Material.ARROW, "&e下一页", new ArrayList<>()));
        inv.setItem(49, MainMenu.named(Material.BARRIER, "&c返回", new ArrayList<>()));

        // 底部余额
        EcoBridge eco = plugin.getEcoBridge();
        if (eco.isAvailable()) {
            inv.setItem(46, MainMenu.named(Material.EMERALD,
                    "&a余额: &e" + Messages.fmt(eco.getBalance(player.getUniqueId())) + " " + eco.getCurrencyName(),
                    new ArrayList<>()));
        }

        // 分类切换按钮
        inv.setItem(47, MainMenu.named(Material.COMPASS, "&b切换分类",
                new ArrayList<>(Arrays.asList(Messages.color("&7当前: &f" + category)))));

        // 模式切换按钮
        inv.setItem(51, MainMenu.named(isBuy ? Material.GOLD_INGOT : Material.GOLDEN_PICKAXE,
                isBuy ? "&6切换到 收购区" : "&e切换到 购买区",
                Arrays.asList(isBuy ? "&7把物品卖给系统" : "&7花钱向系统购买")));

        player.openInventory(inv);
    }

    private ItemStack productIcon(Product product, boolean isBuy) {
        ItemStack icon = product.getTemplate();
        if (icon.getAmount() != 1) icon.setAmount(1);   // 展示统一按单个，价格即单价
        ItemMeta meta = icon.getItemMeta();
        double price = isBuy ? product.getBuyPrice() : product.getSellPrice();
        if (meta != null) {
            List<String> old = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            old.add(Messages.color("&8&m                        "));
            old.add(Messages.color(isBuy
                    ? "&7单价: &e" + Messages.fmt(price) + " " + plugin.getMessages().currencyName()
                    : "&7回收价: &e" + Messages.fmt(price) + " " + plugin.getMessages().currencyName()));
            old.add(Messages.color("&8分组: &7" + product.getCategory()));
            if (isBuy) {
                int st = product.getStock();
                if (st == -1) old.add(Messages.color("&7库存: &a无限"));
                else old.add(Messages.color("&7库存: " + (st == 0 ? "&c0（已售罄）" : "&f" + st)));
            }
            if (isBuy && product.getBuyPrice() <= 0) old.add(Messages.color("&c此商品仅限出售给系统"));
            else if (!isBuy && product.getSellPrice() <= 0) old.add(Messages.color("&c此商品仅可购买"));
            else if (price <= 0) old.add(Messages.color("&c该方向不开放"));
            else if (isBuy) {
                if (product.getStock() == 0) old.add(Messages.color("&c已售罄，无法购买"));
                else old.add(Messages.color("&a左键买1 &8| &aShift买一组"));
            }
            else old.add(Messages.color("&a左键卖1 &8| &aShift卖全部"));
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.WHITE + nl.tinyaii.shop.util.MaterialNames.name(product.getTemplate()));
            }
            meta.setLore(old);
            // 隐形 id 标记（用隐藏段塞进最后一行）
            List<String> lore = meta.getLore();
            lore.add(ChatColor.BLACK + "" + ChatColor.DARK_GRAY + "#" + productId(product));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** 通过 ShopManager 反查 id（保序 Map，线性查找量小） */
    private int productId(Product target) {
        for (Map.Entry<Integer, Product> e : plugin.getShopManager().all().entrySet()) {
            if (e.getValue() == target) return e.getKey();
        }
        return -1;
    }

    static String prettyName(Product product) {
        StringBuilder sb = new StringBuilder();
        for (String s : product.getMaterialName().toLowerCase().split("_")) {
            if (s.isEmpty()) continue;
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
