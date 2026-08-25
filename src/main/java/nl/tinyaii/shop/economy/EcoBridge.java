package nl.tinyaii.shop.economy;

import nl.tinyaii.shop.ShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Economy 反射联动（全家桶同款）：装了才能交易，没装禁用并提示。
 */
public class EcoBridge {

    private final ShopPlugin plugin;
    private boolean available;
    private Method mGetBalance, mHas, mWithdraw, mDeposit;
    private String currencyName = "金币";

    public EcoBridge(ShopPlugin plugin) {
        this.plugin = plugin;
        tryInit();
    }

    private void tryInit() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("Economy") == null) return;
        try {
            Class<?> api = Class.forName("nl.tinyaii.economy.api.EconomyAPI");
            mGetBalance = api.getMethod("getBalance", java.util.UUID.class);
            mHas = api.getMethod("has", java.util.UUID.class, double.class);
            mWithdraw = api.getMethod("withdraw", java.util.UUID.class, double.class);
            mDeposit = api.getMethod("deposit", java.util.UUID.class, double.class);
            currencyName = readCurrencyName();
            available = true;
            plugin.getLogger().info("已检测到 Economy 插件，商店交易启用。");
        } catch (Throwable t) {
            plugin.getLogger().warning("Economy API 反射失败: " + t.getMessage());
        }
    }

    private String readCurrencyName() {
        try {
            Plugin eco = Bukkit.getPluginManager().getPlugin("Economy");
            File cfgFile = new File(eco.getDataFolder(), "config.yml");
            if (cfgFile.exists()) {
                org.bukkit.configuration.file.YamlConfiguration yml =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);
                return yml.getString("settings.currency-name", "金币");
            }
        } catch (Throwable ignored) {}
        return "金币";
    }

    /** reload 时重读货币名（只读配置，绝不重新初始化） */
    public void refreshCurrency() {
        if (!available) {
            tryInit();   // Economy 可能是后装的；tryInit 成功时会打一次日志
            return;
        }
        currencyName = readCurrencyName();
    }

    public boolean isAvailable() { return available; }

    public double getBalance(java.util.UUID uuid) {
        try { return (Double) mGetBalance.invoke(null, uuid); }
        catch (Exception e) { return 0; }
    }

    public boolean has(java.util.UUID uuid, double amount) {
        try { return (Boolean) mHas.invoke(null, uuid, amount); }
        catch (Exception e) { return false; }
    }

    public boolean withdraw(java.util.UUID uuid, double amount) {
        try { return (Boolean) mWithdraw.invoke(null, uuid, amount); }
        catch (Exception e) { return false; }
    }

    public void deposit(java.util.UUID uuid, double amount) {
        if (amount <= 0) return;
        try { mDeposit.invoke(null, uuid, amount); } catch (Exception ignored) {}
    }

    public String getCurrencyName() { return currencyName; }
}
