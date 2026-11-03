package net.phoenixvine.blueprint.client.render;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import org.jetbrains.annotations.Nullable;

/**
 * The blocks of a hologram seen as a level, so the vanilla block renderer can draw them: the blocks are where the map says (and
 * hide each other's faces), everything is fully lit, and grass and leaves take the colour of the biome at the anchor. It needs
 * nothing but Minecraft, so it works with any GregTech version.
 */
final class GhostLevel implements BlockAndTintGetter {

    private final Long2ReferenceMap<BlockState> blocks;
    private final BlockPos tintAt;

    GhostLevel(Long2ReferenceMap<BlockState> blocks, BlockPos tintAt) {
        this.blocks = blocks;
        this.tintAt = tintAt;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos.asLong());
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0f;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return Minecraft.getInstance().level.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return Minecraft.getInstance().level.getBlockTint(tintAt, resolver);
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkening) {
        return 15;
    }
}
