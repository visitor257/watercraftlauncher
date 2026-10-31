package dev.mcver;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge 入口（MC 1.21.5 起 / NeoForge 21.5+）。
 *
 * <p><b>和 Forge 那代不一样的地方</b>：Forge 1.21.5 把整套事件总线换成了
 * {@code eventbus.api.bus.BusGroup}（见 {@code modx/forge215}），但 **NeoForge 没有跟着换** ——
 * 21.5 的 {@code net.neoforged.bus} 里仍然只有 {@code IEventBus}（javap 验过：
 * {@code FMLJavaModLoadingContext.getModEventBus()} 还在，bus 8.0.x 里没有 BusGroup）。
 * 所以这一代的 NeoForge 入口和 21.1 基本一样，唯一要动的是下面那条 setId。
 *
 * <p><b>{@code setId} 是硬性要求</b>：MC 1.21.2 起 {@code Item.Properties#setId} 必须显式给，
 * 不设就在注册时 {@code NullPointerException: Item id not set}（Forge 1.21.3 那次崩的就是这个）。
 * 21.1 的 overlay 不能调它（那代没这个方法），所以 21.2 之后每个世代都要自己补。
 */
@Mod(McVerMod.MOD_ID)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredItem<Item> TOOL = ITEMS.register("download_tool",
            () -> new Item(new Item.Properties().stacksTo(1).setId(
                    ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "download_tool")))));

    public McVerMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        LOGGER.info("mcver: 物品已登记 {}", TOOL.getId());
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return TOOL.get();
    }

    /** 创造模式物品栏：塞进「工具与实用物品」那一栏。 */
    @EventBusSubscriber(modid = MOD_ID)
    public static final class ModEvents {

        @SubscribeEvent
        public static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
            ResourceLocation id = e.getTabKey().location();
            if (id.getPath().equals("tools_and_utilities")) {
                e.accept(new ItemStack(tool()));
            }
        }
    }
}
