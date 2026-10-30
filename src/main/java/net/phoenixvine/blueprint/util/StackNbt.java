package net.phoenixvine.blueprint.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

/** Reads and writes the custom data (the NBT) of an item stack. */
public final class StackNbt {

    private StackNbt() {}

    /** The stack's custom data to change in place, created if it has none. */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CompoundTag copy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (stack.isEmpty()) return copy;
        CustomData fresh = CustomData.of(copy);
        stack.set(DataComponents.CUSTOM_DATA, fresh);
        return fresh.getUnsafe();
    }

    /** A copy of the stack's custom data, or null when it has none. */
    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void removeTagKey(ItemStack stack, String key) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(key));
    }
}
