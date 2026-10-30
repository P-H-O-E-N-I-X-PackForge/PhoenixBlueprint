package net.phoenixvine.blueprint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.C2SShapeUndoPacket;
import net.phoenixvine.blueprint.common.export.C2SBlueprintWandActionPacket;
import net.phoenixvine.blueprint.common.export.C2SShapeMachinePacket;
import net.phoenixvine.blueprint.common.export.S2CShapeExportPacket;

/**
 * Phoenix Blueprint: design multiblock shapes, build them with the wand, and export the finished machine as GregTech KubeJS or
 * Java (patterns, substructures, or the whole machine definition).
 */
@Mod(Blueprint.MOD_ID)
public class Blueprint {

    public static final String MOD_ID = "phoenix_blueprint";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<BlueprintWandItem> WAND = ITEMS.register("blueprint_wand",
            () -> new BlueprintWandItem(new Item.Properties().stacksTo(1).attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.BLOCK_INTERACTION_RANGE,
                            new AttributeModifier(id("blueprint_wand_reach"), BlueprintWandItem.EXTRA_REACH,
                                    AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.HAND)
                    .build())));

    public Blueprint(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(Blueprint::registerPayloads);
        modBus.addListener(Blueprint::addToCreativeTab);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(C2SShapeUndoPacket.TYPE, C2SShapeUndoPacket.STREAM_CODEC, C2SShapeUndoPacket::handle);
        registrar.playToServer(C2SBlueprintWandActionPacket.TYPE, C2SBlueprintWandActionPacket.STREAM_CODEC,
                C2SBlueprintWandActionPacket::handle);
        registrar.playToServer(C2SShapeMachinePacket.TYPE, C2SShapeMachinePacket.STREAM_CODEC,
                C2SShapeMachinePacket::handle);
        registrar.playToClient(S2CShapeExportPacket.TYPE, S2CShapeExportPacket.STREAM_CODEC, S2CShapeExportPacket::handle);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(WAND.get());
    }
}
