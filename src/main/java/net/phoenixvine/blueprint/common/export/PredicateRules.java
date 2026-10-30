package net.phoenixvine.blueprint.common.export;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.util.StackNbt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How many of a kind of block a machine needs, and whether hatches may take its place: the limits GregTech patterns put on a
 * predicate ({@code setMinGlobalLimited}, {@code setMaxGlobalLimited}, {@code setExactLimit}, {@code .or(autoAbilities)}).
 * Kept on the wand by the key of the predicate they belong to (see {@link KubeJsExport#keyFor}).
 */
public final class PredicateRules {

    public static final int NONE = 0;
    public static final int MIN = 1;
    public static final int MAX = 2;
    public static final int EXACT = 3;

    public static final int FIELD_MODE = 0;
    public static final int FIELD_COUNT = 1;
    public static final int FIELD_HATCHES = 2;
    public static final int FIELD_MAINTENANCE = 3;
    public static final int FIELD_RESET = 4;

    private static final String KEY = "ShapeRules";
    private static final int MAX_RULES = 96;
    private static final int MAX_COUNT = 999;

    /**
     * @param mode        {@link #NONE}, {@link #MIN}, {@link #MAX} or {@link #EXACT}
     * @param count       the number the mode applies to
     * @param hatches     the recipe types' hatches may take this block's place ({@code Predicates.autoAbilities})
     * @param maintenance a maintenance hatch may take its place (exactly one)
     */
    public record Rule(int mode, int count, boolean hatches, boolean maintenance) {

        /** No limit and no hatches. */
        public static final Rule PLAIN = new Rule(NONE, 1, false, false);
        /** What the machine's casing gets when nothing was set: hatches and a maintenance hatch may replace it. */
        public static final Rule CASING = new Rule(NONE, 1, true, true);
    }

    public final Map<String, Rule> rules = new LinkedHashMap<>();

    /** The rule for a predicate key, or {@code fallback} when none was set. */
    public Rule get(String key, Rule fallback) {
        return rules.getOrDefault(key, fallback);
    }

    /** Changes one field of a rule (starting from {@code start} when there is none yet); {@link #FIELD_RESET} deletes it. */
    public void apply(String key, int field, int value, Rule start) {
        if (key.isEmpty() || key.length() > 200) return;
        if (field == FIELD_RESET) {
            rules.remove(key);
            return;
        }
        if (!rules.containsKey(key) && rules.size() >= MAX_RULES) return;
        Rule r = rules.getOrDefault(key, start);
        r = switch (field) {
            case FIELD_MODE -> new Rule(Math.floorMod(value, 4), r.count(), r.hatches(), r.maintenance());
            case FIELD_COUNT -> new Rule(r.mode(), Math.max(0, Math.min(MAX_COUNT, value)), r.hatches(), r.maintenance());
            case FIELD_HATCHES -> new Rule(r.mode(), r.count(), !r.hatches(), r.maintenance());
            case FIELD_MAINTENANCE -> new Rule(r.mode(), r.count(), r.hatches(), !r.maintenance());
            default -> r;
        };
        rules.put(key, r);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (var entry : rules.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("key", entry.getKey());
            t.putInt("mode", entry.getValue().mode());
            t.putInt("count", entry.getValue().count());
            t.putBoolean("hatches", entry.getValue().hatches());
            t.putBoolean("maintenance", entry.getValue().maintenance());
            list.add(t);
        }
        tag.put("rules", list);
        return tag;
    }

    public static PredicateRules load(CompoundTag tag) {
        PredicateRules out = new PredicateRules();
        ListTag list = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_RULES); i++) {
            CompoundTag t = list.getCompound(i);
            out.rules.put(t.getString("key"), new Rule(Math.floorMod(t.getInt("mode"), 4),
                    Math.max(0, Math.min(MAX_COUNT, t.getInt("count"))), t.getBoolean("hatches"),
                    t.getBoolean("maintenance")));
        }
        return out;
    }

    public static PredicateRules read(ItemStack stack) {
        CompoundTag tag = StackNbt.getTag(stack);
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) return new PredicateRules();
        return load(tag.getCompound(KEY));
    }

    public void write(ItemStack stack) {
        StackNbt.getOrCreateTag(stack).put(KEY, save());
    }
}
