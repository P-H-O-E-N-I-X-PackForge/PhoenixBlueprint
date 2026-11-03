package net.phoenixvine.blueprint.common.export;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.common.BlueprintWandItem;

import java.util.function.Supplier;

/** What the wand's keys (mode, controller, copy) ask the server to do to the wand in the player's hand. */
public record C2SBlueprintWandActionPacket(int action) {

    // Encoder (writes data into the buffer)
    public static void encode(C2SBlueprintWandActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action());
    }

    // Decoder (reads data from the buffer)
    public static C2SBlueprintWandActionPacket decode(FriendlyByteBuf buf) {
        return new C2SBlueprintWandActionPacket(buf.readVarInt());
    }

    // Message handler executed on the server thread
    public static void handle(C2SBlueprintWandActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    ShapeExportActions.apply(player, stack, packet.action());
                    return;
                }
            }
        });
        context.setPacketHandled(true);
    }
}