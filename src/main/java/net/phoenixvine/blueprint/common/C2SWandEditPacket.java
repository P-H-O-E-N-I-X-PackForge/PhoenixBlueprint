package net.phoenixvine.blueprint.common;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;

/** One editor change to the wand in the player's hand: see {@link WandEdits}. */
public record C2SWandEditPacket(int op, byte[] data) implements CustomPacketPayload {

    public static final Type<C2SWandEditPacket> TYPE = new Type<>(Blueprint.id("wand_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SWandEditPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SWandEditPacket::op, ByteBufCodecs.byteArray(512), C2SWandEditPacket::data,
            C2SWandEditPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SWandEditPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    WandEdits.apply(player, stack, packet.op(), new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.data())));
                    return;
                }
            }
        });
    }
}
