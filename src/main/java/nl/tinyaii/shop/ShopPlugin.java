package nl.tinyaii.shop;

import nl.tinyaii.shop.command.ShopCommand;
import nl.tinyaii.shop.economy.EcoBridge;
import nl.tinyaii.shop.gui.MenuListener;
import nl.tinyaii.shop.shop.ShopManager;
import nl.tinyaii.shop.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopPlugin extends JavaPlugin {

    private ShopManager shopManager;
    private nl.tinyaii.shop.shop.WorldShopManager worldShopManager;
    private EcoBridge ecoBridge;
    private Messages messages;

    @Override
    public void onEnable() {
        // TinyAII 品牌横幅 —— 必须在所有初始化逻辑之前输出（与 AutoBackup 完全一致）
        getLogger().info(" _____ _                _    ___ ___");
        getLogger().info("|_   _(_)_ __  _   _   / \\  |_ _|_ _|");
        getLogger().info("  | | | | '_ \\| | | | / _ \\  | | | |");
        getLogger().info("  | | | | | | | |_| |/ ___ \\ | | | |");
        getLogger().info("  |_| |_|_| |_|\\__, /_/   \\_\\___|___|");
        getLogger().info("               |___/");
        getLogger().info("Shop 商店系统 v" + getDescription().getVersion() + " - TinyAII 出品");

        saveDefaultConfig();
        migrateConfig();   // 升级补齐：旧 config.yml 缺失的新键自动从内置默认值合并
        if (!new java.io.File(getDataFolder(), "shop.yml").exists()) {
            saveResource("shop.yml", false);
        }
        messages = new Messages(this);
        ecoBridge = new EcoBridge(this);
        shopManager = new ShopManager(this);
        shopManager.load();
        worldShopManager = new nl.tinyaii.shop.shop.WorldShopManager(this);
        worldShopManager.load();

        getCommand("商店").setExecutor(new ShopCommand(this));
        getCommand("商店").setTabCompleter(new ShopCommand(this));
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        getLogger().info("官方商店 " + shopManager.size() + " 件 | 世界商店 "
                + worldShopManager.all().size() + " 件在售。指令: /商店"

                + (ecoBridge.isAvailable() ? "" : "（警告：Economy 未安装，交易不可用）"));
    }

    @Override
    public void onDisable() {
        if (shopManager != null) shopManager.save();
    }

    public void reloadAll() {
        reloadConfig();
        migrateConfig();
        messages.reload();
        ecoBridge.refreshCurrency();
    }

    /**
     * 配置迁移：旧版 config.yml 缺失的新键（如 world-listed 等世界商店消息、
     * worldshop 段）从 jar 内默认配置合并补齐；已有键不动（保留用户自定义值）。
     */
    private void migrateConfig() {
        java.io.File f = new java.io.File(getDataFolder(), "config.yml");
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration user =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        java.io.InputStream defStream = getResource("config.yml");
        if (defStream == null) return;
        org.bukkit.configuration.file.YamlConfiguration def =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (!user.contains(key)) {
                user.set(key, def.get(key));
                changed = true;
            }
        }
        // 顶层注释段说明键不写入数据，仅写实际存在的路径
        if (changed) {
            try { user.save(f); getLogger().info("config.yml 已自动补齐新版配置项。"); }
            catch (Exception e) { getLogger().warning("config.yml 迁移失败: " + e.getMessage()); }
        }
    }

    public ShopManager getShopManager() { return shopManager; }
    public nl.tinyaii.shop.shop.WorldShopManager getWorldShopManager() { return worldShopManager; }
    public EcoBridge getEcoBridge() { return ecoBridge; }
    public Messages getMessages() { return messages; }

    public static String colorWrap(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
