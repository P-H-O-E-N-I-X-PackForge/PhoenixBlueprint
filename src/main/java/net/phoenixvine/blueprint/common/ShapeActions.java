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
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

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
        ShapePlan plan = ShapePlan.read(player.getItemInHand(wandHand));
        // every part builds from its own block, else from the block in the other hand
        BlockOrienter.Table[] tables = PartBlocks.tables(plan, state);
        for (BlockOrienter.Table table : tables) {
            if (table == null) {
                player.displayClientMessage(Component.literal("Hold the block to build with in your other hand.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }

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
        int denied = 0;

        // blocks with a direction (logs, stairs, slabs...) are turned to follow the shape
        LongIterator it = shape.cells().iterator();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        while (it.hasNext()) {
            long cell = it.nextLong();
            BlockPos pos = BlockPos.of(cell);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            BlockOrienter.Table table = PartBlocks.tableFor(tables, shape.owners().get(cell));
            BlockState target = table.get(shape.orientation().get(cell));
            switch (setGuarded(level, player, pos, target, plan.overwrite, before)) {
                case PLACED -> placed++;
                case DENIED -> denied++;
                default -> skipped++;
            }
        }

        remember(player, level, before);
        if (minX != Integer.MAX_VALUE) {
            // what the shape covers, whether or not every block of it was new: this is what export mode selects
            LAST_BOUNDS.put(player.getUUID(), new Bounds(level.dimension(), new BlockPos(minX, minY, minZ),
                    new BlockPos(maxX, maxY, maxZ)));
        }
        player.displayClientMessage(Component.literal(summary(placed, skipped, denied) + ". Left-click to undo.")
                .withStyle(denied > 0 ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
    }

    /** The corners of the box a player's last build covered, and where. */
    private record Bounds(ResourceKey<Level> dimension, BlockPos low, BlockPos high) {}

    private static final Map<UUID, Bounds> LAST_BOUNDS = new HashMap<>();

    private enum Outcome {
        PLACED,
        SKIPPED,
        DENIED
    }

    /**
     * Puts one block in the world, the way a player placing it would be checked: protection and claim mods can refuse it.
     * Replacing a block that is not free (overwrite) is a break as well as a placement, so the break is asked about first;
     * the placement is then announced the way a placed block is (set, then the event, and put back if a listener cancels
     * it). When a block is placed, what was there is noted in {@code before} so it can be undone.
     */
    private static Outcome setGuarded(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                                      boolean overwrite, Map<Long, BlockState> before) {
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return Outcome.SKIPPED;
        BlockState existing = level.getBlockState(pos);
        boolean free = existing.isAir() || existing.canBeReplaced();
        if ((!free && !overwrite) || level.getBlockEntity(pos) != null || existing == state) return Outcome.SKIPPED;

        var breakEvent = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, existing, player);
        if (!free && net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breakEvent)) {
            return Outcome.DENIED;
        }

        // placement events describe the block that was there, so the snapshot is taken before the block is set; if a
        // listener refuses, the old block goes back (the way a refused placement is undone for a player)
        net.minecraftforge.common.util.BlockSnapshot snapshot = net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, state, 2);

        var placeEvent = new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(placeEvent)) {
            level.setBlock(pos, existing, 2);
            return Outcome.DENIED;
        }

        before.put(pos.asLong(), existing);
        return Outcome.PLACED;
    }

    private static void remember(ServerPlayer player, ServerLevel level, Map<Long, BlockState> before) {
        if (before.isEmpty()) return;
        Deque<Undo> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        history.addLast(new Undo(level.dimension(), before));
        while (history.size() > UNDO_DEPTH) history.removeFirst();
    }

    private static String summary(int placed, int skipped, int denied) {
        StringBuilder text = new StringBuilder("Placed ").append(placed).append(" blocks");
        if (skipped > 0 || denied > 0) {
            text.append(" (");
            if (skipped > 0) text.append(skipped).append(" skipped");
            if (skipped > 0 && denied > 0) text.append(", ");
            if (denied > 0) text.append(denied).append(" refused by protection");
            text.append(")");
        }
        return text.toString();
    }

    /**
     * Builds blocks of different kinds at given places (an imported pattern), the same way a shape is built: only air and
     * replaceable blocks are replaced unless {@code overwrite}, blocks with block entities are kept, and it can be undone.
     */
    public static void placeStates(ServerPlayer player, Map<BlockPos, BlockState> cells, boolean overwrite) {
        if (!mayUse(player)) {
            player.displayClientMessage(Component.literal("The blueprint wand needs creative mode or operator permission.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (cells.size() > MAX_PLACED) {
            player.displayClientMessage(Component.literal("That is too big to build (over " + MAX_PLACED + " blocks).")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Map<Long, BlockState> before = new LinkedHashMap<>();
        int placed = 0;
        int skipped = 0;
        int denied = 0;
        for (Map.Entry<BlockPos, BlockState> entry : cells.entrySet()) {
            switch (setGuarded(level, player, entry.getKey(), entry.getValue(), overwrite, before)) {
                case PLACED -> placed++;
                case DENIED -> denied++;
                default -> skipped++;
            }
        }
        remember(player, level, before);
        if (!cells.isEmpty()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : cells.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            LAST_BOUNDS.put(player.getUUID(), new Bounds(level.dimension(), new BlockPos(minX, minY, minZ),
                    new BlockPos(maxX, maxY, maxZ)));
        }
        player.displayClientMessage(Component.literal(summary(placed, skipped, denied) + ". Left-click to undo.")
                .withStyle(denied > 0 ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
    }

    private static final int MAX_PLACED = 60_000;

    public static void undo(ServerPlayer player) {
        if (!mayUse(player)) return;
        Deque<Undo> history = HISTORY.get(player.getUUID());
        if (history == null || history.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing to undo.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Undo last = history.removeLast();
        LAST_BOUNDS.remove(player.getUUID());
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

    /** The lowest and highest corner of what the player last built in the dimension they are in (and has not undone), or null. */
    public static BlockPos[] lastBuildBounds(ServerPlayer player) {
        Bounds bounds = LAST_BOUNDS.get(player.getUUID());
        if (bounds == null || !bounds.dimension().equals(player.level().dimension())) return null;
        return new BlockPos[] { bounds.low(), bounds.high() };
    }

    public static void forget(UUID player) {
        HISTORY.remove(player);
        LAST_BOUNDS.remove(player);
    }
}
