package net.phoenixvine.blueprint.common.export;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.ShapeActions;
import net.phoenixvine.blueprint.common.ShapePlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds an imported pattern: the pattern text (the server reads it again with its own registries), where the controller goes
 * and which way it faces. Undone with the wand's left-click like any other build.
 */
public record C2SImportPlacePacket(String text, BlockPos anchor, int front) implements CustomPacketPayload {

    public static final Type<C2SImportPlacePacket> TYPE = new Type<>(Blueprint.id("import_place"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SImportPlacePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32767), C2SImportPlacePacket::text, BlockPos.STREAM_CODEC,
            C2SImportPlacePacket::anchor, ByteBufCodecs.VAR_INT, C2SImportPlacePacket::front, C2SImportPlacePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SImportPlacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !ShapeActions.mayUse(player)) return;
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
    }
}
