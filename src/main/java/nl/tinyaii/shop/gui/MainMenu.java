package nl.tinyaii.shop.gui;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.economy.EcoBridge;
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
 * 主菜单：分类选择 + 购买/收购页签说明。
 */
public class MainMenu {

    public static final String TITLE = ChatColor.DARK_GRAY + "商店";
    private final ShopPlugin plugin;
    private final Player player;

    public MainMenu(ShopPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(
                new MenuHolder(MenuHolder.Type.MAIN, "", "", 0, 1), 27, TITLE);

        // 世界商店入口（玩家互市）
        inv.setItem(12, named(Material.EMERALD_BLOCK, "&a世界商店",
                Arrays.asList("&7玩家互相买卖的市场", "&7所有人都能上架商品", "",
                        "&e点击进入")));

        // 左：购买页签（进分类选择）
        inv.setItem(11, named(Material.GOLDEN_PICKAXE, "&e▶ 购买区",
                Arrays.asList("&7花钱向系统购买物品", "&7点商品=买1个 &8| &7Shift=买一组", "",
                        "&e点击选择分类")));
        // 中：分类浏览
        List<String> catLore = new ArrayList<>();
        var sec = plugin.getConfig().getConfigurationSection("settings.categories");
        if (sec == null) {
            catLore.add(Messages.color("&7无分类"));
        } else {
            for (String k : sec.getKeys(false)) catLore.add(Messages.color("&8- " + k));
        }
        catLore.add("");
        catLore.add(Messages.color("&e点击选择分类"));
        inv.setItem(13, named(Material.COMPASS, "&b按分类浏览", catLore));
        // 右：收购页签
        inv.setItem(15, named(Material.GOLD_INGOT, "&6▶ 收购区",
                Arrays.asList("&7把物品卖给系统换钱", "&7点商品=卖1个 &8| &7Shift=卖全部", "",
                        "&e点击选择分类")));

        // 底部余额
        EcoBridge eco = plugin.getEcoBridge();
        String balance = eco.isAvailable()
                ? "&a余额: &e" + Messages.fmt(eco.getBalance(player.getUniqueId())) + " " + eco.getCurrencyName()
                : "&c经济插件未安装，交易不可用";
        inv.setItem(22, named(eco.isAvailable() ? Material.EMERALD : Material.BARRIER,
                balance, new ArrayList<>()));

        player.openInventory(inv);
    }

    static ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.color(name));
            List<String> out = new ArrayList<>();
            for (String l : lore) out.add(Messages.color(l));
            meta.setLore(out);
            it.setItemMeta(meta);
        }
        return it;
    }
}
