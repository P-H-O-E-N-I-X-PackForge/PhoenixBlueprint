package net.phoenixvine.blueprint.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.export.WandSelection;
import net.phoenixvine.phantasia.api.PhantasiaAPI;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows the selected structure in Phantasia's 3D viewer as if it were a registered multiblock: the blocks come from the
 * world, are drawn by Phantasia's scene renderer (connected textures and all), and start in their formed state; Phantasia's
 * working toggle switches them to their active state. Only used when Phantasia is installed, and nothing else touches this class.
 */
final class WandPhantasiaPreview {

    /** Most blocks the preview will take (it bakes them all). */
    private static final int MAX_BLOCKS = 200_000;

    private WandPhantasiaPreview() {}

    static void open(WandSelection.Structure s) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || s.corner1 == null || s.corner2 == null) {
            message(mc, "Set both corners first.");
            return;
        }
        BlockPos lo = new BlockPos(Math.min(s.corner1.getX(), s.corner2.getX()),
                Math.min(s.corner1.getY(), s.corner2.getY()), Math.min(s.corner1.getZ(), s.corner2.getZ()));
        BlockPos hi = new BlockPos(Math.max(s.corner1.getX(), s.corner2.getX()),
                Math.max(s.corner1.getY(), s.corner2.getY()), Math.max(s.corner1.getZ(), s.corner2.getZ()));
        int sx = hi.getX() - lo.getX() + 1, sy = hi.getY() - lo.getY() + 1, sz = hi.getZ() - lo.getZ() + 1;
        if ((long) sx * sy * sz > MAX_BLOCKS) {
            message(mc, "The selection is too big to preview (" + MAX_BLOCKS + " blocks at most).");
            return;
        }

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[sx][sy][sz];
        ItemStack icon = ItemStack.EMPTY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockState state = level.getBlockState(pos.set(lo.getX() + x, lo.getY() + y, lo.getZ() + z));
                    if (state.isAir()) {
                        grid[x][y][z] = PhantasiaBlockInfo.EMPTY;
                        continue;
                    }
                    grid[x][y][z] = PhantasiaBlockInfo.fromBlockState(formed(state));
                    if (s.controller != null && s.controller.equals(pos) || icon.isEmpty()) {
                        ItemStack stack = new ItemStack(state.getBlock());
                        if (!stack.isEmpty()) icon = stack;
                    }
                }
            }
        }

        String name = s.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        PhantasiaAPI.openForDefinition(new Definition(Blueprint.id("wand_preview/" + name), "Preview: " + s.name, icon,
                () -> grid), mc.screen);
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }

    /** The state with its formed flag switched on. */
    private static BlockState formed(BlockState state) {
        for (Property<?> prop : List.copyOf(state.getProperties())) {
            String name = prop.getName();
            if (prop instanceof BooleanProperty bp && (name.equals("formed") || name.equals("is_formed"))) {
                state = state.setValue(bp, true);
            }
        }
        return state;
    }

    private static BlockState active(BlockState state) {
        for (Property<?> prop : List.copyOf(state.getProperties())) {
            String name = prop.getName();
            if (prop instanceof BooleanProperty bp && (name.equals("powered") || name.equals("active") ||
                    name.equals("working"))) {
                state = state.setValue(bp, true);
            } else if (prop instanceof EnumProperty<?> ep && name.equals("state")) {
                for (Comparable<?> value : (Collection<Comparable<?>>) (Collection) ep.getPossibleValues()) {
                    if ("online".equals(value.toString()) || "active".equals(value.toString())) {
                        state = setEnum(state, (EnumProperty) ep, value);
                        break;
                    }
                }
            }
        }
        return state;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState setEnum(BlockState state, EnumProperty ep, Comparable value) {
        return state.setValue(ep, value);
    }

    private record Definition(ResourceLocation id, String displayName, ItemStack icon,
                              IPhantasiaMultiblockShape shape) implements IPhantasiaMultiblockDefinition {

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public List<IPhantasiaMultiblockShape> getMatchingShapes() {
            return List.of(shape);
        }

        @Override
        public List<IPhantasiaMultiblockShape> getAllShapes() {
            return List.of(shape);
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ItemStack getIcon() {
            return icon;
        }

        @Override
        public boolean shouldAutoDetectVariants() {
            return false;
        }

        @Override
        public void applyWorkingState(PhantasiaTrackedDummyWorld level, Set<BlockPos> positions,
                                      Map<BlockPos, PhantasiaBlockInfo> blockMap, boolean working) {
            for (BlockPos pos : positions) {
                BlockState current = level.getBlockState(pos);
                if (current.isAir()) continue;
                BlockState next;
                if (working) {
                    next = active(current);
                } else {
                    PhantasiaBlockInfo original = blockMap.get(pos);
                    if (original == null || original.getBlockState().isAir()) continue;
                    next = original.getBlockState();
                }
                if (next == current) continue;
                level.setBlock(pos, next, 0);
                if (next.getBlock() instanceof EntityBlock eb) {
                    BlockEntity be = eb.newBlockEntity(pos, next);
                    if (be != null) {
                        be.setLevel(level);
                        level.setBlockEntity(be);
                    }
                }
            }
        }
    }
}
