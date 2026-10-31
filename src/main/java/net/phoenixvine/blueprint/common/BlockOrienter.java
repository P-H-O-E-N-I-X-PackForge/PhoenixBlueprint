package net.phoenixvine.blueprint.common;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Turns a block to follow the surface it is part of: a log or pillar lies along the shape, stairs and anything with a
 * facing look away from (or toward) the surface, and stairs, trapdoors and slabs sit in the top or bottom half to match.
 * <p>
 * What {@link ShapeOrientation} works out for each cell is packed into one byte (see {@link #pack}); this class turns that
 * byte and a block into the block as it is placed. A block with none of these properties comes back unchanged.
 */
public final class BlockOrienter {

    /** Stands for "no orientation known": the block is placed as it is. */
    public static final byte NONE = -1;

    private BlockOrienter() {}

    /**
     * @param normal the direction the surface faces (away from the shape)
     * @param axis   the axis a log-like block lies along
     * @param top    the surface faces mostly downward, so stairs and slabs belong in the top half
     */
    public static byte pack(Direction normal, Direction.Axis axis, boolean top) {
        return (byte) (normal.get3DDataValue() | (axis.ordinal() << 3) | (top ? 32 : 0));
    }

    /** The block turned as the packed orientation says; {@code mode} is {@link ShapePlan#orient}. */
    public static BlockState apply(BlockState base, byte packed, int mode) {
        if (mode == ShapePlan.ORIENT_OFF || packed < 0) return base;
        Direction normal = Direction.from3DDataValue(packed & 7);
        Direction.Axis axis = Direction.Axis.values()[Math.min(2, (packed >> 3) & 3)];
        boolean top = (packed & 32) != 0;
        Direction facing = mode == ShapePlan.ORIENT_IN ? normal.getOpposite() : normal;

        BlockState state = base;
        state = trySet(state, BlockStateProperties.AXIS, axis);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            // a horizontal-only axis (nether portals, say): a vertical one is laid along the surface's horizontal direction
            Direction.Axis horizontal = axis.isHorizontal() ? axis :
                    normal.getAxis().isHorizontal() ? normal.getAxis() : Direction.Axis.X;
            state = trySet(state, BlockStateProperties.HORIZONTAL_AXIS, horizontal);
        }
        state = trySet(state, BlockStateProperties.FACING, facing);
        if (facing.getAxis().isHorizontal()) state = trySet(state, BlockStateProperties.HORIZONTAL_FACING, facing);
        state = trySet(state, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) &&
                state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
            state = trySet(state, BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        }
        return state;
    }

    /** Sets a property when the block has it and accepts the value (some blocks have a "facing" with fewer directions). */
    private static <T extends Comparable<T>> BlockState trySet(BlockState state, Property<T> property, T value) {
        if (!state.hasProperty(property)) return state;
        try {
            return state.setValue(property, value);
        } catch (IllegalArgumentException e) {
            return state;
        }
    }

    /** The oriented block for each packed orientation of one base block, worked out once each. */
    public static final class Table {

        private final BlockState base;
        private final int mode;
        private final BlockState[] cache = new BlockState[64];

        public Table(BlockState base, int mode) {
            this.base = base;
            this.mode = mode;
        }

        public BlockState get(byte packed) {
            if (mode == ShapePlan.ORIENT_OFF || packed < 0 || packed >= cache.length) return base;
            BlockState state = cache[packed];
            if (state == null) {
                state = apply(base, packed, mode);
                cache[packed] = state;
            }
            return state;
        }
    }
}
