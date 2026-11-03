package net.phoenixvine.blueprint.client.render;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;

import net.minecraft.world.level.block.state.BlockState;

/** The blocks of a hologram, by position (relative to where it is anchored). */
public record GhostBlocks(Long2ReferenceMap<BlockState> map) {}
