package dev.mcver;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 1.9~1.10 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>这一代的右键钩子是 {@code Item#method_11373(ItemStack, World, PlayerEntity, Hand)}——
 * 和 1.11/1.12 的 {@code method_13649(World, PlayerEntity, Hand)} 比，
 * <b>多一个 ItemStack 且参数顺序不同</b>（1.11 起把 stack 挪到 player 里自己取）。
 * 父类有默认实现，override 即可，不需要 Mixin。
 */
public class McVerItem extends Item {

    @Override
    public TypedActionResult<ItemStack> method_11373(ItemStack heldStack, World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            McVerMod.LOGGER.info("mcver: 右键打开启动器");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute((java.util.concurrent.Callable<Void>) () -> {
                McVerClient.openMain();
                return null;
            });
        }
        return new TypedActionResult<>(net.minecraft.util.ActionResult.SUCCESS, heldStack);
    }
}
