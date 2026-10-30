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
        return out;
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
            spec.pinDim = tag.getString("pinDim");
            spec.pinX = tag.getInt("pinX");
            spec.pinY = tag.getInt("pinY");
            spec.pinZ = tag.getInt("pinZ");
        }
        return spec;
    }

    private static int clampOffset(int v) {
        return Math.max(-128, Math.min(128, v));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, Arrays.hashCode(size), hollow, wall, yaw, pitch, roll, dx, dy, dz, locked,
                pinDim, pinX, pinY, pinZ);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShapeSpec s && kind == s.kind && Arrays.equals(size, s.size) && hollow == s.hollow &&
                wall == s.wall && yaw == s.yaw && pitch == s.pitch && roll == s.roll && dx == s.dx && dy == s.dy &&
                dz == s.dz && locked == s.locked && pinDim.equals(s.pinDim) && pinX == s.pinX && pinY == s.pinY &&
                pinZ == s.pinZ;
    }
}
