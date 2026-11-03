package net.phoenixvine.blueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.client.gui.ShapeHologramInfo;
import net.phoenixvine.blueprint.client.render.GhostBlocks;
import net.phoenixvine.blueprint.client.render.GhostRenderer;
import net.phoenixvine.blueprint.common.C2SShapeUndoPacket;
import net.phoenixvine.blueprint.common.BlockOrienter;
import net.phoenixvine.blueprint.common.PartBlocks;
import net.phoenixvine.blueprint.common.ShapeGenerator;
import net.phoenixvine.blueprint.common.ShapeOrientation;
import net.phoenixvine.blueprint.common.ShapePlan;
import net.phoenixvine.blueprint.common.BlueprintWandItem;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.phoenixvine.blueprint.network.BlueprintNetwork;
import org.jetbrains.annotations.Nullable;

/**
 * The hologram of what the wand will build: the plan drawn as ghost blocks (the block in the other hand, or glass without
 * one) where you are aiming. It is rebuilt only when the plan or the block changes; moving the aim only moves it. While the
 * editor is open it stays where it was last aimed so you can watch it change.
 */
@Mod.EventBusSubscriber(modid = Blueprint.MOD_ID, value = Dist.CLIENT)
public final class ShapeHologram {

    private static final int KEEP_ALIVE_TICKS = 4;

    /** The parts that follow the aim. */
    private static final GhostRenderer FREE = new GhostRenderer();
    /** The parts pinned in the world: they do not move with the aim, so they are rebuilt only when the plan changes. */
    private static final GhostRenderer PINNED = new GhostRenderer();

    private static @Nullable BlockPos anchor;
    private static @Nullable ShapePlan builtPlan;
    private static @Nullable BlockState builtState;
    private static @Nullable String builtDimension;
    private static boolean showingFree = false;
    private static boolean showingPinned = false;

    private ShapeHologram() {}

    /** Where the wand is aimed (the editor stays on the last spot while it is open); null before it has been aimed. */
    public static @Nullable BlockPos anchor() {
        return anchor;
    }

    public static String dimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().location().toString();
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        // Ensure it only runs at the end of the tick to avoid double-processing
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        FREE.clientTick();
        PINNED.clientTick();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clear();
            return;
        }

        InteractionHand wandHand = wandHand(player);
        if (wandHand == null) {
            clear();
            return;
        }

        if (mc.screen == null || isShapeScreen(mc)) {
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                anchor = hit.getBlockPos().relative(hit.getDirection());
            } else if (anchor == null) {
                anchor = player.blockPosition().relative(player.getDirection(), 6);
            }
        }

        if (net.phoenixvine.blueprint.common.export.WandSelection.read(player.getItemInHand(wandHand))
                .exportMode) {
            clear();
            return;
        }
        if (anchor == null) return;

        ShapePlan plan = ShapePlan.read(player.getItemInHand(wandHand));
        ItemStack other = player.getItemInHand(wandHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND :
                InteractionHand.MAIN_HAND);
        boolean haveBlock = other.getItem() instanceof BlockItem;
        BlockState state = haveBlock ? ((BlockItem) other.getItem()).getBlock().defaultBlockState() :
                Blocks.GLASS.defaultBlockState();
        String dimension = dimension();

        if (!plan.equals(builtPlan) || state != builtState || !dimension.equals(builtDimension)) {
            rebuild(plan, state, haveBlock, dimension);
        } else {
            if (showingFree) {
                FREE.setAnchor(anchor);
                FREE.keepAlive(KEEP_ALIVE_TICKS);
            }
            if (showingPinned) PINNED.keepAlive(KEEP_ALIVE_TICKS);
        }
    }

    private static void rebuild(ShapePlan plan, BlockState state, boolean haveBlock, String dimension) {
        builtPlan = ShapePlan.load(plan.save());
        builtState = state;
        builtDimension = dimension;

        ShapeGenerator.Result free = ShapeGenerator.freeParts(plan);
        ShapeGenerator.WorldResult pinned = ShapeGenerator.pinnedParts(plan, dimension);
        // which way each block of the free parts turns (the pinned parts bring their own)
        Long2ByteMap freeOrientation = plan.orient != ShapePlan.ORIENT_OFF && !free.truncated() ?
                ShapeOrientation.compute(plan, s -> !s.locked, BlockPos.ZERO, free.shiftY(), dimension, free.cells()) :
                ShapeGenerator.noOrientation();
        ShapeHologramInfo.blocks = free.cells().size() + pinned.cells().size();
        ShapeHologramInfo.tooBig = free.truncated() || pinned.truncated();
        // every part builds from its own block, else from the one in the other hand (glass stands in when there is none)
        BlockOrienter.Table[] tables = PartBlocks.tables(plan, state);
        boolean valid = haveBlock || !PartBlocks.needsHeld(plan);
        ShapeHologramInfo.haveBlock = valid;
        ShapeHologramInfo.pinnedElsewhere = pinned.skippedParts();

        showingFree = show(FREE, anchor, free.cells(), freeOrientation, free.owners(), null, tables, valid) &&
                !ShapeHologramInfo.tooBig;
        if (!showingFree) FREE.dispose();

        // the pinned parts are drawn relative to the first one's pin, a spot that does not move
        BlockPos reference = null;
        for (var spec : plan.parts) {
            if (spec.locked && spec.pinDim.equals(dimension)) {
                reference = new BlockPos(spec.pinX, spec.pinY, spec.pinZ);
                break;
            }
        }
        showingPinned = false;
        if (reference != null && !pinned.cells().isEmpty() && !ShapeHologramInfo.tooBig) {
            showingPinned = show(PINNED, reference, pinned.cells(), pinned.orientation(), pinned.owners(), reference,
                    tables, valid);
        }
        if (!showingPinned) PINNED.dispose();
    }

    /**
     * Shows cells as ghost blocks. {@code origin}, when given, is the world spot the cells' coordinates are made relative to
     * (the pinned parts are in the world, the renderer wants them from the spot they hang from).
     */
    private static boolean show(GhostRenderer renderer, BlockPos at, it.unimi.dsi.fastutil.longs.LongSet cells,
                                Long2ByteMap orientation, it.unimi.dsi.fastutil.longs.Long2IntMap owners,
                                @Nullable BlockPos origin, BlockOrienter.Table[] tables, boolean valid) {
        if (cells.isEmpty()) return false;
        Long2ReferenceMap<BlockState> blocks = new Long2ReferenceOpenHashMap<>(cells.size());
        LongIterator it = cells.iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            long key = origin == null ? cell : BlockPos.asLong(BlockPos.getX(cell) - origin.getX(),
                    BlockPos.getY(cell) - origin.getY(), BlockPos.getZ(cell) - origin.getZ());
            blocks.put(key, PartBlocks.tableFor(tables, owners.get(cell)).get(orientation.get(cell)));
        }
        renderer.showPreview(at, new GhostBlocks(blocks), valid, KEEP_ALIVE_TICKS);
        return true;
    }

    private static void clear() {
        if (showingFree || showingPinned) {
            FREE.dispose();
            PINNED.dispose();
            showingFree = false;
            showingPinned = false;
        }
        builtPlan = null;
        builtState = null;
        builtDimension = null;
    }

    private static @Nullable InteractionHand wandHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BlueprintWandItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof BlueprintWandItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean isShapeScreen(Minecraft mc) {
        return mc.screen instanceof net.phoenixvine.blueprint.client.gui.BlueprintScreen;
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        for (GhostRenderer renderer : new GhostRenderer[] { FREE, PINNED }) {
            renderer.draw(
                    event.getPoseStack(),
                    event.getPoseStack().last().pose(), // Model-view matrix from pose stack
                    mc.renderBuffers().bufferSource(),
                    event.getCamera(),
                    event.getStage(),
                    event.getPartialTick() // Native event partial tick
            );
        }
    }

    /** Left-clicking the air with the wand undoes the last placement (clicking a block is handled on the server). */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getItemStack().getItem() instanceof BlueprintWandItem) {
            BlueprintNetwork.CHANNEL.sendToServer(new C2SShapeUndoPacket());
        }
    }
}
