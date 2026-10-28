package dev.mcver;

import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 入口（MC 1.18.2 / Forge 40.x）。
 *
 * <p>这一代和 1.19.4 的差别在「创造栏怎么进」：
 * 1.19.3 之前没有 {@code CreativeModeTabEvent}，加物品栏是
 * {@code new Item.Properties().tab(CreativeModeTab.TAB_TOOLS)}（1.19.4 才把这个方法删掉）。
 * 所以这里不需要任何创造栏事件监听，构造函数只剩注册 + 挂 tick。
 *
 * <p>{@code Registries.ITEM} 这个类 1.19.3 才有；1.18.2 给 {@code Registry.ITEM_REGISTRY}
 * （{@code DeferredRegister.create(Registry.ITEM, ...)} 会落进 {@code IForgeRegistryEntry}
 * 那个过期重载，编不过）。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registry.ITEM_REGISTRY, MOD_ID);
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
