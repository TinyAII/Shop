package nl.tinyaii.shop.shop;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.util.Messages;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 世界商店交易：买家扣款→卖家到账（离线也进 Economy 数据）→发货/收货，锁内原子。
 */
public class WorldTradeService {

    private final ShopPlugin plugin;

    public WorldTradeService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 从挂售单买 n 个；n 自动截断到库存 */
    public boolean buy(Player buyer, Listing listing, int want) {
        Messages msg = plugin.getMessages();
        var eco = plugin.getEcoBridge();
        if (!eco.isAvailable()) { msg.send(buyer, "eco-missing"); return false; }

        synchronized (listing) {
            int n = Math.min(want, listing.getStock());
            if (n <= 0) { buyer.sendMessage(Messages.color("&c该商品已售罄。")); return false; }
            // 不能买自己的（无意义还绕手续费）
            if (listing.getOwner().equals(buyer.getUniqueId())) {
                buyer.sendMessage(Messages.color("&c不能购买自己上架的商品。"));
                return false;
            }
            double cost = Math.round(listing.getUnitPrice() * n * 100.0) / 100.0;
            String curName;
            boolean pointsMode = listing.isPoints() && eco.isPointsAvailable();
            curName = pointsMode ? eco.getPointsName() : msg.currencyName();
            if (pointsMode) {
                int needPts = (int) Math.ceil(cost);
                if (eco.getPointsBalance(buyer.getUniqueId()) < needPts) {
                    msg.send(buyer, "insufficient-money", "{cost}", String.valueOf(needPts),
                            "{currency}", curName);
                    return false;
                }
                eco.withdrawPoints(buyer.getUniqueId(), needPts);
                eco.depositPoints(listing.getOwner(), needPts);
            } else {
                if (!eco.has(buyer.getUniqueId(), cost)) {
                    msg.send(buyer, "insufficient-money", "{cost}", Messages.fmt(cost),
                            "{currency}", msg.currencyName());
                    return false;
                }
                if (!eco.withdraw(buyer.getUniqueId(), cost)) return false;
                eco.deposit(listing.getOwner(), cost);
            }
            // 减库存发货；买完最后一件自动下架（不留占位僵尸单）
            listing.setStock(listing.getStock() - n);
            int given = giveItems(buyer, listing, n);
            if (listing.getStock() <= 0) {
                plugin.getWorldShopManager().delist(listing.getId(), listing.getOwner(), true);
                buyer.sendMessage(Messages.color("&7该商品已全部售出，挂售单自动关闭。"));
            } else {
                plugin.getWorldShopManager().save();
            }

            String sellerName = listing.getOwnerName().isEmpty() ? "玩家" : listing.getOwnerName();
            msg.send(buyer, "buy-success", "{n}", String.valueOf(given),
                    "{item}", nl.tinyaii.shop.util.MaterialNames.coloredName(listing.getTemplate()),
                    "{cost}", Messages.fmt(cost), "{currency}", curName);
            buyer.sendMessage(Messages.color("&7卖家: &f" + sellerName + " &8(货款已转入其钱包)"));
            // 通知在线卖家
            Player seller = org.bukkit.Bukkit.getPlayer(listing.getOwner());
            if (seller != null && !seller.equals(buyer)) {
                seller.sendMessage(Messages.color("&7[&e世界商店&7] &f" + buyer.getName()
                        + " &a购买了你的商品 &e" + n + "x&7，货款 &e" + Messages.fmt(cost) + " " + curName + " &7已到账。"));
                seller.playSound(seller.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
            }
            if (plugin.getConfig().getBoolean("settings.sound", true)) {
                buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
            return given == n;
        }
    }

    /** 玩家上架：从背包收货 */
    public int takeFromInventory(Player p, org.bukkit.inventory.ItemStack template, int amount) {
        org.bukkit.Material mat = template.getType();
        boolean plain = isPlain(template);
        int owned = 0;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == mat && matchesTemplate(it, template, plain)) owned += it.getAmount();
        }
        int toTake = Math.min(owned, amount);
        int remaining = toTake;
        for (int slot = 0; slot < p.getInventory().getSize() && remaining > 0; slot++) {
            org.bukkit.inventory.ItemStack it = p.getInventory().getItem(slot);
            if (it == null || it.getType() != mat || !matchesTemplate(it, template, plain)) continue;
            int n = Math.min(it.getAmount(), remaining);
            remaining -= n;
            if (n >= it.getAmount()) p.getInventory().setItem(slot, null);
            else it.setAmount(it.getAmount() - n);
        }
        p.updateInventory();
        return toTake;
    }

    private int giveItems(Player p, Listing listing, int amount) {
        boolean dropMode = plugin.getConfig().getString("settings.full-inventory", "drop")
                .equalsIgnoreCase("drop");
        int perStack = listing.getTemplate().getMaxStackSize();
        int remaining = amount, given = 0;
        while (remaining > 0) {
            int n = Math.min(perStack, remaining);
            ItemStack stack = listing.take(n);
            var overflow = p.getInventory().addItem(stack);
            int added = n - overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += added;
            remaining -= n;
            if (!overflow.isEmpty() && dropMode) {
                for (ItemStack rest : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    given += rest.getAmount();
                }
                break;
            }
            if (!overflow.isEmpty()) break;
        }
        p.updateInventory();
        return given;
    }

    private boolean isPlain(org.bukkit.inventory.ItemStack t) {
        var meta = t.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasEnchants()
                && (meta.getLore() == null || meta.getLore().isEmpty()));
    }

    /** 普通物品只比材质；带附魔/名字等 NBT 的必须完整一致（isSimilar） */
    private boolean matchesTemplate(org.bukkit.inventory.ItemStack item,
                                    org.bukkit.inventory.ItemStack template, boolean plain) {
        return plain || item.isSimilar(template);
    }

    private String pretty(String material) {
        StringBuilder sb = new StringBuilder();
        for (String s : material.toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }
}
