package dev.mcver;

import dev.mcver.bridge.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * 1.12.2 的「版本下载器」物品：右键打开启动器界面。
 *
 * <p>右键钩子是 {@code Item#func_77659_a(World, EntityPlayer, EnumHand)}
 * （SRG 名，MCP 名 {@code onItemRightClick}）。父类已有默认实现，override 即可 ——
 * 不需要事件总线，也不会被原版 {@code keyUse} 的 {@code while (wasPressed())} 吃掉。
 *
 * <p><b>为什么直接 {@code return super...} 而不是自己造 {@code ActionResult}</b>：
 * 造对象要写 {@code EnumActionResult.field_xxxxx} 这种 SRG 字段名，而父类默认实现
 * 正好返回 {@code PASS} + 原栈，语义上「物品没被消耗、也没被拒绝」，正是要的。
 */
public class McVerItem extends Item {

    @Override
    public ActionResult<ItemStack> func_77659_a(World world, EntityPlayer player, EnumHand hand) {
        if (world.field_72995_K) {                      // isRemote：只在客户端开界面
            McVerMod.LOGGER.info("mcver: right-click open（右键打开启动器）");
            Minecraft.getInstance().execute(McVerClient::openMain);
        }
        return super.func_77659_a(world, player, hand);
    }
}
