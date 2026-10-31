package dev.mcver;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 入口（MC 1.21.5 起 / Forge 55+）。
 *
 * <p><b>为什么需要单独一份：</b>Forge 1.21.5 换掉了整套事件总线 ——
 * {@code net.minecraftforge.eventbus.api.IEventBus} 没了，改成
 * {@code net.minecraftforge.eventbus.api.bus.BusGroup} + 每个事件自己的静态 {@code BUS}。
 * 老的 {@code forge21}/{@code forge214} 入口在这代直接编不过（实测 1.21.8：
 * 「程序包 net.minecraftforge.eventbus.api 不存在 / 找不到符号 IEventBus」）。
 *
 * <p>三处对应关系（javap 1.21.8-58.1.26 实测）：
 * <ul>
 *   <li>注册：{@code DeferredRegister.register(BusGroup)}，bus 用
 *       {@code FMLJavaModLoadingContext.get().getModBusGroup()}
 *       （<b>不能用构造函数注入</b>：实测 1.21.8 报
 *       {@code NoSuchMethodException: dev.mcver.McVerMod.<init>()} —— 这一代的 javafml
 *       还没支持我们这种参数类型，无参构造函数是能跑的那一种）；</li>
 *   <li>创造栏：{@code BuildCreativeModeTabContentsEvent.getBus(busGroup).addListener(...)}，
 *       且 {@code accept} 要多传一个 {@code CreativeModeTab.TabVisibility}；</li>
 *   <li>客户端 tick：{@code TickEvent.ClientTickEvent.BUS.addListener(...)}（原来是
 *       {@code MinecraftForge.EVENT_BUS}，现在那个字段只剩一个迁移占位类）。</li>
 * </ul>
 *
 * <p>{@code new Item(...)} 仍在 supplier 里做；{@code setId} 从 1.21.2 起是硬性要求。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    private static final RegistryObject<Item> TOOL = ITEMS.register("download_tool",
            () -> new Item(new Item.Properties().stacksTo(1).setId(
                    ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "download_tool")))));

    public McVerMod() {
        BusGroup modBus = FMLJavaModLoadingContext.get().getModBusGroup();
        ITEMS.register(modBus);
        BuildCreativeModeTabContentsEvent.getBus(modBus).addListener(McVerMod::creativeTabs);
        if (FMLEnvironment.dist.isClient()) {
            TickEvent.ClientTickEvent.BUS.addListener(McVerClient::onTick);
            LOGGER.info("mcver: 客户端 tick 已挂载");
        }
        LOGGER.info("mcver: 物品已登记 {}", TOOL.getId());
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return TOOL.get();
    }

    /** 创造模式物品栏：塞进「工具与实用物品」那一栏（按注册名认，字段是 private）。 */
    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        ResourceLocation id = e.getTabKey().location();
        if (id.getPath().equals("tools_and_utilities")) {
            e.accept(new ItemStack(tool()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
