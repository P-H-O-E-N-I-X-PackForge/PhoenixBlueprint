package net.phoenixvine.blueprint.common;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.Blueprint;

import java.util.function.Supplier;

/** One editor change to the wand in the player's hand: see {@link WandEdits}. */
public record C2SWandEditPacket(int op, byte[] data) {

    // Encoder for FriendlyByteBuf
    public static void encode(C2SWandEditPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.op());
        buf.writeByteArray(packet.data());
    }

    // Decoder for FriendlyByteBuf
    public static C2SWandEditPacket decode(FriendlyByteBuf buf) {
        int op = buf.readVarInt();
        byte[] data = buf.readByteArray(512); // Respects your max length constraint
        return new C2SWandEditPacket(op, data);
    }

    // Server-side network handler using 1.20.1 NetworkEvent.Context
    public static void handle(C2SWandEditPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    WandEdits.apply(player, stack, packet.op(), new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.data())));
                    return;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}