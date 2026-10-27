package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.item.itemgroup.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 1.13.2（Legacy Fabric 段）公共初始化。
 *
 * <p>差异：
 * <ul>
 *   <li>{@code Item.Settings} 有了（1.14 起才叫 {@code maxCount}，1.13 叫 {@code setMaxStackSize}），
 *       创造栏用 {@code setGroup(ItemGroup.MISC)} 挂进「杂项」—— 这一代还没有
 *       {@code FabricItemGroupBuilder} 可用（Legacy Fabric API 的包名/命名跟本工程对不上，
 *       所以整个 1.13.2 target 不用 fabric-api）；</li>
 *   <li>注册表没有静态 {@code register}，只能 {@code Registry.ITEM.add(...)}；</li>
 *   <li>右键行为在 {@link McVerItem} 里 override。</li>
 * </ul>
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Identifier ITEM_ID = new Identifier(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new McVerItem(
            new Item.Settings().setMaxStackSize(1).setGroup(ItemGroup.MISC));

    @Override
    public void onInitialize() {
        Registry.ITEM.add(ITEM_ID, DOWNLOAD_TOOL);
        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
