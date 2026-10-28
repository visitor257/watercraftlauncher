package dev.mcver;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge 入口（MC 1.16.5 / Forge 36.x）。
 *
 * <p>这一代的两个「名字不同」：
 * <ul>
 *   <li>{@code RegistryObject} 还在 {@code net.minecraftforge.fmllegacy} 包里
 *       （1.17 起挪到 {@code net.minecraftforge.fmllegacy}、1.18.2 才回 {@code registries}）；</li>
 *   <li>{@code DeferredRegister.create} 只有 {@code (IForgeRegistry,String)} 和
 *       {@code (Class,String)} 两个重载（{@code Registries} 那个 {@code ResourceKey}
 *       重载 1.18.2 才有）→ 用 {@code ForgeRegistries.ITEMS}。</li>
 * </ul>
 *
 * <p><b>编译前置：</b>必须用 {@code mk-srg-mojmap.mjs ... --patched} 生成 mojmap jar。
 * vanilla 的 {@code client-*-srg.jar} 里 {@code Item} <b>不</b>继承 {@code ForgeRegistryEntry}
 * （那是 Forge 在运行期用 ModLauncher 打补丁加上去的），拿它编译的话
 * {@code DeferredRegister<Item>} 会卡在 {@code T extends IForgeRegistryEntry<T>} 上编不过。
 * 直接用 {@code Registry.register(...)} 也不行 —— 构造期注册表已经锁了
 * （{@code Can not register to a locked registry}）。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
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
