package net.phoenixvine.blueprint.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.blueprint.Blueprint;

import java.util.UUID;

/** Left-clicking a block with the wand undoes the last placement instead of breaking the block. */
@Mod.EventBusSubscriber(modid = Blueprint.MOD_ID)
public final class BlueprintWandEvents {

    private static final UUID REACH_MODIFIER_ID = UUID.fromString("8c512345-1234-1234-1234-123456789abc");

    private BlueprintWandEvents() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof BlueprintWandItem)) return;
        event.setCanceled(true);

        // In 1.20.1 Forge, LeftClickBlock handles the initial interaction directly
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ShapeActions.undo(player);
        }
    }

    /**
     * Adds {@link BlueprintWandItem#EXTRA_REACH} to the player's block reach while the wand is in either hand. It runs on both
     * sides: the client uses the reach to aim (and so to place the hologram) and the server uses it to accept the click.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        AttributeInstance reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (reach == null) return;

        boolean holding = holdsWand(player);
        boolean applied = reach.getModifier(REACH_MODIFIER_ID) != null;
        if (holding && !applied) {
            reach.addTransientModifier(new AttributeModifier(REACH_MODIFIER_ID, "Blueprint wand reach",
                    BlueprintWandItem.EXTRA_REACH, AttributeModifier.Operation.ADDITION));
        } else if (!holding && applied) {
            reach.removeModifier(REACH_MODIFIER_ID);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ShapeActions.forget(event.getEntity().getUUID());
    }

    /** Whether the player has the wand in either hand. */
    public static boolean holdsWand(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintWandItem ||
                player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintWandItem;
    }
}