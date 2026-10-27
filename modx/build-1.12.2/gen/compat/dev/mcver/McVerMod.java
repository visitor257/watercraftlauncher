package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * 1.12.2（Legacy Fabric）公共初始化。
 *
 * <p>差异：
 * <ul>
 *   <li>{@code Item} 是 <b>无参构造</b>（{@code Item.Settings} 是 1.14 才有的），
 *       {@code maxCount(1)} 对应 protected 字段 {@code maxCount}（不好直接改，先不设）；</li>
 *   <li>注册表是 {@code Item.REGISTRY.put(id, item)}（1.16 起才是 {@code Registry.register}）；</li>
 *   <li>创造栏用 {@code ItemGroup.TOOLS.method_13646(list)} 追加（1.12 没有 ItemGroupEvents）。</li>
 * </ul>
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Identifier ITEM_ID = new Identifier(MOD_ID, "download_tool");

    /** 1.12 的 Item 构造函数不吃参数；右键行为在 {@link McVerItem} 里 override。 */
    public static final Item DOWNLOAD_TOOL = new McVerItem();

    @Override
    public void onInitialize() {
        Item.REGISTRY.put(ITEM_ID, DOWNLOAD_TOOL);
        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
