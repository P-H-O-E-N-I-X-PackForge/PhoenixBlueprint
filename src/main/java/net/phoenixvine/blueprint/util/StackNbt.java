package net.phoenixvine.blueprint.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/** Reads and writes the custom data (the NBT) of an item stack. */
public final class StackNbt {

    private StackNbt() {}

    /** The stack's custom data to change in place, created if it has none. */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        return stack.getOrCreateTag();
    }

    /** A copy of the stack's custom data, or null when it has none. */
    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        return stack.getTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.hasTag();
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        stack.setTag(tag);
    }

    public static void removeTagKey(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }
}