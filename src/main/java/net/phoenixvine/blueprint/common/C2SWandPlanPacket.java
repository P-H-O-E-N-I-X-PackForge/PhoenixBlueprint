package net.phoenixvine.blueprint.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;

/**
 * Replaces the plan on the wand in the player's hand with a whole one (loaded from the plan library or pasted). The plan is
 * too big for the one-change edit packets, so it travels as its own tag; the server reads it with {@link ShapePlan#load},
 * which keeps every size, angle and offset within its limits, so what a file or a client sends cannot make a plan the
 * editor could not.
 */
public record C2SWandPlanPacket(CompoundTag plan) implements CustomPacketPayload {

    public static final Type<C2SWandPlanPacket> TYPE = new Type<>(Blueprint.id("wand_plan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SWandPlanPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, C2SWandPlanPacket::plan, C2SWandPlanPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Puts the plan on the wand stack: what the server does, and what the editor does to its own copy at once. */
    public static void apply(ItemStack stack, CompoundTag plan) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlueprintWandItem)) return;
        ShapePlan.load(plan).write(stack);
    }

    public static void handle(C2SWandPlanPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !ShapeActions.mayUse(player)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    apply(stack, packet.plan());
                    return;
                }
            }
        });
    }
}
