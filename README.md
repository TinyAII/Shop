# Shop 商店系统

轻量级双商店插件：官方商店（管理员上架，库存可选限量/无限）+ 世界商店（所有玩家自由挂售、真实库存、货款直达卖家）。GUI 收购出售双页签、分类浏览、物品名全汉化、Economy 反射联动。零硬依赖。

![Version](https://img.shields.io/badge/version-1.0.0-blue) ![License](https://img.shields.io/badge/license-MIT-green) ![API](https://img.shields.io/badge/API-1.16%2B-orange)

## 功能特性

### 🏛️ 官方商店（管理员经营）
- **收购/出售双页签**：点商品=单个交易，Shift=整组（买）/全部（卖）
- **库存管理**：上架时可设数量（`-1`=无限 / `n`=限量），卖完自动显示售罄不可购买，`补货` 命令一键恢复——防经济通胀
- **单向商品**：价格填 `0` 关闭该方向，可做"仅出售给系统"的回收站或"仅可购买"的限定品
- **税费**：卖出税率可配（默认 0%）

### 🌍 世界商店（玩家互市）
- **人人可上架**：手持物品 `/商店 上架 <单价> [数量]`，从背包真实收货
- **真实库存**：挂多少卖多少，天然守恒；买空自动下架
- **离线到账**：货款经 Economy 按 UUID 直达卖家钱包，不在线也能收钱；在线实时通知
- **安全防护**：限价区间、每人最多 9 个挂售单（可配）、不能购买自己的商品、300ms 防抖

### 通用
- **GUI 全流程**：主菜单 → 分类选择 → 商品网格分页翻页，底部实时余额
- **物品名全汉化**：内置 200+ 常用物品中文名（矿物/食物/方块/工具武器防具/红石），自定义名优先
- **完整 NBT 保留**：附魔/自定义名/Lore 商品照常上架交易；同款校验防刷
- **配置自动迁移**：升级新版本缺失配置项自动补齐，老服无痛更新
- **中文命令 + Tab 补全**

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/商店` | 打开商店主菜单 | shop.use |
| `/商店 购买` / `/商店 收购` | 直接进对应区 | shop.use |
| `/商店 世界` | 浏览玩家挂售商品 | shop.use |
| `/商店 上架 <单价> [数量]` | 手持物品挂售到世界商店 | shop.use |
| `/商店 我的` | 查看我的挂售单 | shop.use |
| `/商店 下架 <单号>` | 收回挂售商品 | shop.use |
| `/商店 管理 上架 <买价\|0> <卖价\|0> [分类] [库存]` | 官方商店上架（0=关闭该方向） | shop.admin |
| `/商店 管理 补货 <序号> <数量>` | 补充官方库存（-1=无限） | shop.admin |
| `/商店 管理 改价 <序号> <买> <卖>` | 修改价格 | shop.admin |
| `/商店 管理 下架 <序号>` / `列表` | 下架 / 列表 | shop.admin |
| `/商店 重载` | 重载配置 | shop.admin |

权限默认值：`shop.use` 所有人、`shop.admin` OP。

## 配置示例

```yaml
settings:
  sell-tax-rate: 0.0      # 官方收购税率
  full-inventory: drop    # 背包满策略
worldshop:
  enabled: true
  tax-rate: 0.05          # 世界商店成交手续费（向卖家收）
  max-listings: 9         # 每人最多挂售数
  min-price: 0.1
  max-price: 100000
```

## 兼容性

- 支持核心：Spigot / Paper / Purpur / Leaves
- API 版本：1.16+（spigot-api 1.16.5 编译，理论兼容至最新版）
- Java：17+
- 前置依赖：无（Economy 为可选联动，未装时商店禁用交易并提示）

## 开源协议

MIT License

---

# Shop (English)

Lightweight dual-shop plugin: Admin Shop (admin-curated, optional limited/infinite stock) + World Shop (player-to-player market with real stock and direct seller payout). GUI buy/sell tabs, categories, localized item names, Economy integration via reflection. Zero hard dependencies.

## Features

- **Admin Shop**: buy/sell tabs, per-product stock (-1 infinite / n limited), restock command, one-way products (price 0 disables a direction), configurable tax
- **World Shop**: every player can list items from inventory with custom price; real stock; auto-delist when sold out; payout goes directly to the seller's wallet even offline; anti-abuse limits
- **Full GUI flow**: main menu → category picker → paginated grid with live balance
- **Localized item names**: 200+ built-in Chinese names, custom names take priority
- **Full NBT support**: enchanted/named items tradeable with strict same-item matching
- **Config auto-migration**: missing keys merged on upgrade

## Compatibility

- Server: Spigot / Paper / Purpur / Leaves
- API version: 1.16+
- Java 17+
- Dependencies: none (optional Economy)

## License

MIT License

## Author

**TinyAII**
