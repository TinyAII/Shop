package nl.tinyaii.shop.command;

/**
 * 命令双语映射：中文 ↔ 英文 子命令别名归一化。
 * 用户输入中/英文均可，内部统一转成中文标准词处理。
 */
public final class CommandAliases {

    private CommandAliases() {}

    /** 把输入的子命令归一化为中文标准词；未知则原样返回 */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return input;
        switch (input.toLowerCase()) {
            // 主命令
            case "buy": return "购买";
            case "sell": return "收购";
            case "world": return "世界";
            case "list": return "上架";
            case "mine": case "my": return "我的";
            case "delist": case "cancel": return "下架";
            case "admin": return "管理";
            case "add": case "create": return "上架";
            case "remove": case "delete": return "下架";
            case "price": return "改价";
            case "restock": return "补货";
            case "ls": case "items": return "列表";
            case "reload": return "重载";
            // 币种参数也支持英文
            case "gold": case "coin": return "金币";
            case "points": case "point": return "点券";
            default: return input;
        }
    }
}
