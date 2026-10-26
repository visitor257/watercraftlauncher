package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 1.19 ~ 1.19.2 兼容层：公共初始化。
 *
 * <p><b>为什么 1.19 要单独一个 jar，不能和 1.19.3/1.19.4 共用</b>（这条是 javap + mappings 核出来的）：
 * <ul>
 *   <li>{@code Registries} 这个类 1.19.3 才有；1.19 ~ 1.19.2 里注册表实例挂在
 *       {@code net.minecraft.util.registry.Registry.ITEM} 上；</li>
 *   <li>更要命的是<b>字段描述符</b>：{@code ITEM} 字段在 1.19 ~ 1.19.2 的类型是
 *       {@code Registry}（{@code class_2378}），1.19.3 起变成 {@code DefaultedRegistry}
 *       （{@code class_7922}）。后者在 1.19 里<b>根本不存在</b> → 用 1.19.4 编出来的 jar
 *       在 1.19 上直接 {@code NoClassDefFoundError: class_7922}（实测过，就在入口点创建那一步）。</li>
 *   <li>创造栏：1.19 ~ 1.19.2 的 fabric-api（0.58.x）只有 {@code FabricItemGroupBuilder}（v0），
 *       没有 {@code ItemGroupEvents}（v1，1.19.3 起才有）。</li>
 * </ul>
 * 结论：1.19 ~ 1.19.2 一个 jar，1.19.3 ~ 1.19.4 一个 jar。
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Identifier ITEM_ID = new Identifier(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new Item(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registry.ITEM, ITEM_ID, DOWNLOAD_TOOL);

        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
