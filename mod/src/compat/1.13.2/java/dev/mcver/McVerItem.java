package dev.mcver;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 1.13.2 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>1.13 里右键钩子还是 {@code Item#method_13649(World, PlayerEntity, Hand)}（yarn 没命名，
 * obf 就是 onItemRightClick）；父类已有默认实现，override 即可 —— 不需要 Mixin，
 * 也就绕开了 Legacy Fabric 下 mixin refmap 的麻烦。
 */
public class McVerItem extends Item {

    public McVerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> method_13649(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            McVerMod.LOGGER.info("mcver: 右键打开启动器");
            dev.mcver.core.Ui.exec(MinecraftClient.getInstance(), McVerClient::openMain);
        }
        return new TypedActionResult<>(ActionResult.SUCCESS, stack);
    }
}
