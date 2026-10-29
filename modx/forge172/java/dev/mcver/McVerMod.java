package dev.mcver;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Forge 入口（MC 1.7.10 / Forge 10.13.x，SRG 命名空间）。
 *
 * <p>这一代又比 1.12.2 老一层：
 * <ul>
 *   <li>FML 在 <b>{@code cpw.mods.fml.*}</b>（1.8 起才搬到 {@code net.minecraftforge.fml}）；</li>
 *   <li>注册是 {@code GameRegistry.registerItem(item, name)}，没有 registry 对象；</li>
 *   <li>物品贴图靠 {@code setTextureName}（protected）→ 得用子类；
 *       路径是 {@code textures/items/<name>.png}（复数）；</li>
 *   <li>{@code @Mod} 没有 {@code clientSideOnly} 这个成员（1.12.2 那份有）。</li>
 * </ul>
 */
@Mod(
        modid = McVerMod.MOD_ID,
        name = "Water Craft Launcher",
        version = "1.0.0",
        acceptedMinecraftVersions = "[1.7.2]"
)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    /** 物品本身。贴图名只能在子类里设（setTextureName 是 protected）。 */
    public static final Item DOWNLOAD_TOOL = new ToolItem();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        GameRegistry.registerItem(DOWNLOAD_TOOL, "download_tool");
        registerRecipe();
        McVerClient.install();
        LOGGER.info("mcver: 物品已登记 {}，客户端 tick 已挂载", DOWNLOAD_TOOL.func_77658_a());
    }

    /**
     * 水桶 → 下载器的无序合成。
     *
     * <p>1.7 连 JSON 配方都没有，只能代码登记；登记完扫一遍 {@code CraftingManager}
     * 的列表并把结果打进日志，别拿「调过注册函数」当成功。
     */
    @SuppressWarnings("rawtypes")
    private static void registerRecipe() {
        try {
            GameRegistry.addShapelessRecipe(new ItemStack(DOWNLOAD_TOOL),
                    net.minecraft.init.Items.field_151131_as);
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

    /** 带贴图的最小物品。 */
    private static final class ToolItem extends Item {

        ToolItem() {
            func_77655_b("download_tool");            // setUnlocalizedName
            func_77625_d(1);                          // setMaxStackSize
            func_77637_a(CreativeTabs.field_78027_g); // setCreativeTab(TOOLS)
            func_111206_d("mcver:download_tool");     // setTextureName → textures/items/download_tool.png

        }

        /** 右键打开启动器（1.7 的钩子还是「返回 ItemStack」的老形态，没有 EnumHand）。 */
        @Override
        public net.minecraft.item.ItemStack func_77659_a(
                net.minecraft.item.ItemStack stack,
                net.minecraft.world.World world,
                net.minecraft.entity.player.EntityPlayer player) {
            if (world.field_72995_K) {                 // isRemote：只在客户端开界面
                LOGGER.info("mcver: right-click open（右键打开启动器）");
                McVerClient.openMain();
            }
            return super.func_77659_a(stack, world, player);
        }
    }
}
