package nl.tinyaii.shop.shop;

import nl.tinyaii.shop.ShopPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界商店管理：挂售单持久化（worldshop.yml）+ 交易原子操作。
 */
public class WorldShopManager {
    private final ShopPlugin plugin;
    private final Map<Integer, Listing> listings = new ConcurrentHashMap<>();
    private int nextId = 1;
    private File file;

    public WorldShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (listings) {
            listings.clear();
            nextId = 1;
            file = new File(plugin.getDataFolder(), "worldshop.yml");
            if (!file.exists()) return;
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yml.getConfigurationSection("listings");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                try {
                    ConfigurationSection s = root.getConfigurationSection(key);
                    if (s == null) continue;
                    ItemStack stack = s.getItemStack("item");
                    if (stack == null) continue;
                    if (stack.getAmount() != 1) { stack.setAmount(1); }
                    Listing l = new Listing(Integer.parseInt(key),
                            UUID.fromString(s.getString("owner", "")),
                            s.getString("owner-name", ""),
                            stack,
                            s.getDouble("price", 0),
                            s.getInt("stock", 0));
                    listings.put(l.getId(), l);
                    nextId = Math.max(nextId, l.getId() + 1);
                } catch (Exception e) {
                    plugin.getLogger().warning("解析挂售单 " + key + " 失败: " + e.getMessage());
                }
            }
        }
    }

    public void save() {
        synchronized (listings) {
            YamlConfiguration yml = new YamlConfiguration();
            for (Listing l : listings.values()) {
                String base = "listings." + l.getId() + ".";
                yml.set(base + "owner", l.getOwner().toString());
                yml.set(base + "owner-name", l.getOwnerName());
                yml.set(base + "item", l.getTemplate());
                yml.set(base + "price", l.getUnitPrice());
                yml.set(base + "stock", l.getStock());
            }
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                yml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("保存 worldshop.yml 失败: " + e.getMessage());
            }
        }
    }

    /** 上架：返回挂售单 id */
    public int list(UUID owner, String ownerName, ItemStack item, double unitPrice, int stock) {
        synchronized (listings) {
            Listing l = new Listing(nextId, owner, ownerName, item, unitPrice, stock);
            listings.put(nextId, l);
            int id = nextId;
            nextId++;
            save();
            return id;
        }
    }

    public Listing get(int id) { return listings.get(id); }

    public List<Listing> all() { return new ArrayList<>(listings.values()); }

    public List<Listing> byOwner(UUID uuid) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.getOwner().equals(uuid)) out.add(l);
        return out;
    }

    /** 下架（仅货主或管理员） */
    public boolean delist(int id, UUID actor, boolean isAdmin) {
        Listing l = listings.get(id);
        if (l == null) return false;
        if (!isAdmin && !l.getOwner().equals(actor)) return false;
        synchronized (listings) {
            listings.remove(id);
            save();
        }
        return true;
    }
}
