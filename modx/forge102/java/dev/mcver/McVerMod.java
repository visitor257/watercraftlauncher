package dev.mcver;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.IForgeRegistry;
import net.minecraftforge.fml.common.registry.IForgeRegistryEntry;

/**
 * Forge 入口（MC 1.10.2 / Forge 12.18.3，SRG 命名空间）。
 *
 * <p>与 1.12.2 的差别只有三处：
 * <ul>
 *   <li>{@code IForgeRegistry} / {@code IForgeRegistryEntry} 还在
 *       <b>{@code net.minecraftforge.fml.common.registry}</b>（1.12 才挪到
 *       {@code net.minecraftforge.registries}）；</li>
 *   <li>物品模型要显式登记（1.11 起才会按注册名自动找 {@code models/item/<name>.json}）；</li>
 *   <li>贴图仍在 {@code textures/items/}（复数）。</li>
 * </ul>
 */
@Mod(
        modid = McVerMod.MOD_ID,
        name = "Water Craft Launcher",
        version = "1.0.0",
        acceptedMinecraftVersions = "[1.10.2,1.11)",
        clientSideOnly = true
)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Item DOWNLOAD_TOOL = new Item();

    @Mod.EventHandler
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void preInit(FMLPreInitializationEvent e) {
        DOWNLOAD_TOOL.func_77655_b("download_tool");            // setUnlocalizedName
        DOWNLOAD_TOOL.func_77625_d(1);                           // setMaxStackSize
        DOWNLOAD_TOOL.func_77637_a(CreativeTabs.field_78027_g);  // setCreativeTab(TOOLS)

        IForgeRegistryEntry entry = (IForgeRegistryEntry) (Object) DOWNLOAD_TOOL;
        entry.setRegistryName(new ResourceLocation(MOD_ID, "download_tool"));
        ((IForgeRegistry) ForgeRegistries.ITEMS).register(entry);

        registerModel();

        McVerClient.install();
        LOGGER.info("mcver: 物品已登记 {}，客户端 tick 已挂载", entry.getRegistryName());
    }

    /** 1.10.2 必须显式把物品指向它的模型，否则图标是紫黑块。 */
    private static void registerModel() {
        try {
            net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                    DOWNLOAD_TOOL, 0,
                    new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                            MOD_ID + ":download_tool", "inventory"));
        } catch (Throwable t) {
            LOGGER.warn("mcver: 模型登记失败 {}", t.toString());
        }
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return DOWNLOAD_TOOL;
    }
}
