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

    public ShapePlan() {
        parts.add(new ShapeSpec());
    }

    public ShapeSpec current() {
        selected = Math.max(0, Math.min(parts.size() - 1, selected));
        return parts.get(selected);
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
        return Objects.hash(parts, sitOnBase, connected);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShapePlan p && parts.equals(p.parts) && sitOnBase == p.sitOnBase &&
                connected == p.connected;
    }
}
