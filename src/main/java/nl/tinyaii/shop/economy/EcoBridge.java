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
    private Method mGetPoints, mDepositPoints, mWithdrawPoints;   // Economy v2.0 点券通道
    private String currencyName = "金币";
    private String pointsName = "点券";
    /** 点券回退后端：未装 Economy 但装 PlayerPoints 时自动使用 */
    private PlayerPointsBridge pointsBridge;

    public EcoBridge(ShopPlugin plugin) {
        this.plugin = plugin;
        this.pointsBridge = new PlayerPointsBridge(plugin);
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
            // Economy v2.0：点券 API（v1.x 无这些方法 → 捕获跳过，点券通道不可用）
            try { mGetPoints = api.getMethod("getPoints", java.util.UUID.class); } catch (NoSuchMethodException ignored) {}
            try { mDepositPoints = api.getMethod("depositPoints", java.util.UUID.class, int.class); } catch (NoSuchMethodException ignored) {}
            try { mWithdrawPoints = api.getMethod("withdrawPoints", java.util.UUID.class, int.class); } catch (NoSuchMethodException ignored) {}
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

    public boolean isAvailable() {
        return available || pointsBridge.isAvailable();
    }

    /** 当前实际生效的货币名（金币优先，点券回退） */
    public String getActiveCurrencyName() {
        if (available) return currencyName;
        return pointsBridge.getCurrencyName();
    }

    /** 当前实际余额（按生效后端） */
    public double getActiveBalance(java.util.UUID uuid) {
        if (available) return getBalance(uuid);
        return pointsBridge.getBalance(uuid);
    }

    // ---------- Economy v2.0 点券通道 ----------

    /** 点券通道是否可用（Economy v2.0 内置点券 或 PlayerPoints 回退） */
    public boolean isPointsAvailable() {
        return (available && mGetPoints != null) || pointsBridge.isAvailable();
    }

    public String getPointsName() {
        if (available && mGetPoints != null) return pointsName;
        return pointsBridge.getCurrencyName();
    }

    public int getPointsBalance(java.util.UUID uuid) {
        if (available && mGetPoints != null) {
            try { Object r = mGetPoints.invoke(null, uuid); return r instanceof Number ? ((Number) r).intValue() : 0; }
            catch (Exception e) { return 0; }
        }
        return (int) pointsBridge.getBalance(uuid);
    }

    public void withdrawPoints(java.util.UUID uuid, int amount) {
        if (available && mWithdrawPoints != null) {
            try { mWithdrawPoints.invoke(null, uuid, amount); } catch (Exception ignored) {}
            return;
        }
        pointsBridge.withdraw(uuid, amount);
    }

    public void depositPoints(java.util.UUID uuid, int amount) {
        if (available && mDepositPoints != null) {
            try { mDepositPoints.invoke(null, uuid, amount); } catch (Exception ignored) {}
            return;
        }
        pointsBridge.deposit(uuid, amount);
    }

    public double getBalance(java.util.UUID uuid) {
        if (available) {
            try { return (Double) mGetBalance.invoke(null, uuid); }
            catch (Exception e) { return 0; }
        }
        return pointsBridge.getBalance(uuid);
    }

    public boolean has(java.util.UUID uuid, double amount) {
        if (available) {
            try { return (Boolean) mHas.invoke(null, uuid, amount); }
            catch (Exception e) { return false; }
        }
        return pointsBridge.has(uuid, amount);
    }

    public boolean withdraw(java.util.UUID uuid, double amount) {
        if (available) {
            try { return (Boolean) mWithdraw.invoke(null, uuid, amount); }
            catch (Exception e) { return false; }
        }
        return pointsBridge.withdraw(uuid, amount);
    }

    public void deposit(java.util.UUID uuid, double amount) {
        if (amount <= 0) return;
        if (available) {
            try { mDeposit.invoke(null, uuid, amount); } catch (Exception ignored) {}
        } else {
            pointsBridge.deposit(uuid, amount);
        }
    }

    public String getCurrencyName() { return currencyName; }
}
