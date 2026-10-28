package dev.mcver;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Forge 入口（MC 1.8.9 / Forge 11.15.x，SRG 命名空间）。
 *
 * <p>1.8.9 比 1.9.4 还少一层东西：
 * <ul>
 *   <li><b>没有 {@code ForgeRegistries} / {@code IForgeRegistry}</b>（1.9 才有）→
 *       老接口 {@code GameRegistry.registerItem(item, name)}；</li>
 *   <li>模型类在 {@code net.minecraft.client.resources.model}（1.9 才挪到
 *       {@code client.renderer.block.model}）；</li>
 *   <li>文本是 {@code IChatComponent}（{@code net.minecraft.util.text} 是 1.9 才有）。</li>
 * </ul>
 */
@Mod(
        modid = McVerMod.MOD_ID,
        name = "Water Craft Launcher",
        version = "1.0.0",
        acceptedMinecraftVersions = "[1.8.9,1.9)",
        clientSideOnly = true
)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Item DOWNLOAD_TOOL = new Item();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        DOWNLOAD_TOOL.func_77655_b("download_tool");            // setUnlocalizedName
        DOWNLOAD_TOOL.func_77625_d(1);                           // setMaxStackSize
        DOWNLOAD_TOOL.func_77637_a(CreativeTabs.field_78027_g);  // setCreativeTab(TOOLS)

        GameRegistry.registerItem(DOWNLOAD_TOOL, "download_tool");
        registerModel();

        McVerClient.install();
        LOGGER.info("mcver: 物品已登记 {}，客户端 tick 已挂载", DOWNLOAD_TOOL.func_77658_a());
    }

    /** 1.8.9 也要显式指模型（自动按注册名找模型是 1.11 起）。 */
    private static void registerModel() {
        try {
            net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                    DOWNLOAD_TOOL, 0,
                    new net.minecraft.client.resources.model.ModelResourceLocation(
                            MOD_ID + ":download_tool", "inventory"));
        } catch (Throwable t) {
            LOGGER.warn("mcver: 模型登记失败 {}", t.toString());
        }
    }

    /** 物品本体。 */
    public static Item tool() {
        return DOWNLOAD_TOOL;
    }
}
