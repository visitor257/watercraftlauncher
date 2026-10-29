package dev.mcver;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

    public static final Item DOWNLOAD_TOOL = new McVerItem();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        DOWNLOAD_TOOL.func_77655_b("download_tool");            // setUnlocalizedName
        DOWNLOAD_TOOL.func_77625_d(1);                           // setMaxStackSize
        DOWNLOAD_TOOL.func_77637_a(CreativeTabs.field_78027_g);  // setCreativeTab(TOOLS)

        GameRegistry.registerItem(DOWNLOAD_TOOL, "download_tool");
        registerModel();

        registerRecipe();

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
            LOGGER.info("mcver: model registered");
        } catch (Throwable t) {
            LOGGER.warn("mcver: 模型登记失败 {}", t.toString());
        }
    }

    /**
     * 水桶 → 下载器的无序合成。
     *
     * <p>JSON 配方是 1.12 才有的，所以 1.11 及以下只能代码登记。
     * 登记完顺手扫一遍 {@code CraftingManager} 的列表，日志里直接给「真的在不在」——
     * 免得又出现「调了注册函数就当成功」这种事。
     */
    @SuppressWarnings("rawtypes")
    private static void registerRecipe() {
        try {
            net.minecraftforge.fml.common.registry.GameRegistry.addShapelessRecipe(
                    new ItemStack(DOWNLOAD_TOOL), net.minecraft.init.Items.field_151131_as);
            boolean found = false;
            for (Object o : net.minecraft.item.crafting.CraftingManager.func_77594_a().func_77592_b()) {
                net.minecraft.item.crafting.IRecipe r = (net.minecraft.item.crafting.IRecipe) o;
                ItemStack out = r.func_77571_b();
                if (out != null && out.func_77973_b() == DOWNLOAD_TOOL) { found = true; break; }
            }
            LOGGER.info("mcver: recipe download_tool=" + found);
        } catch (Throwable t) {
            LOGGER.warn("mcver: 配方登记失败 {}", t.toString());
        }
    }

    /** 物品本体。 */
    public static Item tool() {
        return DOWNLOAD_TOOL;
    }
}
