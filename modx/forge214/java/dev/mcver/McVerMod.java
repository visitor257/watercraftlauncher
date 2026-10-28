package dev.mcver;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 入口（MC 1.21.4 / Forge 54.x）。
 *
 * <p>和 NeoForge 版逻辑一致，差别都在包名和 API：
 * <ul>
 *   <li>事件总线在 {@code net.minecraftforge.eventbus.api}，客户端 tick 用
 *       {@code TickEvent.ClientTickEvent} + {@code phase == END}（NeoForge 20.2+ 是
 *       {@code ClientTickEvent.Post}）；</li>
 *   <li>注册表用 {@code DeferredRegister.create(Registries.ITEM, MOD_ID)}（Forge 没有
 *       {@code createItems}）；</li>
 *   <li>不用 {@code @EventBusSubscriber} 注解，直接在构造函数里 {@code addListener}：
 *       注解的 bus 枚举在 Forge 各版本间挪过包，手工挂载最稳。</li>
 * </ul>
 *
 * <p>{@code new Item(...)} 同样必须在 supplier 里做（1.21.1 的 Item 构造函数会碰注册表，
 * 静态初始化时注册表已冻结 → {@code Registry is already frozen}）。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    // 1.21.2 起 Item.Properties 必须显式 setId：不设的话注册时 NPE「Item id not set」
    private static final RegistryObject<Item> TOOL = ITEMS.register("download_tool",
            () -> new Item(new Item.Properties().stacksTo(1).setId(
                    ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "download_tool")))));

    public McVerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
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

    /** 创造模式物品栏：塞进「工具与实用物品」那一栏（按注册名认，字段是 private）。 */
    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        ResourceLocation id = e.getTabKey().location();
        if (id.getPath().equals("tools_and_utilities")) {
            e.accept(new ItemStack(tool()));
        }
    }
}
