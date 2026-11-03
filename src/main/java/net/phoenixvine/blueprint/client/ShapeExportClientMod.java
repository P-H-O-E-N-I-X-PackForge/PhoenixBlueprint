package net.phoenixvine.blueprint.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.blueprint.Blueprint;

/** Mod-bus client events for the wand. RegisterKeyMappingsEvent only fires on the mod bus, so it can't live in ShapeExportClient. */
@Mod.EventBusSubscriber(modid = Blueprint.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShapeExportClientMod {

    private ShapeExportClientMod() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ShapeExportClient.MODE_KEY);
        event.register(ShapeExportClient.CONTROLLER_KEY);
        event.register(ShapeExportClient.COPY_KEY);
        event.register(ShapeExportClient.LOCK_KEY);
    }
}
