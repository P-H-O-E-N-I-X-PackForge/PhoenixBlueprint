package net.phoenixvine.blueprint.client;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.blueprint.common.C2SWandEditPacket;
import net.phoenixvine.blueprint.common.WandEdits;

import java.util.function.Consumer;

/**
 * The editor's side of {@link WandEdits}: each change is applied to the wand in the player's hand at once, so the editor and
 * the hologram follow immediately, and sent to the server, which makes the same change to the real stack.
 */
public final class WandEditor {

    private WandEditor() {}

    public static void send(int op, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(out);
        byte[] data = new byte[out.readableBytes()];
        out.readBytes(data);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = ShapeExportClient.wand(player);
        if (stack != null) WandEdits.apply(null, stack, op, new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
        PacketDistributor.sendToServer(new C2SWandEditPacket(op, data));
    }

    public static void send(int op) {
        send(op, buf -> {});
    }

    public static void changeKind(int direction) {
        send(WandEdits.KIND, buf -> buf.writeVarInt(direction));
    }

    public static void setKind(int ordinal) {
        send(WandEdits.SET_KIND, buf -> buf.writeVarInt(ordinal));
    }

    public static void setSize(int index, int value) {
        send(WandEdits.SIZE, buf -> {
            buf.writeVarInt(index);
            buf.writeInt(value);
        });
    }

    public static void setFlag(int flag, boolean value) {
        send(WandEdits.FLAG, buf -> {
            buf.writeVarInt(flag);
            buf.writeBoolean(value);
        });
    }

    public static void setField(int field, int value) {
        send(WandEdits.FIELD, buf -> {
            buf.writeVarInt(field);
            buf.writeInt(value);
        });
    }

    public static void select(int direction) {
        send(WandEdits.SELECT, buf -> buf.writeVarInt(direction));
    }

    public static void preset(int preset) {
        send(WandEdits.PRESET, buf -> buf.writeVarInt(preset));
    }

    public static void pin(boolean all, BlockPos at, String dimension) {
        send(WandEdits.PIN, buf -> {
            buf.writeBoolean(all);
            buf.writeBlockPos(at);
            buf.writeUtf(dimension, 128);
        });
    }

    public static void unpin(boolean all) {
        send(WandEdits.UNPIN, buf -> buf.writeBoolean(all));
    }

    /** An export action: see {@code ShapeExportActions}. */
    public static void export(int action) {
        send(WandEdits.EXPORT, buf -> buf.writeVarInt(action));
    }

    /** Changes a limit on a kind of block: see {@code PredicateRules}. */
    public static void rule(String key, int field, int value) {
        send(WandEdits.RULE, buf -> {
            buf.writeUtf(key, 200);
            buf.writeVarInt(field);
            buf.writeVarInt(value);
        });
    }

    /** A machine setting (or a structure name) by field number: see {@code MachineSettings}. */
    public static void machine(int field, String value) {
        send(WandEdits.MACHINE, buf -> {
            buf.writeVarInt(field);
            buf.writeUtf(value, 256);
        });
    }
}
