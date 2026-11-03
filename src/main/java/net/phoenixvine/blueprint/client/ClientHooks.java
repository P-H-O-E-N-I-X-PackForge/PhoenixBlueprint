package net.phoenixvine.blueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.client.gui.BlueprintScreen;

/** Client-only entry points the common item code calls, and the server's action-bar messages routed into the editor. */
@Mod.EventBusSubscriber(modid = Blueprint.MOD_ID, value = Dist.CLIENT)
public final class ClientHooks {

    private ClientHooks() {}

    public static void openEditor(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new BlueprintScreen(hand));
    }

    /**
     * The server answers with action-bar messages ("Copied blocks.", "Nothing built yet to select."), which the open editor
     * would hide behind itself, so they are shown in its footer too.
     */
    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BlueprintScreen editor) editor.serverMessage(event.getMessage().getString());
    }
}
