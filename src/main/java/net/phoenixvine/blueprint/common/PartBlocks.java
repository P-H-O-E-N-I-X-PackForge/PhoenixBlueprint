package net.phoenixvine.blueprint.common;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The block each part of a plan is built from: the block id the part names, or, when it names none (or one that is not
 * installed), the block in the player's other hand.
 */
public final class PartBlocks {

    private PartBlocks() {}

    /** The block an id names (as it is placed), or null for "" and for ids that are not a block here. */
    public static @Nullable BlockState lookup(String id) {
        if (id.isEmpty()) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(key).filter(block -> block != Blocks.AIR)
                .map(block -> block.defaultBlockState()).orElse(null);
    }

    /**
     * One orienting table per place in {@link ShapePlan#blockIds()}: the part's own block, else {@code held}. An entry is
     * null where a part needs the held block and there is none.
     */
    public static BlockOrienter.Table[] tables(ShapePlan plan, @Nullable BlockState held) {
        List<String> ids = plan.blockIds();
        BlockOrienter.Table[] out = new BlockOrienter.Table[ids.size()];
        for (int i = 0; i < out.length; i++) {
            BlockState own = lookup(ids.get(i));
            BlockState base = own != null ? own : held;
            out[i] = base == null ? null : new BlockOrienter.Table(base, plan.orient);
        }
        return out;
    }

    /** Whether some part is built from the block in the other hand, so there has to be one. */
    public static boolean needsHeld(ShapePlan plan) {
        for (String id : plan.blockIds()) {
            if (lookup(id) == null) return true;
        }
        return false;
    }

    /** The table for a cell's owner (a place in the block list; -1 when no part claims the cell): the first one there is. */
    public static BlockOrienter.Table tableFor(BlockOrienter.Table[] tables, int owner) {
        if (owner >= 0 && owner < tables.length && tables[owner] != null) return tables[owner];
        for (BlockOrienter.Table table : tables) {
            if (table != null) return table;
        }
        return null;
    }
}
