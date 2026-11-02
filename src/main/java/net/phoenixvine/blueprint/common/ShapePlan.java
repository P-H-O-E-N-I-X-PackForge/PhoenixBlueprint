package net.phoenixvine.blueprint.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.util.StackNbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a blueprint wand will draw: one or more shapes (several rings in several orientations, say) put together, and whether the
 * whole thing sits with its lowest block on the anchor instead of its centre. Kept on the wand's stack.
 */
public final class ShapePlan {

    public static final int MAX_PARTS = 8;
    private static final String KEY = "ShapePlan";

    public final List<ShapeSpec> parts = new ArrayList<>();
    public int selected = 0;
    /** True: the plan's lowest block is at the anchor's height. False: its centre is on the anchor. */
    public boolean sitOnBase = false;
    /** True: placing replaces whatever is there; false: only air and replaceable blocks are replaced. */
    public boolean overwrite = false;
    /** True: blocks that only touch at an edge or corner get a block between them, so the shape is joined face to face. */
    public boolean connected = true;
    /**
     * How blocks with a direction (logs, stairs, slabs, anything with an axis or facing) are turned to follow the shape:
     * {@link #ORIENT_OFF} leaves them as the block's default, {@link #ORIENT_OUT} turns them to face away from the shape's
     * surface (and lays logs along it), {@link #ORIENT_IN} faces them toward it.
     */
    public int orient = ORIENT_OUT;
    public static final int ORIENT_OFF = 0;
    public static final int ORIENT_OUT = 1;
    public static final int ORIENT_IN = 2;
    /** The most shapes one plan draws once repeats and mirrors are counted. */
    public static final int MAX_INSTANCES = 64;

    /** Every shape the plan really draws: each part, with its repeats and mirror images (see {@link ShapeSpec#expandInto}). */
    public java.util.List<ShapeSpec> instances() {
        java.util.List<ShapeSpec> out = new java.util.ArrayList<>();
        for (ShapeSpec part : parts) part.expandInto(out, MAX_INSTANCES);
        return out;
    }
    /** True: the design has a controller at ({@link #cx}, {@link #cy}, {@link #cz}) from the anchor, so it can be exported. */
    public boolean hasController = false;
    public int cx = 0;
    public int cy = 0;
    public int cz = 0;

    public ShapePlan() {
        parts.add(new ShapeSpec());
    }

    public ShapeSpec current() {
        selected = Math.max(0, Math.min(parts.size() - 1, selected));
        return parts.get(selected);
    }

    /**
     * The different blocks the plan's shapes are built from, in order of first use: a block id, or "" for the block in the
     * other hand. What the generator calls an owner of a cell is a place in this list.
     */
    public java.util.List<String> blockIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (ShapeSpec spec : instances()) {
            if (!ids.contains(spec.block)) ids.add(spec.block);
        }
        if (ids.isEmpty()) ids.add("");
        return ids;
    }

    /** Whether any part has a block of its own (otherwise everything is built from the block in the other hand). */
    public boolean anyBlock() {
        return parts.stream().anyMatch(p -> !p.block.isEmpty());
    }

    public boolean addPart() {
        if (parts.size() >= MAX_PARTS) return false;
        parts.add(selected + 1, current().copy());
        selected++;
        return true;
    }

    public boolean removePart() {
        if (parts.size() <= 1) return false;
        parts.remove(selected);
        selected = Math.max(0, Math.min(parts.size() - 1, selected));
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ShapeSpec spec : parts) list.add(spec.save());
        tag.put("parts", list);
        tag.putInt("selected", selected);
        tag.putBoolean("sitOnBase", sitOnBase);
        tag.putBoolean("overwrite", overwrite);
        tag.putBoolean("connected", connected);
        tag.putInt("orient", orient);
        if (hasController) {
            tag.putBoolean("hasController", true);
            tag.putInt("cx", cx);
            tag.putInt("cy", cy);
            tag.putInt("cz", cz);
        }
        return tag;
    }

    public static ShapePlan load(CompoundTag tag) {
        ShapePlan plan = new ShapePlan();
        plan.parts.clear();
        ListTag list = tag.getList("parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_PARTS); i++) plan.parts.add(ShapeSpec.load(list.getCompound(i)));
        if (plan.parts.isEmpty()) plan.parts.add(new ShapeSpec());
        plan.selected = tag.getInt("selected");
        plan.sitOnBase = tag.getBoolean("sitOnBase");
        plan.overwrite = tag.getBoolean("overwrite");
        plan.connected = !tag.contains("connected") || tag.getBoolean("connected");
        plan.orient = tag.contains("orient") ? Math.floorMod(tag.getInt("orient"), 3) : ORIENT_OUT;
        plan.hasController = tag.getBoolean("hasController");
        plan.cx = Math.max(-128, Math.min(128, tag.getInt("cx")));
        plan.cy = Math.max(-128, Math.min(128, tag.getInt("cy")));
        plan.cz = Math.max(-128, Math.min(128, tag.getInt("cz")));
        plan.current();
        return plan;
    }

    public static ShapePlan read(ItemStack stack) {
        CompoundTag tag = StackNbt.getTag(stack);
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) return new ShapePlan();
        return load(tag.getCompound(KEY));
    }

    public void write(ItemStack stack) {
        StackNbt.getOrCreateTag(stack).put(KEY, save());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parts, sitOnBase, connected, orient, hasController, cx, cy, cz);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShapePlan p && parts.equals(p.parts) && sitOnBase == p.sitOnBase &&
                connected == p.connected && orient == p.orient && hasController == p.hasController && cx == p.cx && cy == p.cy && cz == p.cz;
    }
}
