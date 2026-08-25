package nl.tinyaii.shop.shop;

import nl.tinyaii.shop.ShopPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品管理：LinkedHashMap 保序，变动即落盘 shop.yml。
 */
public class ShopManager {
    private final ShopPlugin plugin;
    /** 保序商品表（序号=列表位置+1，管理命令直观） */
    private final Map<Integer, Product> products = new LinkedHashMap<>();
    private int nextId = 1;
    private File file;

    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (products) {
            products.clear();
            nextId = 1;
            file = new File(plugin.getDataFolder(), "shop.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            List<Map<?, ?>> list = yml.getMapList("products");
            int idx = 0;
            for (Map<?, ?> m : list) {
                idx++;
                try {
                    Object itemObj = m.get("item");
                    if (!(itemObj instanceof Map)) {
                        plugin.getLogger().warning("第 " + idx + " 条商品缺 item 段，跳过");
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    ItemStack stack = parseItemSafely((Map<String, Object>) itemObj, idx);
                    if (stack == null) continue;
                    Object catObj = m.get("category");
                    String category = catObj == null ? "其他" : String.valueOf(catObj);
                    double buy = toDouble(m.get("buy"));
                    double sell = toDouble(m.get("sell"));
                    Product product = new Product(stack, category, buy, sell);
                    Object stObj = m.get("stock");
                    int stock = -1;
                    if (stObj instanceof Number) stock = ((Number) stObj).intValue();
                    else if (stObj != null) { try { stock = Integer.parseInt(stObj.toString()); } catch (Exception ignored) {} }
                    product.setStock(stock);
                    products.put(nextId++, product);
                } catch (Exception e) {
                    plugin.getLogger().warning("解析第 " + idx + " 条商品失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 三格式兼容解析：
     * A) Paper 1.20.5+ 原生格式（含 schema_version/DataVersion/id）→ 直接反序列化（本机生成必兼容）
     * B) 旧完整格式（含 meta 段）→ 反序列化
     * C) 旧简化格式（type/material+amount）→ 手动构建
     * 规避：新版本把无版本号旧格式当 legacy 解析导致扁平化后材质报 Material cannot be null。
     */
    @SuppressWarnings("unchecked")
    private ItemStack parseItemSafely(Map<String, Object> map, int idx) {
        boolean newFormat = map.containsKey("schema_version") || map.containsKey("components");
        boolean hasMeta = map.containsKey("meta");
        if (newFormat || hasMeta) {
            try {
                return ItemStack.deserialize(new LinkedHashMap<>(map));
            } catch (Exception e) {
                plugin.getLogger().warning("第 " + idx + " 条商品反序列化失败: " + e.getMessage());
                return null;
            }
        }
        Object t = map.get("type") != null ? map.get("type") : map.get("material");
        if (t == null) {
            plugin.getLogger().warning("第 " + idx + " 条商品格式无法识别（键: " + map.keySet() + "），跳过");
            return null;
        }
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(String.valueOf(t));
        if (mat == null) {
            plugin.getLogger().warning("第 " + idx + " 条商品材质无效: " + t);
            return null;
        }
        int amount = 1;
        Object a = map.get("amount");
        if (a instanceof Number && ((Number) a).intValue() > 0) amount = ((Number) a).intValue();
        return new ItemStack(mat, amount);
    }

    public void save() {
        synchronized (products) {
            YamlConfiguration yml = new YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Product p : products.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", p.getTemplate().serialize());
                m.put("category", p.getCategory());
                m.put("buy", p.getBuyPrice());
                m.put("sell", p.getSellPrice());
                m.put("stock", p.getStock());
                list.add(m);
            }
            yml.set("products", list);
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                yml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("保存 shop.yml 失败: " + e.getMessage());
            }
        }
    }

    public Product get(int id) { return products.get(id); }

    public Map<Integer, Product> all() { return products; }

    public int size() { return products.size(); }

    public Product add(ItemStack template, String category, double buy, double sell) {
        return add(template, category, buy, sell, -1);
    }

    public Product add(ItemStack template, String category, double buy, double sell, int stock) {
        synchronized (products) {
            Product p = new Product(template, category, buy, sell);
            p.setStock(stock);
            products.put(nextId, p);
            int id = nextId;
            nextId++;
            save();
            return p;
        }
    }

    /** 补货/改库存（-1=无限） */
    public boolean restock(int id, int stock) {
        synchronized (products) {
            Product p = products.get(id);
            if (p == null) return false;
            p.setStock(stock);
            save();
            return true;
        }
    }

    public boolean remove(int id) {
        synchronized (products) {
            boolean ok = products.remove(id) != null;
            if (ok) save();
            return ok;
        }
    }

    public boolean updatePrice(int id, double buy, double sell) {
        synchronized (products) {
            Product p = products.get(id);
            if (p == null) return false;
            p.setBuyPrice(buy);
            p.setSellPrice(sell);
            save();
            return true;
        }
    }

    /** 玩家可见视图：购买页隐藏不可买(价0或售罄)，收购页隐藏不可卖 */
    public List<Product> viewOf(String category, boolean buyMode) {
        List<Product> out = new ArrayList<>();
        for (Product p : products.values()) {
            if (!p.getCategory().equalsIgnoreCase(category)) continue;
            if (buyMode) {
                if (p.getBuyPrice() > 0 && p.getStock() != 0) out.add(p);
            } else {
                if (p.getSellPrice() > 0) out.add(p);
            }
        }
        return out;
    }

    public List<Product> byCategory(String category) {
        List<Product> out = new ArrayList<>();
        for (Product p : products.values()) {
            if (p.getCategory().equalsIgnoreCase(category)) out.add(p);
        }
        return out;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
