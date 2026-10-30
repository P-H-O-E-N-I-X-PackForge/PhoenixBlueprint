package net.phoenixvine.blueprint.client;

import com.gregtechceu.gtceu.client.mui.schema.MutableSchema;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.client.render.GhostRenderer;
import net.phoenixvine.blueprint.common.export.C2SImportPlacePacket;
import net.phoenixvine.blueprint.common.export.ImportResolver;

import org.jetbrains.annotations.Nullable;

/**
 * A pattern read from the clipboard, shown as a hologram where the wand is aimed (the controller's spot is a gold block) while
 * the wand is held. It can be turned in quarter turns and built. It lives on the client only until it is built.
 */
@EventBusSubscriber(modid = Blueprint.MOD_ID, value = Dist.CLIENT)
public final class ImportHologram {

    private static final int KEEP_ALIVE_TICKS = 4;
    private static final GhostRenderer RENDERER = new GhostRenderer();

    private static @Nullable String text;
    private static @Nullable ImportResolver.Placement placement;
    private static int turns = 0;

    // locked in place: the hologram stays here whatever the wand is aimed at
    private static @Nullable BlockPos lockedAt;
    private static @Nullable Direction lockedFront;
    private static String lockedDimension = "";

    private static boolean showing = false;
    private static @Nullable Direction builtFront;
    private static @Nullable ImportResolver.Placement builtFor;

    private ImportHologram() {}

    public static boolean active() {
        return placement != null;
    }

    /** Reads the clipboard's pattern; returns what to tell the player. */
    public static String importClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) return "The clipboard is empty.";
        ImportResolver.Placement result = ImportResolver.resolve(clip);
        if (result.cells().isEmpty() && result.controller() == null) {
            return "No pattern found in the clipboard" + (result.problems().isEmpty() ? "." : ": " + result.problems().get(0));
        }
        text = clip.length() > 32767 ? clip.substring(0, 32767) : clip;
        placement = result;
        turns = 0;
        String summary = "Imported " + result.cells().size() + " blocks (" + result.slices() + " x " + result.rows() + " x " +
                result.chars() + ")" + (result.controller() == null ? ", no controller" : "");
        return result.problems().isEmpty() ? summary : summary + "; " + result.problems().get(0);
    }

    public static boolean locked() {
        return lockedAt != null;
    }

    /** Locks the hologram where it is shown now (and facing as shown), or lets it follow the aim again. */
    public static boolean toggleLock() {
        if (lockedAt != null) {
            lockedAt = null;
            lockedFront = null;
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        BlockPos at = ShapeHologram.anchor();
        if (player == null || at == null || placement == null) return false;
        lockedAt = at;
        lockedFront = front(player);
        lockedDimension = ShapeHologram.dimension();
        return true;
    }

    /** Moves a locked hologram by whole blocks. */
    public static void nudge(int dx, int dy, int dz) {
        if (lockedAt != null) lockedAt = lockedAt.offset(dx, dy, dz);
    }

    /** Where it is (locked, or at the aim) and whether that is in the dimension the player is in; null when nowhere. */
    private static @Nullable BlockPos position() {
        if (lockedAt == null) return ShapeHologram.anchor();
        return lockedDimension.equals(ShapeHologram.dimension()) ? lockedAt : null;
    }

    public static void clear() {
        lockedAt = null;
        lockedFront = null;
        text = null;
        placement = null;
        RENDERER.dispose();
        showing = false;
    }

    public static void turn() {
        if (lockedFront != null) lockedFront = lockedFront.getClockWise();
        else turns = (turns + 1) % 4;
    }

    public static int turns() {
        return turns;
    }

    public static String summary() {
        return placement == null ? "" : placement.cells().size() + " blocks";
    }

    private static Direction front(LocalPlayer player) {
        if (lockedFront != null) return lockedFront;
        Direction front = player.getDirection().getOpposite();
        for (int i = 0; i < turns; i++) front = front.getClockWise();
        return front;
    }

    /** Builds the import at the aimed spot, facing as shown. */
    public static boolean place() {
        LocalPlayer player = Minecraft.getInstance().player;
        BlockPos anchor = position();
        if (player == null || placement == null || text == null || anchor == null) return false;
        PacketDistributor.sendToServer(new C2SImportPlacePacket(text, anchor, front(player).get3DDataValue()));
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RENDERER.clientTick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos anchor = position();
        if (player == null || placement == null || anchor == null || ShapeExportClient.wand(player) == null) {
            if (showing) {
                RENDERER.dispose();
                showing = false;
            }
            return;
        }
        Direction front = front(player);
        if (!showing || placement != builtFor || front != builtFront) {
            Long2ReferenceMap<BlockState> blocks = placement.offsets(front);
            RENDERER.showPreview(anchor, new MutableSchema(blocks), true, KEEP_ALIVE_TICKS);
            builtFor = placement;
            builtFront = front;
            showing = true;
        } else {
            RENDERER.setAnchor(anchor);
            RENDERER.keepAlive(KEEP_ALIVE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RENDERER.draw(event.getPoseStack(), event.getModelViewMatrix(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), event.getStage(), mc.getTimer().getGameTimeDeltaPartialTick(false));
    }
}
