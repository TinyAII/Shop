package nl.tinyaii.shop.shop;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.util.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 交易服务：买/卖唯一入口，先验后扣，锁内原子执行。
 */
public class TradeService {

    private final ShopPlugin plugin;

    public TradeService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 购买 amount 个 */
    public boolean buy(Player p, Product product, int amount) {
        Messages msg = plugin.getMessages();
        var eco = plugin.getEcoBridge();
        if (!eco.isAvailable()) { msg.send(p, "eco-missing"); return false; }
        if (amount <= 0 || product.getBuyPrice() <= 0) return false;

        // 官方商店库存：-1=无限；限量商品卖完即止（防经济通胀）
        amount = product.purchasable(amount);
        if (amount <= 0) {
            p.sendMessage(nl.tinyaii.shop.util.Messages.color("&c该商品已售罄，等待管理员补货。"));
            return false;
        }

        double cost = round2(product.getBuyPrice() * amount);
        if (!eco.has(p.getUniqueId(), cost)) {
            msg.send(p, "insufficient-money", "{cost}", Messages.fmt(cost),
                    "{currency}", msg.currencyName());
            return false;
        }

        // 先给货（背包满策略），成功给货量>0 才扣款
        int given = giveItems(p, product, amount);
        if (given <= 0) {
            msg.send(p, "inventory-full-deny");
            return false;
        }
        double actualCost = round2(product.getBuyPrice() * given);
        eco.withdraw(p.getUniqueId(), actualCost);

        // 扣减官方库存（无限=-1 不动）
        if (product.getStock() != -1) {
            plugin.getShopManager().restock(productKey(product), product.getStock() - given);
        }

        msg.send(p, "buy-success", "{n}", String.valueOf(given),
                "{item}", displayName(product), "{cost}", Messages.fmt(actualCost),
                "{currency}", msg.currencyName());
        if (plugin.getConfig().getBoolean("settings.sound", true)) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        }
        return true;
    }

    /** 出售 amount 个（背包里实际持有为准）；返回成交数量 */
    public int sell(Player p, Product product, int amount) {
        Messages msg = plugin.getMessages();
        var eco = plugin.getEcoBridge();
        if (!eco.isAvailable()) { msg.send(p, "eco-missing"); return 0; }
        if (amount <= 0 || product.getSellPrice() <= 0) return 0;

        // 先收物品（按实际持有截断）
        int taken = takeItems(p, product, amount);
        if (taken <= 0) {
            msg.send(p, "not-enough-items", "{item}", displayName(product));
            return 0;
        }

        double gross = product.getSellPrice() * taken;
        double tax = plugin.getConfig().getDouble("settings.sell-tax-rate", 0.0);
        double gain = round2(gross * (1 - Math.max(0, Math.min(1, tax))));
        eco.deposit(p.getUniqueId(), gain);

        msg.send(p, "sell-success", "{n}", String.valueOf(taken),
                "{item}", displayName(product), "{gain}", Messages.fmt(gain),
                "{currency}", msg.currencyName());
        if (plugin.getConfig().getBoolean("settings.sound", true)) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        }
        return taken;
    }

    // ---------- 内部 ----------

    private int productKey(Product target) {
        for (var e : plugin.getShopManager().all().entrySet()) {
            if (e.getValue() == target) return e.getKey();
        }
        return -1;
    }

    private String displayName(Product product) {
        return nl.tinyaii.shop.util.MaterialNames.coloredName(product.getTemplate());
    }

    private String pretty(String material) {
        StringBuilder sb = new StringBuilder();
        for (String s : material.toLowerCase().split("_")) {
            if (s.isEmpty()) continue;
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 给货；返回实际给出的数量（deny 模式放不下=0 或部分） */
    @SuppressWarnings("deprecation")
    private int giveItems(Player p, Product product, int amount) {
        boolean dropMode = plugin.getConfig().getString("settings.full-inventory", "drop")
                .equalsIgnoreCase("drop");
        int perStack = product.getMaxStack();
        int remaining = amount, given = 0;

        while (remaining > 0) {
            int n = Math.min(perStack, remaining);
            ItemStack stack = product.getTemplate();
            stack.setAmount(n);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(stack);
            int added = n - overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += added;
            remaining -= n;
            if (!overflow.isEmpty() && dropMode) {
                for (ItemStack rest : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    given += rest.getAmount();   // 掉落也算给出
                }
                overflow.clear();
            }
            if (!overflow.isEmpty()) break;      // deny 模式放不下就停
        }
        p.updateInventory();
        return given;
    }

    /** 从背包收货；返回实际收取数量 */
    private int takeItems(Player p, Product product, int amount) {
        Material mat = product.getTemplate().getType();
        // 先统计持有
        int owned = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == mat && matches(product, it)) owned += it.getAmount();
        }
        int toTake = Math.min(owned, amount);
        if (toTake <= 0) return 0;

        int remaining = toTake;
        for (int slot = 0; slot < p.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it == null || it.getType() != mat || !matches(product, it)) continue;
            int n = Math.min(it.getAmount(), remaining);
            remaining -= n;
            if (n >= it.getAmount()) p.getInventory().setItem(slot, null);
            else it.setAmount(it.getAmount() - n);
        }
        p.updateInventory();
        return toTake;
    }

    /** NBT 匹配：有自定义 meta 的必须完全一致，纯物品只比材质 */
    private boolean matches(Product product, ItemStack item) {
        boolean templatePlain = product.getTemplate().getItemMeta() == null
                || (!product.getTemplate().getItemMeta().hasDisplayName()
                    && !product.getTemplate().getItemMeta().hasEnchants()
                    && (product.getTemplate().getItemMeta().getLore() == null
                        || product.getTemplate().getItemMeta().getLore().isEmpty()));
        if (templatePlain) return true;
        return item.isSimilar(product.getTemplate());
    }
}
