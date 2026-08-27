package nl.tinyaii.shop.command;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.gui.MainMenu;
import nl.tinyaii.shop.gui.ShopMenu;
import nl.tinyaii.shop.shop.Product;
import nl.tinyaii.shop.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopPlugin plugin;

    public ShopCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (args.length == 0 || args[0].equals("购买") || args[0].equals("收购")) {
            if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
            if (!sender.hasPermission("shop.use")) { msg.send((Player) sender, "no-permission"); return true; }
            Player p = (Player) sender;
            if (args.length == 0) { new MainMenu(plugin, p).open(); return true; }
            String mode = args[0].equals("购买") ? "buy" : "sell";
            new ShopMenu(plugin, p, mode, firstCategory(), 0).open();
            return true;
        }

        // ---- 世界商店（所有玩家）----
        switch (args[0]) {
            case "上架": {
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                if (!sender.hasPermission("shop.use")) { msg.send((Player) sender, "no-permission"); return true; }
                Player pl = (Player) sender;
                if (args.length < 3) { pl.sendMessage(Messages.color("&c用法: /商店 上架 <单价> [数量] [金币|点券]（手持物品）")); return true; }
                return doWorldList(pl, args);
            }
            case "世界": {
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                new nl.tinyaii.shop.gui.WorldShopMenu(plugin, (Player) sender, 0).open();
                return true;
            }
            case "我的": {
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                var mine = plugin.getWorldShopManager().byOwner(((Player) sender).getUniqueId());
                sender.sendMessage(Messages.color("&6==== 我的挂售单 ===="));
                for (var l : mine) {
                    sender.sendMessage(Messages.color("&7#" + l.getId() + " &f" + nl.tinyaii.shop.util.MaterialNames.name(l.getTemplate())
                            + " &ex" + l.getStock() + " &7@ &e" + Messages.fmt(l.getUnitPrice())
                            + " &7(下架: /商店 下架 " + l.getId() + ")"));
                }
                if (mine.isEmpty()) sender.sendMessage(Messages.color("&7你还没有挂售任何商品。用 /商店 上架 <单价> [数量] [金币|点券]"));
                return true;
            }
            case "下架": {
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                if (args.length < 2) { sender.sendMessage(Messages.color("&c用法: /商店 下架 <单号>")); return true; }
                return doWorldDelist((Player) sender, parseInt(args[1]));
            }
        }

        // ---- 管理 ----
        if (!args[0].equals("管理") && !args[0].equals("重载")) { sendHelp(sender); return true; }

        if (args[0].equals("重载")) {
            if (!sender.hasPermission("shop.admin")) return deny(sender);
            plugin.reloadAll();
            msg.send(sender instanceof Player ? (Player) sender : null, "reloaded");
            return true;
        }

        if (!sender.hasPermission("shop.admin")) return deny(sender);
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用（需手持物品）。"); return true; }
        Player p = (Player) sender;

        if (args.length >= 2) switch (args[1]) {
            case "补货": {
                if (args.length < 4) { p.sendMessage(Messages.color("&c用法: /商店 管理 补货 <序号> <数量>（-1=无限）")); return true; }
                int rid = parseInt(args[2]);
                int rn = parseInt(args[3]);
                if (plugin.getShopManager().restock(rid, rn)) {
                    p.sendMessage(Messages.color("&a商品 &f#" + rid + " &a库存已设为 &e"
                            + (rn == -1 ? "无限" : String.valueOf(rn))));
                } else p.sendMessage(Messages.color("&c序号不存在。"));
                return true;
            }
            case "上架": {
                if (args.length < 4) { p.sendMessage(Messages.color("&c用法: /商店 管理 上架 <买价|0> <卖价|0> [分类] [库存] [币种] &7(币种=金币/点券，默认金币；0=关闭该方向)")); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { msg.send(p, "hand-empty"); return true; }
                double buy = parsePrice(args[2]), sell = parsePrice(args[3]);
                if (buy < 0 || sell < 0) { p.sendMessage(Messages.color("&c价格无效。（0=关闭该方向）")); return true; }
                if (buy == 0 && sell == 0) { p.sendMessage(Messages.color("&c买卖不能同时关闭。")); return true; }
                String category = args.length >= 5 ? args[4] : "其他";
                int stock = args.length >= 6 ? parseInt(args[5]) : -1;
                String currency = args.length >= 7 ? args[6] : "gold";
                Product added = plugin.getShopManager().add(hand.clone(), category, buy, sell, stock, currency);
                msg.send(p, "listed", "{item}", nl.tinyaii.shop.util.MaterialNames.name(hand),
                        "{buy}", Messages.fmt(buy), "{sell}", Messages.fmt(sell), "{category}", category);
                p.sendMessage(Messages.color("&7库存: &f" + (stock == -1 ? "无限" : String.valueOf(stock))
                        + " &7| 币种: &f" + (currency.equalsIgnoreCase("points") ? "点券" : "金币")
                        + " &8(改库存: /商店 管理 补货 <序号> <数量>)"));
                p.sendMessage(Messages.color("&7改币种: 可改回 /商店 管理 改价 <序号> <买> <卖> [金币|点券>"));
                p.sendMessage(Messages.color("&7商品序号: &f#" + plugin.getShopManager().all()
                        .entrySet().stream().filter(e -> e.getValue() == added)
                        .map(e -> e.getKey()).findFirst().orElse(-1)));
                return true;
            }
            case "下架": {
                p.sendMessage(Messages.color("&e输入序号: /商店 管理 下架 <序号> &7（/商店 管理 列表 查看）"));
                if (args.length >= 3) {
                    int id = parseInt(args[2]);
                    if (plugin.getShopManager().remove(id)) msg.send(p, "unlisted");
                    else p.sendMessage(Messages.color("&c序号不存在。"));
                }
                return true;
            }
            case "改价": {
                if (args.length < 5) { p.sendMessage(Messages.color("&c用法: /商店 管理 改价 <序号> <买价|0> <卖价|0> [金币|点券] &7(0=关闭该方向)")); return true; }
                int id = parseInt(args[2]);
                double buy = parsePrice(args[3]), sell = parsePrice(args[4]);
                if (buy < 0 || sell < 0 || (buy == 0 && sell == 0)) {
                    p.sendMessage(Messages.color("&c价格无效或买卖同时为0。"));
                    return true;
                }
                if (plugin.getShopManager().updatePrice(id, buy, sell)) {
                    if (args.length >= 6) {
                        var prod = plugin.getShopManager().get(id);
                        if (prod != null) { prod.setCurrency(args[5]); plugin.getShopManager().save(); }
                    }
                    msg.send(p, "price-updated");
                }
                else p.sendMessage(Messages.color("&c序号不存在。"));
                return true;
            }
            case "列表": {
                String filter = args.length >= 3 ? args[2] : null;
                p.sendMessage(Messages.color("&6==== 商品列表 ===="));
                for (var e : plugin.getShopManager().all().entrySet()) {
                    Product pr = e.getValue();
                    if (filter != null && !pr.getCategory().equalsIgnoreCase(filter)) continue;
                    p.sendMessage(Messages.color("&7#" + e.getKey() + " &f" + nl.tinyaii.shop.util.MaterialNames.name(pr.getTemplate())
                            + " &7买&e" + Messages.fmt(pr.getBuyPrice())
                            + "&7/卖&e" + Messages.fmt(pr.getSellPrice())
                            + " &7库存&e" + (pr.getStock() == -1 ? "∞" : String.valueOf(pr.getStock()))
                            + " &8[" + pr.getCategory() + "]"));
                }
                return true;
            }
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender s) {
        String[] lines = {
                "&6===== Shop 商店系统 =====",
                "&e/商店 &7- 打开商店主菜单",
                "&e/商店 购买 &7- 直接进购买页",
                "&e/商店 收购 &7- 直接进收购页",
                "&b--- 世界商店（玩家交易）---",
                "&e/商店 世界 &7- 浏览玩家商品",
                "&e/商店 上架 <单价> [数量] [金币|点券] &7- 手持物品挂售",
                "&e/商店 我的 &7- 我的挂售单",
                "&e/商店 下架 <单号> &7- 收回商品",
                "&c--- 管理 ---",
                "&e/商店 管理 上架 <买价> <卖价> [分类] &7- 手持物品上架",
                "&e/商店 管理 下架 <序号>",
                "&e/商店 管理 改价 <序号> <买> <卖>",
                "&e/商店 管理 补货 <序号> <数量(-1=无限)>",
                "&e/商店 管理 列表 [分类]",
                "&e/商店 重载"
        };
        for (String l : lines) s.sendMessage(Messages.color(l));
    }

    private boolean deny(CommandSender s) {
        if (s instanceof Player) plugin.getMessages().send((Player) s, "no-permission");
        else s.sendMessage(plugin.getMessages().raw("no-permission"));
        return true;
    }

    private String firstCategory() {
        var sec = plugin.getConfig().getConfigurationSection("settings.categories");
        if (sec != null) for (String k : sec.getKeys(false)) return k;
        return "其他";
    }

    /** 价格解析：0/-/关/无/-1 均视为"关闭该方向"(返回0)；正数正常；非法返回 -1 */
    private double parsePrice(String s) {
        if (s.equals("0") || s.equals("-") || s.equals("关") || s.equals("无") || s.equals("-1")) return 0;
        return parse(s);
    }

    private double parse(String s) {
        try { return Math.round(Double.parseDouble(s) * 100.0) / 100.0; }
        catch (Exception e) { return -1; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private boolean doWorldList(Player p, String[] args) {
        Messages msg = plugin.getMessages();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { msg.send(p, "world-empty-hand"); return true; }

        double price = parse(args[1]);
        double min = plugin.getConfig().getDouble("worldshop.min-price", 0.1);
        double max = plugin.getConfig().getDouble("worldshop.max-price", 100000);
        if (price <= 0 || price < min || price > max) {
            msg.send(p, "world-price-range", "{min}", String.valueOf(min), "{max}", String.valueOf(max));
            return true;
        }
        int want = args.length >= 3 ? parseInt(args[2]) : hand.getAmount();
        String currency = args.length >= 4 ? args[3] : "gold";

        int cap = plugin.getConfig().getInt("worldshop.max-listings", 9);
        var mine = plugin.getWorldShopManager().byOwner(p.getUniqueId());
        if (mine.size() >= cap) { msg.send(p, "world-limit", "{max}", String.valueOf(cap)); return true; }

        // 从背包收货
        nl.tinyaii.shop.shop.WorldTradeService trade = new nl.tinyaii.shop.shop.WorldTradeService(plugin);
        int taken = trade.takeFromInventory(p, hand.clone(), Math.max(1, Math.min(want, 2304)));
        if (taken <= 0) { p.sendMessage(Messages.color(
        "&c背包里没有可上架的该物品。（提示：附魔/改名物品需与手持的完全同款）")); return true; }

        int id = plugin.getWorldShopManager().list(
                p.getUniqueId(), p.getName(), hand.clone(), price, taken, currency);
        String curName = currency.equalsIgnoreCase("points") ? plugin.getEcoBridge().getPointsName() : msg.currencyName();
        msg.send(p, "world-listed", "{n}", String.valueOf(taken),
                "{item}", hand.getType().name(),
                "{price}", Messages.fmt(price),
                "{currency}", curName + (currency.equalsIgnoreCase("points") ? "(点券)" : ""),
                "{id}", String.valueOf(id));
        return true;
    }

    private boolean doWorldDelist(Player p, int id) {
        Messages msg = plugin.getMessages();
        var l = plugin.getWorldShopManager().get(id);
        if (l == null) { p.sendMessage(Messages.color("&c挂售单不存在。")); return true; }
        boolean admin = p.hasPermission("shop.admin");
        if (!l.getOwner().equals(p.getUniqueId()) && !admin) { msg.send(p, "world-not-yours"); return true; }
        // 退回剩余库存
        if (l.getStock() > 0) {
            ItemStack back = l.take(Math.min(l.getStock(), l.getTemplate().getMaxStackSize()));
            var overflow = p.getInventory().addItem(back);
            int rest = l.getStock() - Math.min(l.getStock(), back.getAmount());
            for (var r : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
            if (rest > 0) {
                ItemStack more = l.take(rest);
                for (var r : p.getInventory().addItem(more).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), r);
            }
        }
        plugin.getWorldShopManager().delist(id, p.getUniqueId(), admin);
        msg.send(p, "world-delist", "{id}", String.valueOf(id));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("购买", "收购", "世界", "上架", "我的", "下架"));
            if (sender.hasPermission("shop.admin")) subs.addAll(Arrays.asList("管理", "重载"));
            for (String s : subs) if (s.startsWith(args[0])) out.add(s);
        } else if (args.length == 2 && args[0].equals("管理")) {
            for (String s : Arrays.asList("上架", "下架", "改价", "补货", "列表")) {
                if (s.startsWith(args[1])) out.add(s);
            }
        } else if (args.length == 3 && args[0].equals("管理")
                && (args[1].equals("下架") || args[1].equals("改价"))) {
            for (Integer id : plugin.getShopManager().all().keySet()) {
                String s = String.valueOf(id);
                if (s.startsWith(args[2])) out.add(s);
            }
        } else if (args.length == 3 && args[0].equals("管理") && args[1].equals("列表")) {
            var sec = plugin.getConfig().getConfigurationSection("settings.categories");
            if (sec != null) for (String k : sec.getKeys(false)) {
                if (k.startsWith(args[2])) out.add(k);
            }
        }
        return out;
    }
}
