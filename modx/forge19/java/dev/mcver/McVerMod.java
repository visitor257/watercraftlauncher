package dev.mcver;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 入口（MC 1.19.4 / Forge 45.x）。
 *
 * <p>和 1.20.x/1.21.x 那份的差别，只有「这个名字改了」几句：
 * <ul>
 *   <li>创造栏事件还叫 {@code CreativeModeTabEvent.BuildContents}（{@code BuildCreativeModeTabContentsEvent}
 *       是 1.19.3 之后才拆出来的独立类，45.x 里没有）；它没有 {@code getTabKey()}，只有
 *       {@code getTab()}，id 要从 {@code CreativeModeTabRegistry.getName(tab)} 反查；</li>
 *   <li>其余（{@code @Mod}、{@code FMLJavaModLoadingContext}、{@code DeferredRegister.create(Registries.ITEM, ...)}、
 *       {@code TickEvent.ClientTickEvent}）和 1.21.1 一样。</li>
 * </ul>
 *
 * <p>不用 {@code @EventBusSubscriber}：bus 枚举在 Forge 各版本间挪过包，构造函数里手工
 * {@code addListener} 最稳（和 1.21.1 那份同一个策略）。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    private static final RegistryObject<Item> TOOL =
            ITEMS.register("download_tool", () -> new Item(new Item.Properties().stacksTo(1)));

    public McVerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        // 创造栏事件挂在 **mod bus** 上（挂 game bus 会 IllegalArgumentException）
        modBus.addListener(McVerMod::creativeTabs);
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener(McVerClient::onTick);
            LOGGER.info("mcver: 客户端 tick 已挂载（防务端加载：{}）", ModList.get() != null);
        }
        LOGGER.info("mcver: 物品已登记 {}", TOOL.getId());
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return TOOL.get();
    }

    /** 创造模式物品栏：塞进「工具与实用物品」那一栏（按注册名认）。 */
    private static void creativeTabs(CreativeModeTabEvent.BuildContents e) {
        CreativeModeTab tab = e.getTab();
        if (tab == null) return;
        ResourceLocation id = CreativeModeTabRegistry.getName(tab);
        if (id != null && id.getPath().equals("tools_and_utilities")) {
            e.accept(() -> tool());
        }
    }
}
