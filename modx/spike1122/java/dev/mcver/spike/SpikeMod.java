package dev.mcver.spike;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * 1.12.2 可行性 spike，验三件事：
 *
 * <ol>
 *   <li>mod jar 能装进 Forge 1.12.2 并被加载（{@code mcmod.info} + {@code @Mod}）；</li>
 *   <li>编译用的是 **SRG jar**（成员名 {@code func_/field_}），说明「SRG 编 → 不 reobf」这条路成立；</li>
 *   <li>mod jar 里的 {@code net.minecraft.text.Text} 假名字类能被加载 —— 垫片方案的前提。</li>
 * </ol>
 *
 * <p><b>为什么注册要绕这么一圈</b>：原版 jar 里的 {@code Item} 并**不**实现
 * {@code IForgeRegistryEntry}（那是 Forge 用 binpatch 在运行期加的），所以
 * {@code ForgeRegistries.ITEMS.register(item)} 编译期过不了类型约束。用 raw type 硬怼过去，
 * 运行期类已被补丁过，一切正常。
 */
@Mod(modid = SpikeMod.MODID, name = "WCL spike", version = "1.0.0", acceptedMinecraftVersions = "[1.12.2]")
public class SpikeMod {

    public static final String MODID = "mcver";

    @Mod.EventHandler
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void init(FMLInitializationEvent e) {
        // 验收标记（ProbeLaunch 的 waitFor 用这一行）
        System.out.println("mcver: spike shim=" + net.minecraft.text.Text.literal("ok")
                + " shimClass=" + net.minecraft.text.Text.class.getName()
                + " loader=" + this.getClass().getClassLoader().getClass().getName());

        Item tool = new Item();
        // 经 Object 中转才能把 Item 强转成 IForgeRegistryEntry：两者编译期没关系
        // （Item implements IForgeRegistryEntry 是 Forge 的 binpatch 在运行期加上的）。
        IForgeRegistryEntry entry = (IForgeRegistryEntry) (Object) tool;
        entry.setRegistryName(new ResourceLocation("mcver", "download_tool"));
        ((IForgeRegistry) ForgeRegistries.ITEMS).register(entry);
        System.out.println("mcver: spike registered=" + entry.getRegistryName());
    }
}
