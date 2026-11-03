package net.phoenixvine.blueprint.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.Blueprint;

import java.util.function.Supplier;

/** Sent when the wand is left-clicked at nothing: undoes the last placement. (Left-clicking a block is caught on the server.) */
public record C2SShapeUndoPacket() {

    // Since the packet carries no data, encode and decode just handle an empty buffer
    public static void encode(C2SShapeUndoPacket packet, FriendlyByteBuf buf) {
        // No fields to write
    }

    public static C2SShapeUndoPacket decode(FriendlyByteBuf buf) {
        return new C2SShapeUndoPacket();
    }

    public static void handle(C2SShapeUndoPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            boolean holding = player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintWandItem ||
                    player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintWandItem;
            if (holding) ShapeActions.undo(player);
        });
        ctx.setPacketHandled(true);
    }
}