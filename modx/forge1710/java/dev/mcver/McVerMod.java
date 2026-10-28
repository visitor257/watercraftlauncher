package dev.mcver;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

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
        acceptedMinecraftVersions = "[1.7.10]"
)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    /** 物品本身。贴图名只能在子类里设（setTextureName 是 protected）。 */
    public static final Item DOWNLOAD_TOOL = new ToolItem();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        GameRegistry.registerItem(DOWNLOAD_TOOL, "download_tool");
        McVerClient.install();
        LOGGER.info("mcver: 物品已登记 {}，客户端 tick 已挂载", DOWNLOAD_TOOL.func_77658_a());
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
    }
}
