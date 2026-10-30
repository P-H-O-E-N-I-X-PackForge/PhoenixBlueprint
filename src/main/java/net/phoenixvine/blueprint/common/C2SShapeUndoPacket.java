package net.phoenixvine.blueprint.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;

/** Sent when the wand is left-clicked at nothing: undoes the last placement. (Left-clicking a block is caught on the server.) */
public record C2SShapeUndoPacket() implements CustomPacketPayload {

    public static final Type<C2SShapeUndoPacket> TYPE = new Type<>(Blueprint.id("blueprint_wand_undo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SShapeUndoPacket> STREAM_CODEC = StreamCodec
            .unit(new C2SShapeUndoPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SShapeUndoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            boolean holding = player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintWandItem ||
                    player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintWandItem;
            if (holding) ShapeActions.undo(player);
        });
    }
}
