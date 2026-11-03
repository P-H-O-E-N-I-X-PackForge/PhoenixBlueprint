package net.phoenixvine.blueprint.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.Blueprint;

import java.util.function.Supplier;

/**
 * Replaces the plan on the wand in the player's hand with a whole one (loaded from the plan library or pasted). The plan is
 * too big for the one-change edit packets, so it travels as its own tag; the server reads it with {@link ShapePlan#load},
 * which keeps every size, angle and offset within its limits, so what a file or a client sends cannot make a plan the
 * editor could not.
 */
public record C2SWandPlanPacket(CompoundTag plan) {

    public static void encode(C2SWandPlanPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.plan());
    }

    public static C2SWandPlanPacket decode(FriendlyByteBuf buf) {
        return new C2SWandPlanPacket(buf.readNbt());
    }

    /** Puts the plan on the wand stack: what the server does, and what the editor does to its own copy at once. */
    public static void apply(ItemStack stack, CompoundTag plan) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlueprintWandItem)) return;
        ShapePlan.load(plan).write(stack);
    }

    public static void handle(C2SWandPlanPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !ShapeActions.mayUse(player)) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlueprintWandItem) {
                    apply(stack, packet.plan());
                    return;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}