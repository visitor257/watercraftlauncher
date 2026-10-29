package dev.mcver;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * 1.11.2 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>签名是 {@code func_77659_a(ItemStack, World, EntityPlayer, EnumHand)}（1.9 ~ 1.11 这一代），
 * 1.12 才去掉第一个 {@code ItemStack} 参数。
 */
public class McVerItem extends Item {

    @Override
    public ActionResult<ItemStack> func_77659_a(ItemStack stack, World world, EntityPlayer player, EnumHand hand) {
        if (world.field_72995_K) {                      // isRemote：只在客户端开界面
            McVerMod.LOGGER.info("mcver: right-click open（右键打开启动器）");
            McVerClient.openMain();
        }
        return super.func_77659_a(stack, world, player, hand);
    }
}
