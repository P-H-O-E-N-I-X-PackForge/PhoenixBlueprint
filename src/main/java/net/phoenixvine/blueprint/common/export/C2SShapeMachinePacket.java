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
import net.phoenixvine.blueprint.common.ShapeActions;
import net.phoenixvine.blueprint.common.BlueprintWandItem;

import brachy.modularui.factory.PlayerInventoryUIFactory;

/**
 * A value picked in a screen of its own (a machine id typed in, a block or texture picked from a list): the screens replace the
 * editor, which closes it, so the value goes to the wand in a packet instead of through the editor, and the editor is opened
 * again afterwards.
 */
public record C2SShapeMachinePacket(int field, String value) implements CustomPacketPayload {

    public static final Type<C2SShapeMachinePacket> TYPE = new Type<>(Blueprint.id("blueprint_wand_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SShapeMachinePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SShapeMachinePacket::field, ByteBufCodecs.stringUtf8(256),
            C2SShapeMachinePacket::value, C2SShapeMachinePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SShapeMachinePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !ShapeActions.mayUse(player)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!(stack.getItem() instanceof BlueprintWandItem)) continue;
                if (packet.field() == MachineSettings.RENAME_STRUCTURE) {
                    WandSelection sel = WandSelection.read(stack);
                    sel.rename(packet.value().replaceAll("[^A-Za-z0-9_]", "_"));
                    sel.write(stack);
                } else {
                    MachineSettings settings = MachineSettings.read(stack);
                    if (settings.apply(packet.field(), packet.value())) settings.write(stack);
                }
                if (packet.field() != MachineSettings.DERIVED_CASING) {
                    PlayerInventoryUIFactory.INSTANCE.openFromHand(player, hand);
                }
                return;
            }
        });
    }
}
