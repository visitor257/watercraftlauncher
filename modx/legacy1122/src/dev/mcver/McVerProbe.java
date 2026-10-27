// 1.12.2 Legacy Fabric 的最小探针：只做两件事 —— 证明 jar 能被 loader 加载、
// 证明我们编出来的类在运行时能被 remap 成 obf 名并正常执行。
package dev.mcver;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.itemgroup.ItemGroup;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

public class McVerProbe implements ClientModInitializer {

    public static final Identifier ID = new Identifier("mcver", "probe");

    @Override
    public void onInitializeClient() {
        // 1) 注册一个物品（1.12 是 Item.REGISTRY + 无参 Item 构造函数）
        Item item = new Item();
        Item.REGISTRY.put(ID, item);
        System.out.println("MCVER1122 PROBE registered item=" + Item.REGISTRY.get(ID));

        // 2) 碰一下 UI 侧的类，确认这些符号在运行时真能解析
        MinecraftClient mc = MinecraftClient.getInstance();
        System.out.println("MCVER1122 PROBE client=" + (mc != null)
                + " screenClass=" + Screen.class.getName()
                + " text=" + new LiteralText("ok").asFormattedString()
                + " group=" + (ItemGroup.TOOLS != null)
                + " stack=" + new ItemStack(item).getCount());
        System.out.println("MCVER1122 PROBE OK");
    }
}
