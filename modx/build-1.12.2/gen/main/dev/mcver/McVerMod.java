package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 公共初始化：只干一件事——注册那个物品。
 *
 * <p><b>为什么 {@code registryKey} 要包 try/catch：</b>
 * {@code Item.Settings#registryKey(RegistryKey&lt;Item&gt;)} 是 <b>1.21.2</b> 才加的。
 * 一个 jar 要通吃 1.21 ~ 1.21.11，编译基准只能是“有这个方法”的那一端（这里取最新的
 * 1.21.11），然后在 1.21 / 1.21.1 上靠 {@code NoSuchMethodError} 退回来。
 *
 * <p>为什么不反射：mod 会被 remap 成 intermediary 名（{@code RegistryKey} → {@code class_5321}），
 * 运行期 {@code Class.forName("net.minecraft.registry.RegistryKey")} 和
 * {@code getMethod("registryKey")} 都找不到 —— 这条试过了，日志里就是
 * “该版本无 Item.Settings#registryKey，跳过”然后 {@code Item id not set} 崩在静态初始化里。
 * <b>已 remap 的环境里，字符串反射查 MC 符号一徇不通。</b>
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Identifier ITEM_ID = Identifier.of(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new Item(makeSettings());

    /**
     * 物品设置。1.21.2+ 必须绑 registryKey（不绑会 {@code Item id not set}），
     * 1.21 / 1.21.1 根本没这个方法，直接退回来。
     */
    private static Item.Settings makeSettings() {
        Item.Settings s = new Item.Settings().maxCount(1);
        try {
            s.registryKey(RegistryKey.of(RegistryKeys.ITEM, ITEM_ID));
        } catch (Throwable ignored) {
            // 1.21 / 1.21.1：没有 registryKey，Registry.register 会自己绑
        }
        return s;
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, ITEM_ID, DOWNLOAD_TOOL);

        // 塞进创造模式「工具」栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(DOWNLOAD_TOOL));

        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
