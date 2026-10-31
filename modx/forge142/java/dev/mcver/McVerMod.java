package dev.mcver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 入口（MC 1.14.2 / 1.14.3，Forge 26/27）。
 *
 * <p><b>为什么不能复用 forge14</b>：那两份是按 1.14.4（Forge 28）写的，而这两代
 * **根本没有 {@code DeferredRegister}** —— javap 验过：Forge 26 的 universal jar 里
 * 只有 {@code net.minecraftforge.fml.RegistryObject}，没有 DeferredRegister
 * （那个类是 1.14.4/Forge 28 才进来的）。
 * 同时事件包名也还是老的：{@code TickEvent} 在
 * {@code net.minecraftforge.fml.common.gameevent}，而不是 {@code net.minecraftforge.event}。
 *
 * <p>所以物品注册回到那一代的标准写法：{@code RegistryEvent.Register<Item>} +
 * {@code setRegistryName}，静态订阅走 {@code @Mod.EventBusSubscriber(bus = Bus.MOD)}。
 * 物品实例只能在注册事件里创建（没有 DeferredRegister 的惰性 supplier），
 * 所以 {@link #tool()} 在注册完成前是 null。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    /** 注册事件里才会被赋值。 */
    private static Item tool;

    public McVerMod() {
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener(McVerClient::onTick);
            LOGGER.info("mcver: 客户端 tick 已挂载（防务端加载：{}）", ModList.get() != null);
        }
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return tool;
    }

    /** 注册事件（1.14.2 没有 DeferredRegister，只能这么注册）。 */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class RegistryHandlers {

        private RegistryHandlers() { }

        @SubscribeEvent
        public static void onItems(RegistryEvent.Register<Item> e) {
            tool = new Item(new Item.Properties().stacksTo(1).tab(CreativeModeTab.TAB_TOOLS))
                    .setRegistryName(new ResourceLocation(MOD_ID, "download_tool"));
            e.getRegistry().register(tool);
            LOGGER.info("mcver: 物品已登记 {}", tool.getRegistryName());
        }
    }
}
