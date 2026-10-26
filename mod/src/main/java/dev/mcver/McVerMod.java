package dev.mcver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** 公共初始化：只干一件事——注册那个物品。 */
public class McVerMod implements ModInitializer {

    public static final String MOD_ID = "mcver";
    public static final dev.mcver.core.Log LOGGER = new dev.mcver.core.Log(MOD_ID);

    public static final Identifier ITEM_ID = Identifier.of(MOD_ID, "download_tool");

    public static final Item DOWNLOAD_TOOL = new Item(new Item.Settings()
            .registryKey(RegistryKey.of(RegistryKeys.ITEM, ITEM_ID))
            .maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, ITEM_ID, DOWNLOAD_TOOL);

        // 塞进创造模式「工具」栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(DOWNLOAD_TOOL));

        LOGGER.info("mcver: 物品已注册 {}", ITEM_ID);
    }
}
