package dev.mcver;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 1.8.9 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>1.8 的右键钩子还是「返回 ItemStack」的老形态：
 * {@code func_77659_a(ItemStack, World, EntityPlayer)}。{@code ActionResult} 是 1.9 才引入的，
 * 所以这里没有 {@code EnumHand} 参数，也没有成功/失败的包装。
 */
public class McVerItem extends Item {

    @Override
    public ItemStack func_77659_a(ItemStack stack, World world, EntityPlayer player) {
        if (world.field_72995_K) {                      // isRemote：只在客户端开界面
            McVerMod.LOGGER.info("mcver: right-click open（右键打开启动器）");
            McVerClient.openMain();
        }
        return super.func_77659_a(stack, world, player);
    }
}
