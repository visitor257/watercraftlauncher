package dev.mcver;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Forge 入口（MC 1.12.2 / Forge 14.23.x，SRG 命名空间）。
 *
 * <p>这一代的注册完全是另一套：
 * <ul>
 *   <li>没有 {@code DeferredRegister}，直接 {@code ForgeRegistries.ITEMS.register(...)}；</li>
 *   <li>没有 {@code Item.Properties}，是 setter 链：{@code func_77655_b}（setUnlocalizedName）、
 *       {@code func_77625_d}（setMaxStackSize）、{@code func_77637_a}（setCreativeTab）；</li>
 *   <li>mod 描述在 <b>{@code mcmod.info}</b>，不是 {@code mods.toml}。</li>
 * </ul>
 *
 * <p><b>注册时的强转为什么要经 {@code Object}</b>：原版 jar 里的 {@code Item} 并不
 * {@code implements IForgeRegistryEntry} —— 那是 Forge 用 binpatch 在运行期加上去的，
 * 编译期 javac 看不到，所以直接 {@code (IForgeRegistryEntry) item} 会被拒。
 */
@Mod(
        modid = McVerMod.MOD_ID,
        name = "Water Craft Launcher",
        version = "1.0.0",
        acceptedMinecraftVersions = "[1.12,1.13)",
        clientSideOnly = true
)
public class McVerMod {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Item DOWNLOAD_TOOL = new McVerItem();

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

    /**
     * 把物品指向它自己的模型。
     *
     * <p><b>1.11 / 1.12 不会自动找 {@code models/item/<名字>.json}</b>，不做这一步物品就是
     * 紫黑格「missing model」——用户报的「图标（水）没了」就是这个。1.10.2 那份一直有，
     * 1.11/1.12 这份当时漏了。
     */
    private static void registerModel() {
        try {
            net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                    DOWNLOAD_TOOL, 0,
                    new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                            MOD_ID + ":download_tool", "inventory"));
            LOGGER.info("mcver: model registered");
        } catch (Throwable t) {
            LOGGER.warn("mcver: 模型登记失败 {}", t.toString());
        }
    }

    /** 物品本体（注册完成后才非 null）。 */
    public static Item tool() {
        return DOWNLOAD_TOOL;
    }
}
