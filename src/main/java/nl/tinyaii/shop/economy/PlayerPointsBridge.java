package nl.tinyaii.shop.economy;

import nl.tinyaii.shop.ShopPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * PlayerPoints 点券联动（反射调用，编译期零依赖）。
 * 用户服务器装 PlayerPoints 时商店可用点券结算；没装跳过。
 *
 * 反射链：PlayerPoints.getPlugin(...) → getAPI() → look(uuid)/take(uuid,n)/give(uuid,n)
 * （PlayerPoints API：IPointsAPI 有 look/getPoints/setPoints/take/give）
 */
public class PlayerPointsBridge {

    private final ShopPlugin plugin;
    private boolean available;
    private Object apiInstance;           // IPlayerPointsAPI 实例（反射持有）
    private Method mLook, mTake, mGive, mSet;
    private static final String CURRENCY = "点券";

    public PlayerPointsBridge(ShopPlugin plugin) {
        this.plugin = plugin;
        tryInit();
    }

    private void tryInit() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) return;
        try {
            Class<?> ppClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            // PlayerPoints 主类通常是 PlayerPoints.getPlugin(PlayerPoints.class) 静态单例
            Object main = null;
            for (Method m : ppClass.getDeclaredMethods()) {
                if (m.getName().equals("getPlugin") && m.getParameterCount() == 1
                        && m.getReturnType().isInstance(null) == false) {
                    // 保守：用 getPlugin(Class) 常规 Bukkit 模式取实例
                }
            }
            // PlayerPoints 3.x: 静态方法 PlayerPoints.getPlugin(PlayerPoints.class) 来自 JavaPlugin
            Method getPlugin = ppClass.getMethod("getPlugin", Class.class);
            main = getPlugin.invoke(null, ppClass);

            // getAPI() → IPlayerPointsAPI；API 接口有 look(UUID)/take(UUID,int)/give(UUID,int)/setPoints(UUID,int)
            Method getApi = main.getClass().getMethod("getAPI");
            Object api = getApi.invoke(main);

            // IPointsAPI 接口方法（反射找签名）
            Class<?> apiIface = api.getClass();
            for (Method m : apiIface.getMethods()) {
                String n = m.getName();
                if (n.equals("look") || n.equals("getPoints")) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) mLook = m;
                } else if (n.equals("take")) {
                    if (m.getParameterCount() == 2) mTake = m;
                } else if (n.equals("give") || n.equals("add")) {
                    if (m.getParameterCount() == 2) mGive = m;
                } else if (n.equals("setPoints") || n.equals("set")) {
                    if (m.getParameterCount() == 2) mSet = m;
                }
            }
            apiInstance = api;
            if (mLook == null) throw new IllegalStateException("未找到 look 方法");
            available = true;
            plugin.getLogger().info("已检测到 PlayerPoints 插件，商店支持点券交易。");
        } catch (Throwable t) {
            plugin.getLogger().warning("PlayerPoints API 反射失败（点券交易禁用）: " + t.getMessage());
            available = false;
        }
    }

    public boolean isAvailable() { return available; }

    public String getCurrencyName() { return CURRENCY; }

    public double getBalance(UUID uuid) {
        if (!available || mLook == null) return 0;
        try {
            Object r = mLook.invoke(apiInstance, uuid);
            return r instanceof Number ? ((Number) r).doubleValue() : 0;
        } catch (Exception e) { return 0; }
    }

    /** 返回 true=余额足够 */
    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount - 0.001;
    }

    /** 扣除；返回 true=成功 */
    public boolean withdraw(UUID uuid, double amount) {
        if (!available) return false;
        int intAmount = (int) Math.ceil(amount);
        if (!has(uuid, intAmount)) return false;
        try {
            if (mTake != null) {
                Object r = mTake.invoke(apiInstance, uuid, intAmount);
                if (r instanceof Boolean) return (Boolean) r;
                // take 可能返回点数 int
                if (r instanceof Number) return true;
            } else if (mSet != null) {
                double bal = getBalance(uuid);
                mSet.invoke(apiInstance, uuid, (int) Math.floor(bal - intAmount));
                return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    public void deposit(UUID uuid, double amount) {
        if (!available || amount <= 0) return;
        int intAmount = (int) Math.floor(amount);
        if (intAmount <= 0) return;
        try {
            if (mGive != null) {
                mGive.invoke(apiInstance, uuid, intAmount);
            } else if (mSet != null) {
                double bal = getBalance(uuid);
                mSet.invoke(apiInstance, uuid, (int) Math.floor(bal + intAmount));
            }
        } catch (Exception ignored) {}
    }
}