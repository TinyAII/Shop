package nl.tinyaii.shop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GUI 标识：区分主菜单/商品页，携带模式与翻页状态。
 */
public class MenuHolder implements InventoryHolder {

    public enum Type { MAIN, SHOP, CATEGORY, WORLD }

    private final Type type;
    /** buy=购买页 / sell=收购页（MAIN 时无意义） */
    private final String mode;
    private final String category;
    private final int page;
    private final int pages;

    public MenuHolder(Type type, String mode, String category, int page, int pages) {
        this.type = type;
        this.mode = mode;
        this.category = category;
        this.page = page;
        this.pages = pages;
    }

    @Override
    public Inventory getInventory() { return null; }

    public Type getType() { return type; }
    public String getMode() { return mode; }
    public String getCategory() { return category; }
    public int getPage() { return page; }
    public int getPages() { return pages; }
}
