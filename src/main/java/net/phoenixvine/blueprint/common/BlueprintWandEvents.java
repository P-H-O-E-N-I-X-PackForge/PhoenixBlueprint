package net.phoenixvine.blueprint.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.phoenixvine.blueprint.Blueprint;

/** Left-clicking a block with the wand undoes the last placement instead of breaking the block. */
@EventBusSubscriber(modid = Blueprint.MOD_ID)
public final class BlueprintWandEvents {

    private BlueprintWandEvents() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof BlueprintWandItem)) return;
        event.setCanceled(true);
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) ShapeActions.undo(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ShapeActions.forget(event.getEntity().getUUID());
    }

    /** Whether the player has the wand in either hand. */
    public static boolean holdsWand(net.minecraft.world.entity.player.Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintWandItem ||
                player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintWandItem;
    }
}
