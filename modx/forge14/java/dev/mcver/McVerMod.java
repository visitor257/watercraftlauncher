package dev.mcver;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge 入口（MC 1.14.4 / Forge 28.x）。
 *
 * <p>和 1.16.5 那份几乎一样，只有两处：
 * <ul>
 *   <li>创造栏字段在 1.14.4 叫 {@code TAB_*}，1.16 改叫 {@code TAB_*} 但类名从
 *       {@code ItemGroup} 换成 {@code CreativeModeTab}（mojmap 统一后都是 CreativeModeTab）；</li>
 *   <li>物品注册照旧 {@code DeferredRegister<Item>} + {@code RegistryObject}（1.14 就在
 *       {@code net.minecraftforge.fml} 包里）。</li>
 * </ul>
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    // 1.14.4 的 DeferredRegister 没有静态 create（1.16 才加），只能 new
    private static final DeferredRegister<Item> ITEMS =
            new DeferredRegister<>(ForgeRegistries.ITEMS, MOD_ID);
    private static final RegistryObject<Item> TOOL = ITEMS.register("download_tool",
            () -> new Item(new Item.Properties().stacksTo(1).tab(CreativeModeTab.TAB_TOOLS)));

    public McVerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
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
}
