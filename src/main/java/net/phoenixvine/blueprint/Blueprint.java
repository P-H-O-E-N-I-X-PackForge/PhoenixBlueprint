package net.phoenixvine.blueprint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.network.BlueprintNetwork;

/**
 * Phoenix Blueprint: design multiblock shapes, build them with the wand, and export the finished machine as GregTech KubeJS or
 * Java (patterns, substructures, or the whole machine definition).
 */
@Mod(Blueprint.MOD_ID)
public class Blueprint {

    public static final String MOD_ID = "phoenix_blueprint";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    // 1.20.1 uses Forge Registries DeferredRegister with RegistryObject
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<BlueprintWandItem> WAND = ITEMS.register("blueprint_wand",
            () -> new BlueprintWandItem(new Item.Properties().stacksTo(1)));

    public Blueprint() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);
        modBus.addListener(this::commonSetup);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // Initialize and register your SimpleChannel network packets for 1.20.1 Forge
        event.enqueueWork(BlueprintNetwork::registerPackets);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WAND.get());
        }
    }
}