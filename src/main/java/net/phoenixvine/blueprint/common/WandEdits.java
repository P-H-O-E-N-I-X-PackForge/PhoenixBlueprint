package net.phoenixvine.blueprint.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.common.export.MachineSettings;
import net.phoenixvine.blueprint.common.export.PredicateRules;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import org.jetbrains.annotations.Nullable;

/**
 * Every change the editor can make to the wand, as numbered operations with their values in a buffer. The client applies one
 * to its own copy of the stack as soon as it is clicked (so the editor and the hologram follow at once) and sends it to the
 * server, which applies the same operation to the real stack; the stack then reaches the client the usual way and the two agree.
 * The operations that need the server (undo, copying, selecting the last build) do nothing on the client.
 */
public final class WandEdits {

    public static final int KIND = 0;
    public static final int SIZE = 1;
    public static final int FLAG = 2;
    public static final int FIELD = 3;
    public static final int SELECT = 4;
    public static final int ADD = 5;
    public static final int REMOVE = 6;
    public static final int UNDO = 7;
    public static final int RESET = 8;
    public static final int PRESET = 9;
    public static final int SET_KIND = 10;
    public static final int PIN = 11;
    public static final int UNPIN = 12;
    public static final int EXPORT = 13;
    /** A machine setting: a field number and a text value. */
    public static final int MACHINE = 14;
    /** A limit on a kind of block: a predicate key, a field and a value. */
    public static final int RULE = 15;
    /** How directional blocks are turned to follow the shape: see {@link ShapePlan#orient}. */
    public static final int ORIENT = 16;
    /** The block the selected part is built from (an id, or empty for the block in the other hand). */
    public static final int BLOCK = 17;

    public static final int FLAG_HOLLOW = 0;
    public static final int FLAG_SIT_ON_BASE = 1;
    public static final int FLAG_OVERWRITE = 2;
    public static final int FLAG_CONNECTED = 3;
    public static final int FLAG_MIRROR_X = 4;
    public static final int FLAG_MIRROR_Y = 5;
    public static final int FLAG_MIRROR_Z = 6;
    public static final int FLAG_OPEN = 7;

    public static final int FIELD_WALL = 0;
    public static final int FIELD_YAW = 1;
    public static final int FIELD_PITCH = 2;
    public static final int FIELD_ROLL = 3;
    public static final int FIELD_DX = 4;
    public static final int FIELD_DY = 5;
    public static final int FIELD_DZ = 6;
    public static final int FIELD_ARRAY_MODE = 7;
    public static final int FIELD_ARRAY_COUNT = 8;
    public static final int FIELD_ARRAY_SPACING = 9;

    private WandEdits() {}

    /**
     * Applies one operation to the wand stack.
     *
     * @param player the player on the server, or null on the client (prediction: no permission check, and the operations that
     *               need the server are skipped)
     */
    public static void apply(@Nullable ServerPlayer player, ItemStack stack, int op, FriendlyByteBuf buf) {
        if (player != null && !ShapeActions.mayUse(player)) return;
        if (stack.isEmpty() || !(stack.getItem() instanceof BlueprintWandItem)) return;

        if (op == UNDO) {
            if (player != null) ShapeActions.undo(player);
            return;
        }
        if (op == EXPORT) {
            ShapeExportActions.apply(player, stack, buf.readVarInt());
            return;
        }
        if (op == MACHINE) {
            int field = buf.readVarInt();
            String value = buf.readUtf(256);
            if (field == MachineSettings.RENAME_STRUCTURE) {
                WandSelection sel = WandSelection.read(stack);
                sel.rename(value.replaceAll("[^A-Za-z0-9_]", "_"));
                sel.write(stack);
            } else {
                MachineSettings settings = MachineSettings.read(stack);
                if (settings.apply(field, value)) settings.write(stack);
            }
            return;
        }

        if (op == RULE) {
            String key = buf.readUtf(200);
            int field = buf.readVarInt();
            int value = buf.readVarInt();
            PredicateRules rules = PredicateRules.read(stack);
            rules.apply(key, field, value, key.equals("appearance") ? PredicateRules.Rule.CASING :
                    PredicateRules.Rule.PLAIN);
            rules.write(stack);
            return;
        }

        ShapePlan plan = op == RESET ? new ShapePlan() : ShapePlan.read(stack);
        ShapeSpec spec = plan.current();
        switch (op) {
            case KIND -> {
                ShapeKind next = buf.readVarInt() >= 0 ? spec.kind.next() : spec.kind.prev();
                spec.setKind(next);
            }
            case PIN -> {
                boolean all = buf.readBoolean();
                BlockPos at = buf.readBlockPos();
                String dimension = buf.readUtf(128);
                // a part sitting on the base was shown that much higher, so it is pinned that much higher
                int shift = ShapeGenerator.baseShift(plan);
                for (ShapeSpec part : plan.parts) {
                    if (!all && part != spec) continue;
                    if (part.locked) continue;
                    part.locked = true;
                    part.pinDim = dimension;
                    part.pinX = at.getX();
                    part.pinY = at.getY() + shift;
                    part.pinZ = at.getZ();
                }
            }
            case UNPIN -> {
                boolean all = buf.readBoolean();
                for (ShapeSpec part : plan.parts) {
                    if (all || part == spec) part.locked = false;
                }
            }
            case SET_KIND -> {
                int ordinal = buf.readVarInt();
                if (ordinal >= 0 && ordinal < ShapeKind.values().length) spec.setKind(ShapeKind.values()[ordinal]);
            }
            case ORIENT -> plan.orient = Math.floorMod(buf.readVarInt(), 3);
            case BLOCK -> {
                String id = buf.readUtf(128);
                spec.block = PartBlocks.lookup(id) != null ? id : "";
            }
            case SIZE -> spec.setSize(buf.readVarInt(), buf.readInt());
            case FLAG -> {
                int flag = buf.readVarInt();
                boolean value = buf.readBoolean();
                switch (flag) {
                    case FLAG_HOLLOW -> spec.hollow = value;
                    case FLAG_OPEN -> spec.open = value;
                    case FLAG_SIT_ON_BASE -> plan.sitOnBase = value;
                    case FLAG_OVERWRITE -> plan.overwrite = value;
                    case FLAG_CONNECTED -> plan.connected = value;
                    case FLAG_MIRROR_X -> spec.mirrorX = value;
                    case FLAG_MIRROR_Y -> spec.mirrorY = value;
                    case FLAG_MIRROR_Z -> spec.mirrorZ = value;
                    default -> {}
                }
            }
            case FIELD -> {
                int field = buf.readVarInt();
                int value = buf.readInt();
                switch (field) {
                    case FIELD_WALL -> spec.wall = Math.max(1, Math.min(16, value));
                    case FIELD_YAW -> spec.yaw = Math.floorMod(value, 360);
                    case FIELD_PITCH -> spec.pitch = Math.floorMod(value, 360);
                    case FIELD_ROLL -> spec.roll = Math.floorMod(value, 360);
                    case FIELD_DX -> spec.dx = Math.max(-128, Math.min(128, value));
                    case FIELD_DY -> spec.dy = Math.max(-128, Math.min(128, value));
                    case FIELD_DZ -> spec.dz = Math.max(-128, Math.min(128, value));
                    case FIELD_ARRAY_MODE -> spec.arrayMode = Math.floorMod(value, ShapeSpec.ARRAY_MODES);
                    case FIELD_ARRAY_COUNT -> spec.arrayCount = Math.max(2, Math.min(ShapeSpec.MAX_ARRAY, value));
                    case FIELD_ARRAY_SPACING -> spec.arraySpacing = Math.max(1, Math.min(64, value));
                    default -> {}
                }
            }
            case SELECT -> plan.selected = Math.max(0, Math.min(plan.parts.size() - 1,
                    plan.selected + buf.readVarInt()));
            case RESET -> {} // the plan was replaced by a fresh one above; it is written below
            case ADD -> plan.addPart();
            case REMOVE -> plan.removePart();
            case PRESET -> {
                switch (buf.readVarInt()) {
                    case 0 -> {
                        spec.yaw = 0;
                        spec.pitch = 0;
                        spec.roll = 0;
                    }
                    case 1 -> {
                        spec.yaw = 0;
                        spec.pitch = 90;
                        spec.roll = 0;
                    }
                    case 2 -> {
                        spec.yaw = 0;
                        spec.pitch = 0;
                        spec.roll = 90;
                    }
                    case 3 -> spec.pitch = Math.floorMod(spec.pitch + 45, 360);
                    default -> {}
                }
            }
            default -> {
                return;
            }
        }
        plan.write(stack);
    }
}
