package net.phoenixvine.blueprint.common.export;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.util.StackNbt;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the blueprint wand remembers for exporting: whether it is in export mode, the two corners and the controller of the
 * selection, and how the KubeJS text is written. Kept on the wand's stack.
 */
public final class WandSelection {

    private static final String KEY = "ShapeExport";

    /** The most structures a machine can be exported with: "main" and the substructures. */
    public static final int MAX_STRUCTURES = 6;

    /** One named pattern of the machine: GTM 8 machines have a default one ("main") and may have substructures. */
    public static final class Structure {

        public String name;
        public @Nullable BlockPos corner1;
        public @Nullable BlockPos corner2;
        public @Nullable BlockPos controller;

        public Structure(String name) {
            this.name = name;
        }
    }

    /** Every structure; the one being edited is {@link #current}, and its corners and controller are the fields below. */
    private final List<Structure> structures = new ArrayList<>(List.of(new Structure("main")));
    private int current = 0;

    // the corners and controller of the structure being edited
    public @Nullable BlockPos corner1;
    public @Nullable BlockPos corner2;
    public @Nullable BlockPos controller;
    /** True: right-clicking selects blocks and copies them, instead of building. */
    public boolean exportMode = false;
    /** True while a right click on a block marks the controller instead of a corner. */
    public boolean controllerMode = false;
    public KubeJsExport.Options options = KubeJsExport.Options.DEFAULT;

    /** Writes the fields back to the structure being edited. */
    private void commit() {
        Structure s = structures.get(current);
        s.corner1 = corner1;
        s.corner2 = corner2;
        s.controller = controller;
    }

    private void load(int index) {
        current = Math.max(0, Math.min(structures.size() - 1, index));
        Structure s = structures.get(current);
        corner1 = s.corner1;
        corner2 = s.corner2;
        controller = s.controller;
    }

    public int structureCount() {
        return structures.size();
    }

    public int currentIndex() {
        return current;
    }

    public String name() {
        return structures.get(current).name;
    }

    public void select(int index) {
        commit();
        load(index);
    }

    /** Adds a substructure after the last and switches to it; false when there are already {@link #MAX_STRUCTURES}. */
    public boolean addStructure() {
        if (structures.size() >= MAX_STRUCTURES) return false;
        commit();
        structures.add(new Structure("sub_" + structures.size()));
        load(structures.size() - 1);
        controllerMode = false;
        return true;
    }

    /** Removes the structure being edited; the first, "main", cannot be removed. */
    public boolean removeStructure() {
        if (current == 0) return false;
        commit();
        structures.remove(current);
        load(current - 1);
        return true;
    }

    public void rename(String name) {
        if (current != 0) structures.get(current).name = name;
    }

    /** All the structures, the one being edited up to date. */
    public List<Structure> structures() {
        commit();
        return structures;
    }

    public CompoundTag save() {
        commit();
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Structure s : structures) {
            CompoundTag t = new CompoundTag();
            t.putString("name", s.name);
            if (s.corner1 != null) t.putLong("c1", s.corner1.asLong());
            if (s.corner2 != null) t.putLong("c2", s.corner2.asLong());
            if (s.controller != null) t.putLong("ctl", s.controller.asLong());
            list.add(t);
        }
        tag.put("structures", list);
        tag.putInt("current", current);
        tag.putBoolean("export", exportMode);
        tag.putBoolean("controllerMode", controllerMode);
        tag.putBoolean("upper", options.uppercaseFirst());
        tag.putInt("air", options.air());
        tag.putBoolean("parts", options.partsAsAbilities());
        tag.putInt("style", options.style());
        tag.putBoolean("gt7", options.gt7());
        tag.putBoolean("java", options.java());
        return tag;
    }

    public static WandSelection load(CompoundTag tag) {
        WandSelection s = new WandSelection();
        s.structures.clear();
        if (tag.contains("structures", Tag.TAG_LIST)) {
            ListTag list = tag.getList("structures", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(list.size(), MAX_STRUCTURES); i++) {
                CompoundTag t = list.getCompound(i);
                Structure st = new Structure(i == 0 ? "main" : t.getString("name"));
                if (t.contains("c1")) st.corner1 = BlockPos.of(t.getLong("c1"));
                if (t.contains("c2")) st.corner2 = BlockPos.of(t.getLong("c2"));
                if (t.contains("ctl")) st.controller = BlockPos.of(t.getLong("ctl"));
                s.structures.add(st);
            }
        } else {
            // older wands kept a single selection
            Structure st = new Structure("main");
            if (tag.contains("c1")) st.corner1 = BlockPos.of(tag.getLong("c1"));
            if (tag.contains("c2")) st.corner2 = BlockPos.of(tag.getLong("c2"));
            if (tag.contains("ctl")) st.controller = BlockPos.of(tag.getLong("ctl"));
            s.structures.add(st);
        }
        if (s.structures.isEmpty()) s.structures.add(new Structure("main"));
        s.load(tag.getInt("current"));
        s.exportMode = tag.getBoolean("export");
        s.controllerMode = tag.getBoolean("controllerMode");
        s.options = new KubeJsExport.Options(tag.getBoolean("upper"), Math.max(0, Math.min(2, tag.getInt("air"))),
                !tag.contains("parts") || tag.getBoolean("parts"),
                tag.contains("style") ? KubeJsExport.validStyle(tag.getInt("style")) :
                        tag.getBoolean("full") ? KubeJsExport.STYLE_FULL : KubeJsExport.STYLE_FRAGMENT,
                tag.getBoolean("java"), tag.getBoolean("gt7"));
        return s;
    }

    public static WandSelection read(ItemStack stack) {
        CompoundTag tag = StackNbt.getTag(stack);
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) return new WandSelection();
        return load(tag.getCompound(KEY));
    }

    public void write(ItemStack stack) {
        StackNbt.getOrCreateTag(stack).put(KEY, save());
    }

    public boolean hasBothCorners() {
        return corner1 != null && corner2 != null;
    }

    public BlockPos low() {
        return new BlockPos(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()));
    }

    public BlockPos high() {
        return new BlockPos(Math.max(corner1.getX(), corner2.getX()), Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ()));
    }
}
