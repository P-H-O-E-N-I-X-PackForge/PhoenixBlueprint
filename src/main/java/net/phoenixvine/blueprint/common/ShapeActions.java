package net.phoenixvine.blueprint.common;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** What the blueprint wand does on the server: builds the plan out of the block in the other hand, and undoes it. */
public final class ShapeActions {

    private static final int UNDO_DEPTH = 8;

    /** What a placement replaced, so it can be put back. */
    private record Undo(ResourceKey<Level> dimension, Map<Long, BlockState> before) {}

    private static final Map<UUID, Deque<Undo>> HISTORY = new HashMap<>();

    private ShapeActions() {}

    /** Whether this player may use the wand: it builds without cost, so creative mode or operator permission. */
    public static boolean mayUse(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    /** The block to build with: the block item in the hand that does not hold the wand. */
    public static BlockState blockFor(ServerPlayer player, InteractionHand wandHand) {
        ItemStack other = player.getItemInHand(wandHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND :
                InteractionHand.MAIN_HAND);
        return other.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
    }

    public static void place(ServerPlayer player, InteractionHand wandHand, BlockPos anchor) {
        if (!mayUse(player)) {
            player.displayClientMessage(Component.literal("The blueprint wand needs creative mode or operator permission.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockState state = blockFor(player, wandHand);
        if (state == null) {
            player.displayClientMessage(Component.literal("Hold the block to build with in your other hand.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        ShapePlan plan = ShapePlan.read(player.getItemInHand(wandHand));
        String dimension = player.level().dimension().location().toString();
        ShapeGenerator.WorldResult shape = ShapeGenerator.world(plan, anchor, dimension);
        if (shape.skippedParts() > 0) {
            player.displayClientMessage(Component.literal(shape.skippedParts() +
                    " pinned part(s) are pinned in another dimension and were left out.").withStyle(ChatFormatting.GOLD),
                    false);
        }
        if (shape.truncated()) {
            player.displayClientMessage(Component.literal("That shape is too big (over " + ShapeGenerator.MAX_CELLS +
                    " blocks).").withStyle(ChatFormatting.RED), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        Map<Long, BlockState> before = new LinkedHashMap<>();
        int placed = 0;
        int skipped = 0;

        LongIterator it = shape.cells().iterator();
        while (it.hasNext()) {
            BlockPos pos = BlockPos.of(it.nextLong());
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
                skipped++;
                continue;
            }
            BlockState existing = level.getBlockState(pos);
            boolean free = existing.isAir() || existing.canBeReplaced();
            if ((!free && !plan.overwrite) || level.getBlockEntity(pos) != null || existing == state) {
                skipped++;
                continue;
            }
            before.put(pos.asLong(), existing);
            level.setBlock(pos, state, 2);
            placed++;
        }

        if (!before.isEmpty()) {
            Deque<Undo> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            history.addLast(new Undo(level.dimension(), before));
            while (history.size() > UNDO_DEPTH) history.removeFirst();
        }
        player.displayClientMessage(Component.literal("Placed " + placed + " blocks" +
                (skipped > 0 ? " (" + skipped + " skipped)" : "") + ". Left-click to undo.")
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static void undo(ServerPlayer player) {
        if (!mayUse(player)) return;
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing to undo.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Undo last = history.removeLast();
        ServerLevel level = player.getServer().getLevel(last.dimension());
        if (level == null) return;

        int restored = 0;
        for (Map.Entry<Long, BlockState> entry : last.before().entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            level.setBlock(pos, entry.getValue(), 2);
            restored++;
        }
        player.displayClientMessage(Component.literal("Undid " + restored + " blocks.")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** The lowest and highest corner of what the player last built (and has not undone), or null. */
    public static BlockPos[] lastBuildBounds(ServerPlayer player) {
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty() || history.peekLast().before().isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : history.peekLast().before().keySet()) {
            BlockPos pos = BlockPos.of(key);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BlockPos[] { new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ) };
    }

    public static void forget(UUID player) {
        HISTORY.remove(player);
    }
}
