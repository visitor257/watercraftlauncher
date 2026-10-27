package dev.mcver;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 1.8.x 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>1.8 是一路退到最老的那套右键钩子：{@code Item#onStartUse(ItemStack, World, PlayerEntity)}，
 * <b>返回 ItemStack，没有 TypedActionResult、也没有 Hand 参数</b>（1.9 起才有 Hand 和
 * {@code method_11373}）。父类有默认实现，override 即可。
 */
public class McVerItem extends Item {

    @Override
    public ItemStack onStartUse(ItemStack heldStack, World world, PlayerEntity player) {
        if (world.isClient) {
            McVerMod.LOGGER.info("mcver: 右键打开启动器");
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute((java.util.concurrent.Callable<Void>) () -> {
                McVerClient.openMain();
                return null;
            });
        }
        return heldStack;
    }
}
