package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.18.2 兼容层：公共初始化。
 *
 * <p>与主源码（1.21.4）、1.19.4 的差异：
 * <ul>
 *   <li>注册表入口在 {@code net.minecraft.util.registry.Registry}（{@code Registries} 类 1.19.3 才有），
 *       所以是 {@code Registry.register(Registry.ITEM, id, item)}；</li>
 *   <li>{@code Identifier.of(ns, path)} 没有，只能 {@code new Identifier(ns, path)}；</li>
 *   <li>{@code ItemGroupEvents}（把物品塞进创造栏）在 Fabric API 0.77 / 1.18.2 里不存在，
 *       那边只有 {@code FabricItemGroupBuilder}（客户端），所以在 {@link McVerClient} 里
 *       自建一个「Water Craft Launcher」创造栏，而不是塞进原版「工具」栏。</li>
 * </ul>
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier ITEM_ID = new Identifier(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new Item(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registry.ITEM, ITEM_ID, DOWNLOAD_TOOL);

        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
