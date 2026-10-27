package dev.mcver;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 1.12.2 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>1.12 里右键钩子是 {@code Item#method_13649(World, PlayerEntity, Hand)}
 * （yarn 没给名字，obf 就是 onItemRightClick）。父类已有默认实现，这里 override 即可 ——
 * <b>不需要 Mixin</b>，也就绕开了 1.12 上 mixin 的 refmap 麻烦。
 */
public class McVerItem extends Item {

    @Override
    public TypedActionResult<ItemStack> method_13649(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            McVerMod.LOGGER.info("mcver: 右键打开启动器");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute((java.util.concurrent.Callable<Void>) () -> {
                McVerClient.openMain();
                return null;
            });
        }
        return new TypedActionResult<>(net.minecraft.util.ActionResult.SUCCESS, stack);
    }
}
