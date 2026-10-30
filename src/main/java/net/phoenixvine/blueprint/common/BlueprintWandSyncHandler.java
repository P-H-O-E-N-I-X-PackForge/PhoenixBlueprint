package net.phoenixvine.blueprint.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.value.sync.SyncHandler;

import java.util.function.Supplier;

/**
 * Carries the editor's changes to the server, which makes them to the plan on the wand's stack (the stack then reaches the
 * client the usual way, and the editor and the hologram read it from there).
 */
public final class BlueprintWandSyncHandler extends SyncHandler<BlueprintWandSyncHandler> {

    private static final int KIND = 0;
    private static final int SIZE = 1;
    private static final int FLAG = 2;
    private static final int FIELD = 3;
    private static final int SELECT = 4;
    private static final int ADD = 5;
    private static final int REMOVE = 6;
    private static final int UNDO = 7;
    private static final int RESET = 8;
    private static final int PRESET = 9;
    private static final int SET_KIND = 10;
    private static final int PIN = 11;
    private static final int UNPIN = 12;
    private static final int EXPORT = 13;

    public static final int FLAG_HOLLOW = 0;
    public static final int FLAG_SIT_ON_BASE = 1;
    public static final int FLAG_OVERWRITE = 2;
    public static final int FLAG_CONNECTED = 3;

    public static final int FIELD_WALL = 0;
    public static final int FIELD_YAW = 1;
    public static final int FIELD_PITCH = 2;
    public static final int FIELD_ROLL = 3;
    public static final int FIELD_DX = 4;
    public static final int FIELD_DY = 5;
    public static final int FIELD_DZ = 6;

    private final Supplier<ItemStack> wand;
    private final ServerPlayer serverPlayer;

    public BlueprintWandSyncHandler(Supplier<ItemStack> wand, ServerPlayer serverPlayer) {
        this.wand = wand;
        this.serverPlayer = serverPlayer;
        allowC2S();
    }

    // ------------------------------------------------------------------ client: ask for a change

    public void changeKind(int direction) {
        syncToServer(KIND, buf -> buf.writeVarInt(direction));
    }

    /**
     * Pins the current part (or every part) at the spot the wand is aimed at, in this dimension, where it now shows; it stays
     * there whatever you aim at afterwards.
     */
    public void pin(boolean all, net.minecraft.core.BlockPos at, String dimension) {
        syncToServer(PIN, buf -> {
            buf.writeBoolean(all);
            buf.writeBlockPos(at);
            buf.writeUtf(dimension, 128);
        });
    }

    /** Lets the current part (or every part) follow the aim again. */
    public void unpin(boolean all) {
        syncToServer(UNPIN, buf -> buf.writeBoolean(all));
    }

    /** An export action: see {@link net.phoenixvine.blueprint.common.export.ShapeExportActions}. */
    public void export(int action) {
        syncToServer(EXPORT, buf -> buf.writeVarInt(action));
    }

    /** Switches the current part to the shape with this ordinal. */
    public void setKind(int ordinal) {
        syncToServer(SET_KIND, buf -> buf.writeVarInt(ordinal));
    }

    public void setSize(int index, int value) {
        syncToServer(SIZE, buf -> {
            buf.writeVarInt(index);
            buf.writeInt(value);
        });
    }

    public void setFlag(int flag, boolean value) {
        syncToServer(FLAG, buf -> {
            buf.writeVarInt(flag);
            buf.writeBoolean(value);
        });
    }

    public void setField(int field, int value) {
        syncToServer(FIELD, buf -> {
            buf.writeVarInt(field);
            buf.writeInt(value);
        });
    }

    public void select(int direction) {
        syncToServer(SELECT, buf -> buf.writeVarInt(direction));
    }

    public void addPart() {
        syncToServer(ADD);
    }

    public void removePart() {
        syncToServer(REMOVE);
    }

    public void undo() {
        syncToServer(UNDO);
    }

    public void reset() {
        syncToServer(RESET);
    }

    /** Turns the current shape to a common orientation: 0 flat, 1 facing Z, 2 facing X, 3 tilted 45 degrees. */
    public void preset(int preset) {
        syncToServer(PRESET, buf -> buf.writeVarInt(preset));
    }

    // ------------------------------------------------------------------ server: make it

    @Override
    public void readOnServer(int id, RegistryFriendlyByteBuf buf) {
        if (serverPlayer == null || !ShapeActions.mayUse(serverPlayer)) return;
        ItemStack stack = wand.get();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlueprintWandItem)) return;

        if (id == UNDO) {
            ShapeActions.undo(serverPlayer);
            return;
        }
        if (id == EXPORT) {
            net.phoenixvine.blueprint.common.export.ShapeExportActions.apply(serverPlayer, stack,
                    buf.readVarInt());
            return;
        }

        ShapePlan plan = id == RESET ? new ShapePlan() : ShapePlan.read(stack);
        ShapeSpec spec = plan.current();
        switch (id) {
            case KIND -> {
                ShapeKind next = buf.readVarInt() >= 0 ? spec.kind.next() : spec.kind.prev();
                spec.setKind(next);
            }
            case PIN -> {
                boolean all = buf.readBoolean();
                net.minecraft.core.BlockPos at = buf.readBlockPos();
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
            case SIZE -> spec.setSize(buf.readVarInt(), buf.readInt());
            case FLAG -> {
                int flag = buf.readVarInt();
                boolean value = buf.readBoolean();
                switch (flag) {
                    case FLAG_HOLLOW -> spec.hollow = value;
                    case FLAG_SIT_ON_BASE -> plan.sitOnBase = value;
                    case FLAG_OVERWRITE -> plan.overwrite = value;
                    case FLAG_CONNECTED -> plan.connected = value;
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
                    case 3 -> {
                        spec.pitch = Math.floorMod(spec.pitch + 45, 360);
                    }
                    default -> {}
                }
            }
            default -> {
                return;
            }
        }
        plan.write(stack);
    }

    @Override
    public void readOnClient(int id, RegistryFriendlyByteBuf buf) {}
}
