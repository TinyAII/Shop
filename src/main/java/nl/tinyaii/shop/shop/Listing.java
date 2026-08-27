package nl.tinyaii.shop.shop;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 世界商店挂售单：玩家上架的真实库存商品。
 */
public class Listing {
    private final int id;
    private final UUID owner;
    private String ownerName;
    private final ItemStack template;   // 数量恒为1的展示模板
    private double unitPrice;
    private String currency = "gold";   // 结算币种：gold=金币 / points=点券
    private int stock;

    public Listing(int id, UUID owner, String ownerName, ItemStack template, double unitPrice, int stock) {
        this(id, owner, ownerName, template, unitPrice, stock, "gold");
    }

    public Listing(int id, UUID owner, String ownerName, ItemStack template, double unitPrice, int stock, String currency) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.template = template.clone();
        this.template.setAmount(1);
        this.unitPrice = unitPrice;
        this.currency = (currency != null && currency.equalsIgnoreCase("points")) ? "points" : "gold";
        this.stock = Math.max(0, stock);
    }

    public int getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String n) { this.ownerName = n; }
    /** 展示模板（数量恒为1） */
    public ItemStack getTemplate() { return template.clone(); }
    public org.bukkit.Material getMaterial() { return template.getType(); }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double v) { this.unitPrice = v; }
    public String getCurrency() { return currency; }
    public boolean isPoints() { return currency.equals("points"); }
    public int getStock() { return stock; }
    public void setStock(int s) { this.stock = Math.max(0, s); }

    /** 发货用：n 个副本 */
    public ItemStack take(int n) {
        ItemStack copy = template.clone();
        copy.setAmount(n);
        return copy;
    }
}
