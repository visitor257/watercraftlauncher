package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.21.1 兼容层。
 *
 * <p>与主源码（1.21.4）的差异只有一处：`Item.Settings.registryKey(...)` 是 1.21.2+ 才加的，
 * 1.21.1 里直接 `Registry.register` 就行（注册时会自动绑定 registry key）。
 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier ITEM_ID = Identifier.of(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new Item(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, ITEM_ID, DOWNLOAD_TOOL);

        // 塞进创造模式「工具」栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(DOWNLOAD_TOOL));

        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
