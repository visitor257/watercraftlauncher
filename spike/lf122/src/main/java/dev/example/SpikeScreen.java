package dev.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.itemgroup.ItemGroup;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

// Spike：把 Water Craft Launcher 在 1.12.2 上真正依赖的那几样东西各试一遍。
// 编译过 = 这些 API 在 Legacy Fabric 1.12.2 上有等价物（可能换名字/换签名）。
public class SpikeScreen extends Screen {

    // 1) 物品注册：现代是 Registry.register(Registries.ITEM, id, item)；1.12.2 是 Item.REGISTRY.put(id, item)
    public static final Item TOOL = new Item();

    public static void registerItem() {
        TOOL.setItemGroup(ItemGroup.MISC);
        Item.REGISTRY.put(new Identifier("spike", "tool"), TOOL);
    }

    // 2) Screen：1.12.2 的构造器不要标题参数
    public SpikeScreen() {
        super();
    }

    @Override
    public void init() {
        super.init();
        this.addButton(new ButtonWidget(0, this.width / 2 - 60, this.height / 2, 120, 20, "click"));
        this.addButton(new ButtonWidget(1, this.width / 2 - 60, this.height / 2 + 24, 120, 20, "second"));
    }

    // 3) 按键点击：现代是 widget 自己 onPress；1.12.2 由 Screen.buttonClicked 分派
    @Override
    protected void buttonClicked(ButtonWidget b) {
        if (b.id == 0) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        // 1.12.2 的 Screen.render 不会自己画背景，得手动调（和 1.20.1/1.19.4 一样）
        this.renderBackground();
        // 4) Ctx.fill
        DrawableHelper.fill(10, 10, this.width - 10, this.height - 10, 0xFFC6C6C6);
        // 5) Ctx.drawText（1.12.2 直接吃 String）
        this.textRenderer.draw("watercraft launcher on 1.12.2", 20, 20, 0x404040);
        // 6) Ctx.drawItem：方法名在 legacy yarn 里还没映射（method_12455 = 老 renderItemAndEffectIntoGUI）
        this.itemRenderer.method_12455(new ItemStack(TOOL), 20, 40);
        super.render(mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground() {
        DrawableHelper.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        MinecraftClient.getInstance().setScreen(null);
    }

    // 7) Text 体系：现代 MutableText；1.12.2 是 LiteralText（够用）
    public static String title() {
        return new LiteralText("spike").asFormattedString();
    }
}
