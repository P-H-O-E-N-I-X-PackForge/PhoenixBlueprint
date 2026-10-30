package net.phoenixvine.blueprint.common.export;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.BlueprintWandItem;

/** What the wand's keys (mode, controller, copy) ask the server to do to the wand in the player's hand. */
public record C2SBlueprintWandActionPacket(int action) implements CustomPacketPayload {

    public static final Type<C2SBlueprintWandActionPacket> TYPE = new Type<>(Blueprint.id("blueprint_wand_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBlueprintWandActionPacket> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.VAR_INT, C2SBlueprintWandActionPacket::action, C2SBlueprintWandActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBlueprintWandActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    ShapeExportActions.apply(player, stack, packet.action());
                    return;
                }
            }
        });
    }
}
