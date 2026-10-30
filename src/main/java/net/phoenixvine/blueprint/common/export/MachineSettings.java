package net.phoenixvine.blueprint.common.export;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.util.StackNbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the machine definition export needs besides the patterns: the machine's id, rotation, recipe types and modifiers, the
 * appearance block and the two model textures. The class is always {@code WorkableElectricMultiblockMachine}. Kept on the wand.
 */
public final class MachineSettings {

    private static final String KEY = "ShapeMachine";
    private static final int MAX_LIST = 8;

    public static final String[] ROTATIONS = { "ALL", "NON_Y_AXIS", "Y_AXIS", "NONE" };

    // the fields a picker or text box sets, by number
    public static final int NAME = 0;
    public static final int APPEARANCE = 1;
    public static final int CASING_TEXTURE = 2;
    public static final int OVERLAY_TEXTURE = 3;
    public static final int ADD_TYPE = 4;
    public static final int ADD_MODIFIER = 5;
    public static final int RENAME_STRUCTURE = 6;
    /** Set by the client in the background (no editor reopening): the texture of the block the casing falls back to. */
    public static final int DERIVED_CASING = 7;

    public String name = "my_machine";
    public int rotation = 0;
    public final List<String> recipeTypes = new ArrayList<>();
    public final List<String> recipeModifiers = new ArrayList<>();
    public String appearanceBlock = "";
    public String casingTexture = "";
    public String overlayTexture = "";
    /** The casing texture worked out by the client from the fallback block, used when none was picked. */
    public String derivedCasingTexture = "";

    /**
     * What the export uses: the appearance block falls back to {@code controllerBlock} (the block marked as the controller) when
     * none was picked, and the casing texture to the one worked out from the appearance block.
     */
    public MachineSettings effective(@org.jetbrains.annotations.Nullable String controllerBlock) {
        MachineSettings m = load(save());
        if (m.appearanceBlock.isEmpty() && controllerBlock != null) m.appearanceBlock = controllerBlock;
        if (m.casingTexture.isEmpty()) m.casingTexture = derivedCasingTexture;
        return m;
    }

    public String rotationName() {
        return ROTATIONS[Math.floorMod(rotation, ROTATIONS.length)];
    }

    /** The Java constant name: MY_MACHINE. */
    public String constantName() {
        String id = name.isBlank() ? "my_machine" : name;
        return id.toUpperCase(Locale.ROOT);
    }

    /** The machine id as written in the registration; never empty. */
    public String id() {
        return name.isBlank() ? "my_machine" : name;
    }

    /** Sets a field from a picker or a text box; false when the value is not valid for it. */
    public boolean apply(int field, String value) {
        switch (field) {
            case NAME -> {
                String id = value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_]", "_");
                if (id.isEmpty() || id.length() > 48) return false;
                name = id;
            }
            case APPEARANCE -> {
                if (!isId(value)) return false;
                appearanceBlock = value;
            }
            case CASING_TEXTURE -> {
                if (!isId(value)) return false;
                casingTexture = value;
            }
            case OVERLAY_TEXTURE -> {
                if (!isId(value)) return false;
                overlayTexture = value;
            }
            case DERIVED_CASING -> {
                if (!isId(value)) return false;
                derivedCasingTexture = value;
            }
            case ADD_TYPE -> {
                if (!isId(value) || recipeTypes.contains(value) || recipeTypes.size() >= MAX_LIST) return false;
                recipeTypes.add(value);
            }
            case ADD_MODIFIER -> {
                if (!isId(value) || recipeModifiers.contains(value) || recipeModifiers.size() >= MAX_LIST) return false;
                recipeModifiers.add(value);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean isId(String value) {
        return value.length() <= 200 && ResourceLocation.tryParse(value) != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("rotation", rotation);
        tag.put("types", list(recipeTypes));
        tag.put("modifiers", list(recipeModifiers));
        tag.putString("appearance", appearanceBlock);
        tag.putString("casing", casingTexture);
        tag.putString("overlay", overlayTexture);
        tag.putString("derived", derivedCasingTexture);
        return tag;
    }

    private static ListTag list(List<String> values) {
        ListTag out = new ListTag();
        for (String v : values) out.add(StringTag.valueOf(v));
        return out;
    }

    public static MachineSettings load(CompoundTag tag) {
        MachineSettings m = new MachineSettings();
        if (tag.contains("name")) m.name = tag.getString("name");
        m.rotation = Math.floorMod(tag.getInt("rotation"), ROTATIONS.length);
        for (Tag t : tag.getList("types", Tag.TAG_STRING)) m.recipeTypes.add(t.getAsString());
        for (Tag t : tag.getList("modifiers", Tag.TAG_STRING)) m.recipeModifiers.add(t.getAsString());
        m.appearanceBlock = tag.getString("appearance");
        m.casingTexture = tag.getString("casing");
        m.overlayTexture = tag.getString("overlay");
        m.derivedCasingTexture = tag.getString("derived");
        return m;
    }

    public static MachineSettings read(ItemStack stack) {
        CompoundTag tag = StackNbt.getTag(stack);
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) return new MachineSettings();
        return load(tag.getCompound(KEY));
    }

    public void write(ItemStack stack) {
        StackNbt.getOrCreateTag(stack).put(KEY, save());
    }
}
