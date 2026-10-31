package net.phoenixvine.blueprint.common;

import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/** One shape in a plan: what it is, its sizes, whether it is a hollow shell, how it is turned and where it sits. */
public final class ShapeSpec {

    public ShapeKind kind = ShapeKind.RING;
    public final int[] size = new int[4];
    public boolean hollow = false;
    /** How thick the shell of a hollow shape is, in blocks. */
    public int wall = 1;
    /** Rotation in degrees about Y, X and Z, applied in that order. */
    public int yaw = 0;
    public int pitch = 0;
    public int roll = 0;
    /** Where the shape's centre is from the anchor, in blocks. */
    public int dx = 0;
    public int dy = 0;
    public int dz = 0;
    /**
     * True: the shape is pinned at a place in the world ({@link #pinDim}, {@link #pinX}, {@link #pinY}, {@link #pinZ}) and
     * stays there however you aim; its offset is then from that place. False: it goes where the wand is aimed.
     */
    public boolean locked = false;
    public String pinDim = "";
    public int pinX = 0;
    public int pinY = 0;
    public int pinZ = 0;
    /** The block this part is built from (an id such as minecraft:glass); empty: the block in the other hand. */
    public String block = "";
    /** Repeats of this part: none, along a line (x, y or z) or around the anchor's vertical axis. */
    public static final int ARRAY_OFF = 0;
    public static final int ARRAY_X = 1;
    public static final int ARRAY_Y = 2;
    public static final int ARRAY_Z = 3;
    public static final int ARRAY_RING = 4;
    public static final int ARRAY_MODES = 5;
    public static final int MAX_ARRAY = 16;
    public int arrayMode = ARRAY_OFF;
    /** How many copies there are in all, this part included. */
    public int arrayCount = 3;
    /** The gap between copies along a line, in blocks from centre to centre. */
    public int arraySpacing = 4;
    /** Mirror images of this part (and its repeats) across the planes through the anchor. */
    public boolean mirrorX = false;
    public boolean mirrorY = false;
    public boolean mirrorZ = false;

    public ShapeSpec() {
        setKind(ShapeKind.RING);
    }

    public ShapeSpec(ShapeKind kind) {
        setKind(kind);
    }

    /** Switches shape and gives every size that shape's default. */
    public void setKind(ShapeKind kind) {
        this.kind = kind;
        for (int i = 0; i < 4; i++) {
            ShapeKind.Param param = kind.param(i);
            size[i] = param == null ? 0 : param.def();
        }
    }

    public void setSize(int index, int value) {
        ShapeKind.Param param = kind.param(index);
        if (param == null) return;
        size[index] = Math.max(param.min(), Math.min(param.max(), value));
    }

    public ShapeSpec copy() {
        ShapeSpec out = new ShapeSpec(kind);
        System.arraycopy(size, 0, out.size, 0, 4);
        out.hollow = hollow;
        out.wall = wall;
        out.yaw = yaw;
        out.pitch = pitch;
        out.roll = roll;
        out.dx = dx;
        out.dy = dy;
        out.dz = dz;
        out.block = block;
        out.arrayMode = arrayMode;
        out.arrayCount = arrayCount;
        out.arraySpacing = arraySpacing;
        out.mirrorX = mirrorX;
        out.mirrorY = mirrorY;
        out.mirrorZ = mirrorZ;
        return out;
    }

    /** This one shape exactly (pin included) with its repeats and mirrors left out: what is drawn for each copy. */
    private ShapeSpec single() {
        ShapeSpec out = copy();
        out.arrayMode = ARRAY_OFF;
        out.mirrorX = false;
        out.mirrorY = false;
        out.mirrorZ = false;
        out.locked = locked;
        out.pinDim = pinDim;
        out.pinX = pinX;
        out.pinY = pinY;
        out.pinZ = pinZ;
        return out;
    }

    /**
     * The shapes this part really draws: itself, then its repeats, then the mirror image of all of those across each plane
     * chosen. A repeat around the anchor turns the copy about the vertical axis through the anchor, so a pillar offset from
     * the middle becomes a ring of pillars. Stops at {@code max} shapes.
     * <p>
     * A mirror turns a shape's rotation as a mirror would, so a symmetric shape (ring, sphere, cylinder...) is reflected
     * exactly; a shape with a handedness (helix, spiral, knot) keeps its own.
     */
    public void expandInto(java.util.List<ShapeSpec> out, int max) {
        java.util.List<ShapeSpec> copies = new java.util.ArrayList<>();
        int count = arrayMode == ARRAY_OFF ? 1 : Math.max(1, Math.min(MAX_ARRAY, arrayCount));
        for (int k = 0; k < count; k++) {
            ShapeSpec copy = single();
            switch (arrayMode) {
                case ARRAY_X -> copy.dx = clampOffset(dx + k * arraySpacing);
                case ARRAY_Y -> copy.dy = clampOffset(dy + k * arraySpacing);
                case ARRAY_Z -> copy.dz = clampOffset(dz + k * arraySpacing);
                case ARRAY_RING -> {
                    double angle = 360.0 * k / count;
                    double radians = Math.toRadians(angle);
                    double cos = Math.cos(radians);
                    double sin = Math.sin(radians);
                    copy.dx = clampOffset((int) Math.round(dx * cos + dz * sin));
                    copy.dz = clampOffset((int) Math.round(-dx * sin + dz * cos));
                    copy.yaw = Math.floorMod(yaw + (int) Math.round(angle), 360);
                }
                default -> {
                }
            }
            copies.add(copy);
        }
        if (mirrorX) mirrorAll(copies, 0);
        if (mirrorY) mirrorAll(copies, 1);
        if (mirrorZ) mirrorAll(copies, 2);
        for (ShapeSpec copy : copies) {
            if (out.size() >= max) return;
            out.add(copy);
        }
    }

    /** Adds the mirror image of every shape in the list, across the plane at right angles to the axis (0 x, 1 y, 2 z). */
    private static void mirrorAll(java.util.List<ShapeSpec> shapes, int axis) {
        int n = shapes.size();
        for (int i = 0; i < n; i++) {
            ShapeSpec m = shapes.get(i).single();
            switch (axis) {
                case 0 -> {
                    m.dx = -m.dx;
                    m.yaw = Math.floorMod(-m.yaw, 360);
                    m.roll = Math.floorMod(-m.roll, 360);
                }
                case 1 -> {
                    m.dy = -m.dy;
                    m.pitch = Math.floorMod(-m.pitch, 360);
                    m.roll = Math.floorMod(-m.roll, 360);
                }
                default -> {
                    m.dz = -m.dz;
                    m.yaw = Math.floorMod(-m.yaw, 360);
                    m.pitch = Math.floorMod(-m.pitch, 360);
                }
            }
            shapes.add(m);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name());
        tag.putIntArray("size", size);
        tag.putBoolean("hollow", hollow);
        tag.putInt("wall", wall);
        tag.putInt("yaw", yaw);
        tag.putInt("pitch", pitch);
        tag.putInt("roll", roll);
        tag.putInt("dx", dx);
        tag.putInt("dy", dy);
        tag.putInt("dz", dz);
        if (locked) {
            tag.putBoolean("locked", true);
            tag.putString("pinDim", pinDim);
            tag.putInt("pinX", pinX);
            tag.putInt("pinY", pinY);
            tag.putInt("pinZ", pinZ);
        }
        if (!block.isEmpty()) tag.putString("block", block);
        if (arrayMode != ARRAY_OFF) {
            tag.putInt("arrayMode", arrayMode);
            tag.putInt("arrayCount", arrayCount);
            tag.putInt("arraySpacing", arraySpacing);
        }
        if (mirrorX) tag.putBoolean("mirrorX", true);
        if (mirrorY) tag.putBoolean("mirrorY", true);
        if (mirrorZ) tag.putBoolean("mirrorZ", true);
        return tag;
    }

    public static ShapeSpec load(CompoundTag tag) {
        ShapeSpec spec = new ShapeSpec();
        try {
            spec.kind = ShapeKind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            spec.kind = ShapeKind.RING;
        }
        int[] sizes = tag.getIntArray("size");
        for (int i = 0; i < 4; i++) {
            ShapeKind.Param param = spec.kind.param(i);
            spec.size[i] = param == null ? 0 : (i < sizes.length ? Math.max(param.min(), Math.min(param.max(), sizes[i])) :
                    param.def());
        }
        spec.hollow = tag.getBoolean("hollow");
        spec.wall = Math.max(1, Math.min(16, tag.contains("wall") ? tag.getInt("wall") : 1));
        spec.yaw = Math.floorMod(tag.getInt("yaw"), 360);
        spec.pitch = Math.floorMod(tag.getInt("pitch"), 360);
        spec.roll = Math.floorMod(tag.getInt("roll"), 360);
        spec.dx = clampOffset(tag.getInt("dx"));
        spec.dy = clampOffset(tag.getInt("dy"));
        spec.dz = clampOffset(tag.getInt("dz"));
        spec.locked = tag.getBoolean("locked");
        if (spec.locked) {
            spec.pinDim = cap(tag.getString("pinDim"));
            spec.pinX = tag.getInt("pinX");
            spec.pinY = tag.getInt("pinY");
            spec.pinZ = tag.getInt("pinZ");
        }
        spec.block = cap(tag.getString("block"));
        spec.arrayMode = Math.max(0, Math.min(ARRAY_MODES - 1, tag.getInt("arrayMode")));
        spec.arrayCount = tag.contains("arrayCount") ? Math.max(2, Math.min(MAX_ARRAY, tag.getInt("arrayCount"))) : 3;
        spec.arraySpacing = tag.contains("arraySpacing") ? Math.max(1, Math.min(64, tag.getInt("arraySpacing"))) : 4;
        spec.mirrorX = tag.getBoolean("mirrorX");
        spec.mirrorY = tag.getBoolean("mirrorY");
        spec.mirrorZ = tag.getBoolean("mirrorZ");
        return spec;
    }

    /** Plans can arrive from a file or a packet, so a text in one is kept to a sensible length. */
    private static String cap(String s) {
        return s.length() > 128 ? "" : s;
    }

    private static int clampOffset(int v) {
        return Math.max(-128, Math.min(128, v));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, Arrays.hashCode(size), hollow, wall, yaw, pitch, roll, dx, dy, dz, locked,
                pinDim, pinX, pinY, pinZ, block, arrayMode, arrayCount, arraySpacing, mirrorX, mirrorY, mirrorZ);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShapeSpec s && kind == s.kind && Arrays.equals(size, s.size) && hollow == s.hollow &&
                wall == s.wall && yaw == s.yaw && pitch == s.pitch && roll == s.roll && dx == s.dx && dy == s.dy &&
                dz == s.dz && locked == s.locked && pinDim.equals(s.pinDim) && pinX == s.pinX && pinY == s.pinY &&
                pinZ == s.pinZ && block.equals(s.block) && arrayMode == s.arrayMode && arrayCount == s.arrayCount &&
                arraySpacing == s.arraySpacing && mirrorX == s.mirrorX && mirrorY == s.mirrorY && mirrorZ == s.mirrorZ;
    }
}
