package dev.mcver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge 入口（MC 1.21.1 / NeoForge 21.1.x）。
 *
 * <p>对应 Fabric 版的 {@code ModInitializer}：注册「版本下载器」物品，并塞进创造模式「工具」栏。
 *
 * <p><b>为什么这里没有 {@code public static final Item DOWNLOAD_TOOL}：</b>
 * 1.21.1 的 {@code Item} 构造函数会往注册表里塞一个 intrusive holder，而 mod 类的静态初始化
 * 发生在注册表**冻结之后** —— 直接写静态字段就是
 * {@code IllegalStateException: Registry is already frozen}（实测第一次启动就崩在这）。
 * 所以物品必须在 {@link DeferredRegister} 的 supplier 里 new，运行时通过 {@link #tool()} 取。
 *
 * <p><b>为什么创造栏按名字认：</b>1.21.1 里 {@code CreativeModeTabs} 的那些 key 字段是 private
 * （1.20.x 还是 public），写字段名编不过；按注册名 {@code tools_and_utilities} 比对最稳。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredItem<Item> TOOL =
            ITEMS.register("download_tool", () -> new Item(new Item.Properties().stacksTo(1)));

    public McVerMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        // NeoForge 20.x 的 @EventBusSubscriber 各家小版本不一样（20.1 没 Bus 枚举、20.4 叫 FORGE 不叫 GAME），
        // 干脆显式注册到 game bus，跨 20.1/20.2/20.4 都一样。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(McVerClient::onTick);
        // BuildCreativeModeTabContentsEvent 挂在 **mod bus**（挂 game bus 会 IllegalArgumentException）
        modBus.addListener(ModEvents::creativeTabs);
        LOGGER.info("mcver: 物品已登记 {}", TOOL.getId());
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return TOOL.get();
    }

    /** 创造模式物品栏：塞进「工具与实用物品」那一栏。 */
    public static final class ModEvents {

        public static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
            ResourceLocation id = e.getTabKey().location();
            if (id.getPath().equals("tools_and_utilities")) {
                e.accept(new ItemStack(tool()));
            }
        }
    }
}
