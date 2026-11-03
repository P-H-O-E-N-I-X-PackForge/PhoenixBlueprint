package net.phoenixvine.blueprint.common.export;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.ShapeActions;
import net.phoenixvine.blueprint.common.ShapePlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds an imported pattern: the pattern text (the server reads it again with its own registries), where the controller goes
 * and which way it faces. Undone with the wand's left-click like any other build.
 */
public record C2SImportPlacePacket(String text, BlockPos anchor, int front) {

    public static void encode(C2SImportPlacePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text(), 32767);
        buf.writeBlockPos(packet.anchor());
        buf.writeVarInt(packet.front());
    }

    public static C2SImportPlacePacket decode(FriendlyByteBuf buf) {
        String text = buf.readUtf(32767);
        BlockPos anchor = buf.readBlockPos();
        int front = buf.readVarInt();
        return new C2SImportPlacePacket(text, anchor, front);
    }

    public static void handle(C2SImportPlacePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !ShapeActions.mayUse(player)) return;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!(stack.getItem() instanceof BlueprintWandItem)) continue;
                Direction front = Direction.from3DDataValue(packet.front());
                ImportResolver.Placement placement = ImportResolver.resolve(packet.text());
                Long2ReferenceMap<BlockState> offsets = placement.offsets(front);
                // the controller's spot is only a mark in the hologram: the machine itself is placed by hand
                offsets.remove(BlockPos.asLong(0, 0, 0));

                Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
                for (var entry : offsets.long2ReferenceEntrySet()) {
                    long key = entry.getLongKey();
                    cells.put(packet.anchor().offset(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key)),
                            entry.getValue());
                }
                if (cells.isEmpty()) {
                    player.displayClientMessage(Component.literal("Nothing in the import could be built.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                ShapeActions.placeStates(player, cells, ShapePlan.read(stack).overwrite);
                return;
            }
        });
        ctx.setPacketHandled(true);
    }
}