package nl.tinyaii.shop.gui;

import nl.tinyaii.shop.ShopPlugin;
import nl.tinyaii.shop.shop.Product;
import nl.tinyaii.shop.shop.TradeService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * GUI 点击处理：主菜单→分类选择→商品页；交易+翻页+模式切换。
 */
public class MenuListener implements Listener {

    private final ShopPlugin plugin;
    private final java.util.Map<java.util.UUID, Long> lastClick = new java.util.HashMap<>();

    public MenuListener(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.SHIFT_LEFT) return;

        // 300ms 防抖
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < 300) return;
        lastClick.put(p.getUniqueId(), now);

        MenuHolder holder = (MenuHolder) e.getInventory().getHolder();
        int slot = e.getRawSlot();

        // ---- 主菜单 ----
        if (holder.getType() == MenuHolder.Type.MAIN) {
            switch (slot) {
                case 11 -> new CategoryMenu(plugin, p, "buy").open();   // 购买区
                case 12 -> new WorldShopMenu(plugin, p, 0).open();      // 世界商店
                case 13 -> new CategoryMenu(plugin, p, "buy").open();   // 分类浏览
                case 15 -> new CategoryMenu(plugin, p, "sell").open();  // 收购区
            }
            return;
        }

        // ---- 世界商店页 ----
        if (holder.getType() == MenuHolder.Type.WORLD) {
            if (slot == 48) {   // 返回统一玩家主菜单 /菜单
            if (plugin.getServer().getPluginManager().getPlugin("Menu") != null) p.performCommand("菜单");
            else p.closeInventory();
            return;
        }
        if (slot == 49) { new MainMenu(plugin, p).open(); return; }
            if (slot == 45 && holder.getPage() > 0) { new WorldShopMenu(plugin, p, holder.getPage() - 1).open(); return; }
            if (slot == 53 && holder.getPage() < holder.getPages() - 1) { new WorldShopMenu(plugin, p, holder.getPage() + 1).open(); return; }
            if (slot < 45) {
                int lid = parseListingId(clicked);
                if (lid <= 0) return;
                var l = plugin.getWorldShopManager().get(lid);
                if (l == null) return;
                int n = e.getClick() == ClickType.SHIFT_LEFT ? Integer.MAX_VALUE : 1;
                new nl.tinyaii.shop.shop.WorldTradeService(plugin).buy(p, l, n);
                Refresh.openLaterWorld(plugin, p);
            }
            return;
        }

        // ---- 分类选择页 ----
        if (holder.getType() == MenuHolder.Type.CATEGORY) {
            String mode = holder.getMode();
            if (slot == e.getInventory().getSize() - 1) { new MainMenu(plugin, p).open(); return; }
            String cat = categoryFromIcon(clicked);
            if (cat != null) new ShopMenu(plugin, p, mode, cat, 0).open();
            return;
        }

        // ---- 商品页 ----
        String mode = holder.getMode();
        String category = holder.getCategory();

        if (slot == 48) {   // 返回统一玩家主菜单 /菜单
            if (plugin.getServer().getPluginManager().getPlugin("Menu") != null) p.performCommand("菜单");
            else p.closeInventory();
            return;
        }
        if (slot == 49) { new MainMenu(plugin, p).open(); return; }
        if (slot == 45 && holder.getPage() > 0) {
            new ShopMenu(plugin, p, mode, category, holder.getPage() - 1).open(); return;
        }
        if (slot == 53 && holder.getPage() < holder.getPages() - 1) {
            new ShopMenu(plugin, p, mode, category, holder.getPage() + 1).open(); return;
        }
        if (slot == 51) {   // 模式切换：保持当前分类
            new ShopMenu(plugin, p, mode.equals("buy") ? "sell" : "buy", category, 0).open(); return;
        }
        if (slot == 47) {   // 分类切换按钮
            new CategoryMenu(plugin, p, mode).open(); return;
        }

        if (slot < 45) {
            int id = parseProductId(clicked);
            if (id <= 0) return;
            Product product = plugin.getShopManager().get(id);
            if (product == null) return;

            TradeService service = new TradeService(plugin);
            boolean shift = e.getClick() == ClickType.SHIFT_LEFT;

            if (mode.equals("buy")) {
                service.buy(p, product, shift ? product.getMaxStack() : 1);
            } else {
                service.sell(p, product, shift ? Integer.MAX_VALUE : 1);   // Shift=卖全部
            }
            Refresh.openLater(plugin, p);   // 刷新余额显示
        }
    }

    /** 从分类图标反查分类名（displayName 即分类名，去色比对） */
    private String categoryFromIcon(ItemStack item) {
        var sec = plugin.getConfig().getConfigurationSection("settings.categories");
        if (sec == null || item.getItemMeta() == null) return null;
        String shown = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        for (String cat : sec.getKeys(false)) {
            if (cat.equals(shown)) return cat;
        }
        return null;
    }

    private int parseListingId(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return -1;
        List<String> lore = item.getItemMeta().getLore();
        for (int i = lore.size() - 1; i >= 0; i--) {
            String s = ChatColor.stripColor(lore.get(i));
            if (s != null && s.startsWith("@")) {
                try { return Integer.parseInt(s.substring(1)); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    private int parseProductId(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return -1;
        List<String> lore = item.getItemMeta().getLore();
        for (int i = lore.size() - 1; i >= 0; i--) {
            String stripped = ChatColor.stripColor(lore.get(i));
            if (stripped != null && stripped.startsWith("#")) {
                try { return Integer.parseInt(stripped.substring(1)); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    static class Refresh {
        static void openLaterWorld(ShopPlugin plugin, Player p) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin,
                    () -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h
                                && h.getType() == MenuHolder.Type.WORLD) {
                            new WorldShopMenu(plugin, p, h.getPage()).open();
                        }
                    }));
        }

        static void openLater(ShopPlugin plugin, Player p) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin,
                    () -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h
                                && h.getType() == MenuHolder.Type.SHOP) {
                            new ShopMenu(plugin, p, h.getMode(), h.getCategory(), h.getPage()).open();
                        }
                    }));
        }
    }
}
