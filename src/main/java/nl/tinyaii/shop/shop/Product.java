package nl.tinyaii.shop.shop;

import org.bukkit.inventory.ItemStack;

/**
 * 商品模型：完整 ItemStack 模板（附魔/NBT保留）+ 双价格 + 分类。
 */
public class Product {
    private final ItemStack template;   // 永远 clone 使用
    private String category;
    private double buyPrice;            // 玩家购买单价（0 = 不可购买）
    private double sellPrice;           // 玩家卖出单价（0 = 不可出售）
    private int stock = -1;             // 购买库存（-1 = 无限；0 = 售罄；防经济通胀需显式设定）

    public Product(ItemStack template, String category, double buyPrice, double sellPrice) {
        this.template = template.clone();
        this.category = category;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public ItemStack getTemplate() { return template.clone(); }
    public String getMaterialName() { return template.getType().name(); }
    public int getMaxStack() { return template.getMaxStackSize(); }
    public String getCategory() { return category; }
    public void setCategory(String c) { this.category = c; }
    public double getBuyPrice() { return buyPrice; }
    public void setBuyPrice(double v) { this.buyPrice = v; }
    public double getSellPrice() { return sellPrice; }
    public void setSellPrice(double v) { this.sellPrice = v; }
    /** -1=无限 */
    public int getStock() { return stock; }
    public void setStock(int s) { this.stock = s; }

    /** 是否可购买 n 个（考虑库存与限购0） */
    public int purchasable(int want) {
        if (buyPrice <= 0) return 0;
        if (stock == -1) return want;
        return Math.max(0, Math.min(want, stock));
    }
}
