package net.phoenixvine.blueprint.common.export;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Looks at the selection before it is exported and says what is likely wrong with it: no controller, too many kinds of block, no
 * energy or maintenance hatch, an unfinished machine definition. Reads the blocks of the world it is given (the client's loaded
 * blocks in the editor), at most {@link #SCAN_LIMIT} of them.
 */
public final class ExportCheck {

    /** Most blocks looked through for the hatch and block-type counts. */
    public static final int SCAN_LIMIT = 200_000;

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public record Finding(Severity level, String text) {}

    private ExportCheck() {}

    public static List<Finding> check(Level world, WandSelection sel, MachineSettings machine) {
        List<Finding> out = new ArrayList<>();
        KubeJsExport.Options o = sel.options;
        boolean machineOutput = o.style() == KubeJsExport.STYLE_MACHINE;

        for (WandSelection.Structure s : sel.structures()) {
            String where = sel.structureCount() > 1 ? "\"" + s.name + "\": " : "";
            if (s.corner1 == null || s.corner2 == null) {
                if (s == sel.structures().get(0) || s.corner1 != null || s.corner2 != null) {
                    out.add(new Finding(Severity.ERROR, where + "set both corners"));
                }
                continue;
            }
            BlockPos low = new BlockPos(Math.min(s.corner1.getX(), s.corner2.getX()),
                    Math.min(s.corner1.getY(), s.corner2.getY()), Math.min(s.corner1.getZ(), s.corner2.getZ()));
            BlockPos high = new BlockPos(Math.max(s.corner1.getX(), s.corner2.getX()),
                    Math.max(s.corner1.getY(), s.corner2.getY()), Math.max(s.corner1.getZ(), s.corner2.getZ()));
            long volume = (long) (high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) *
                    (high.getZ() - low.getZ() + 1);

            if (volume > KubeJsExport.MAX_VOLUME) {
                out.add(new Finding(Severity.ERROR, where + "over " + KubeJsExport.MAX_VOLUME + " blocks, too big"));
                continue;
            }

            if (s.controller == null) {
                out.add(new Finding(machineOutput || o.style() == KubeJsExport.STYLE_FULL ? Severity.WARNING : Severity.INFO,
                        where + "no controller marked (the pattern is read as if it faced south)"));
            } else if (s.controller.getX() < low.getX() || s.controller.getX() > high.getX() ||
                    s.controller.getY() < low.getY() || s.controller.getY() > high.getY() ||
                    s.controller.getZ() < low.getZ() || s.controller.getZ() > high.getZ()) {
                out.add(new Finding(Severity.ERROR, where + "the controller is outside the selection"));
            } else if (world.getBlockState(s.controller).isAir()) {
                out.add(new Finding(Severity.WARNING, where + "the controller mark is on air"));
            }

            if (volume > SCAN_LIMIT) {
                out.add(new Finding(Severity.INFO, where + "too big to check the blocks (" + volume + ")"));
                continue;
            }
            Set<String> types = new HashSet<>();
            boolean energy = false;
            boolean maintenance = false;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = low.getX(); x <= high.getX(); x++) {
                for (int y = low.getY(); y <= high.getY(); y++) {
                    for (int z = low.getZ(); z <= high.getZ(); z++) {
                        var state = world.getBlockState(pos.set(x, y, z));
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (types.add(id)) {
                            String abilities = KubeJsExport.partAbilities(id);
                            if (abilities.contains("INPUT_ENERGY") || abilities.contains("STEAM")) energy = true;
                            if (abilities.contains("MAINTENANCE")) maintenance = true;
                        }
                    }
                }
            }
            if (types.size() > KubeJsExport.MAX_TYPES) {
                out.add(new Finding(Severity.ERROR, where + types.size() + " kinds of block, only " +
                        KubeJsExport.MAX_TYPES + " can be lettered"));
            }
            if (machineOutput) {
                // in the machine definition the casing allows maintenance and the recipe types' hatches, so a missing
                // hatch in the built example matters less
                if (!energy) out.add(new Finding(Severity.INFO, where + "no energy hatch in the selection"));
            } else {
                if (!energy) out.add(new Finding(Severity.INFO, where + "no energy hatch in the selection"));
                if (!maintenance) out.add(new Finding(Severity.INFO, where + "no maintenance hatch in the selection"));
            }
        }

        if (machineOutput) {
            if (machine.appearanceBlock.isEmpty() && sel.structures().stream().noneMatch(s -> s.controller != null)) {
                out.add(new Finding(Severity.WARNING, "no appearance block, and no controller to take one from"));
            }
            if (machine.recipeTypes.isEmpty()) out.add(new Finding(Severity.WARNING, "no recipe type picked"));
            if (o.java()) {
                String controllerBlock = null;
                for (WandSelection.Structure s : sel.structures()) {
                    if (s.controller != null && !world.getBlockState(s.controller).isAir()) {
                        controllerBlock = BuiltInRegistries.BLOCK.getKey(world.getBlockState(s.controller).getBlock())
                                .toString();
                        break;
                    }
                }
                String appearance = machine.effective(controllerBlock).appearanceBlock;
                if (!appearance.isEmpty() && BlockReferences.lookup(appearance) == null) {
                    out.add(new Finding(Severity.WARNING, "the appearance block " + appearance + " is in no *Blocks* or " +
                            "*Casing* class, so it is written as a registry lookup lambda: pick a casing block"));
                }
            }
            if (machine.overlayTexture.isEmpty()) {
                out.add(new Finding(Severity.INFO, "no overlay texture: the casing model is left out"));
            }
        }
        return out;
    }
}
