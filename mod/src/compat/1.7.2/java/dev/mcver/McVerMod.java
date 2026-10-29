package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;

/**
 * 1.7.10（Legacy Fabric）公共初始化。
 *
 * <p>1.7 的注册表是最老那套：{@code Item.REGISTRY.add(int id, String name, Object item)}，
 * 没有泛型、也没有 {@code put(Identifier, T)}；id 得自己找空位（这里从 4000 往上扫）。
 * 名字是 {@code "mcver:download_tool"} 这种字符串。
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final String ITEM_NAME = MOD_ID + ":download_tool";

    /** 1.7 的 Item 构造函数不吃参数；右键行为在 {@link McVerItem} 里 override。 */
    public static final Item DOWNLOAD_TOOL = new McVerItem();

    @Override
    public void onInitialize() {
        int id = freeId();
        Item.REGISTRY.add(id, ITEM_NAME, DOWNLOAD_TOOL);
        LOGGER.info("mcver: 物品已注册 {} (id={})", ITEM_NAME, id);
    }

    /** 找一个没人用的数值 id（1.7 的世界里这仍是硬要求）。 */
    private static int freeId() {
        for (int i = 4000; i < 32000; i++) {
            try {
                if (Item.REGISTRY.getByRawId(i) == null) return i;
            } catch (Throwable ignored) {
                return i;
            }
        }
        return 31999;
    }
}
