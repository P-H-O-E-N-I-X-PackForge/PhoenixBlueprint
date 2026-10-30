package net.phoenixvine.blueprint.common.export;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a read pattern ({@link PatternImport.Layout}) into blocks: the registry for ids and fields, a basic hatch or bus for an
 * ability, and a gold block marking where the controller goes. What it cannot turn into a block is left out and reported.
 */
public final class ImportResolver {

    /** Most blocks an import may have, the same limit as a shape. */
    public static final int MAX_BLOCKS = 60_000;

    /**
     * One block of the pattern by its place in it: which slice, row and character it is.
     */
    public record Cell(int slice, int row, int chr, BlockState state) {}

    /**
     * A pattern ready to be put in the world.
     *
     * @param cells      the blocks (not the controller, which is a mark)
     * @param controller the slice, row and character of the controller, or null if the pattern has none
     * @param problems   what could not be read or turned into a block
     */
    public record Placement(List<Cell> cells, @Nullable int[] controller, int slices, int rows, int chars,
                            List<String> problems) {

        /** Where each block goes relative to the controller (or the middle of the bottom slice if there is none). */
        public Long2ReferenceMap<BlockState> offsets(Direction front) {
            int cs = controller != null ? controller[0] : slices / 2;
            int cr = controller != null ? controller[1] : 0;
            int cc = controller != null ? controller[2] : chars / 2;
            int[] frame = ControllerFrame.frameOf(front, front.getAxis() == Direction.Axis.Y ? Direction.NORTH :
                    Direction.UP);
            Long2ReferenceMap<BlockState> out = new Long2ReferenceOpenHashMap<>(cells.size());
            for (Cell cell : cells) {
                int s = cell.slice() - cs;
                int r = cell.row() - cr;
                int c = cell.chr() - cc;
                int x = s * frame[0] + r * frame[3] + c * frame[6];
                int y = s * frame[1] + r * frame[4] + c * frame[7];
                int z = s * frame[2] + r * frame[5] + c * frame[8];
                out.put(BlockPos.asLong(x, y, z), cell.state());
            }
            // the controller's own spot is marked, so it can be seen where the machine goes
            out.put(BlockPos.asLong(0, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
            return out;
        }
    }

    private ImportResolver() {}

    public static Placement resolve(String text) {
        PatternImport.Layout layout = PatternImport.parse(text);
        List<String> problems = new ArrayList<>(layout.problems());

        Map<Character, BlockState> states = new HashMap<>();
        Set<Character> controllers = new HashSet<>();
        Set<Character> unresolved = new HashSet<>();
        for (var entry : layout.symbols().entrySet()) {
            char symbol = entry.getKey();
            PatternImport.Pred pred = entry.getValue();
            switch (pred.kind()) {
                case CONTROLLER -> controllers.add(symbol);
                case AIR, ANY -> {}
                default -> {
                    BlockState state = state(pred);
                    if (state == null) unresolved.add(symbol);
                    else states.put(symbol, state);
                }
            }
        }
        if (!unresolved.isEmpty()) {
            StringBuilder b = new StringBuilder("could not find a block for: ");
            for (char c : unresolved) b.append(c).append(' ');
            problems.add(b.toString().trim());
        }

        List<Cell> cells = new ArrayList<>();
        int[] controller = null;
        int rows = layout.slices().isEmpty() ? 0 : layout.slices().get(0).size();
        int chars = rows == 0 || layout.slices().get(0).isEmpty() ? 0 : layout.slices().get(0).get(0).length();
        for (int s = 0; s < layout.slices().size(); s++) {
            List<String> slice = layout.slices().get(s);
            for (int r = 0; r < slice.size(); r++) {
                String row = slice.get(r);
                for (int c = 0; c < row.length(); c++) {
                    char symbol = row.charAt(c);
                    if (controllers.contains(symbol)) {
                        if (controller == null) controller = new int[] { s, r, c };
                    } else if (states.containsKey(symbol)) {
                        if (cells.size() >= MAX_BLOCKS) {
                            problems.add("more than " + MAX_BLOCKS + " blocks, the rest is left out");
                            return new Placement(cells, controller, layout.slices().size(), rows, chars, problems);
                        }
                        cells.add(new Cell(s, r, c, states.get(symbol)));
                    }
                }
            }
        }
        return new Placement(cells, controller, layout.slices().size(), rows, chars, problems);
    }

    private static @Nullable BlockState state(PatternImport.Pred pred) {
        String id = switch (pred.kind()) {
            case BLOCK -> pred.a();
            case FIELD -> BlockReferences.idForField(pred.a(), pred.b());
            case ABILITY -> hatchFor(pred.a());
            default -> null;
        };
        if (id == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block == Blocks.AIR && !id.endsWith("air") ? null : block.defaultBlockState();
    }

    private static final Map<String, String> HATCHES = new HashMap<>();

    /** A basic hatch or bus that provides the ability: the lowest tier, so one is shown in the hologram. */
    private static synchronized @Nullable String hatchFor(String ability) {
        String found = HATCHES.get(ability);
        if (found == null) {
            found = findHatch(ability);
            HATCHES.put(ability, found);
        }
        return found.isEmpty() ? null : found;
    }

    private static String findHatch(String ability) {
        String best = null;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals("gtceu")) continue;
            String abilities = KubeJsExport.partAbilities(id.toString());
            if (abilities.isEmpty() || !abilities.split(", ")[0].equals(ability)) continue;
            if (best == null || rank(id.getPath()) < rank(best.substring(best.indexOf(':') + 1)) ||
                    rank(id.getPath()) == rank(best.substring(best.indexOf(':') + 1)) && id.toString().compareTo(best) < 0) {
                best = id.toString();
            }
        }
        return best == null ? "" : best;
    }

    /** Lower tiers first: ulv, then lv, then the rest. */
    private static int rank(String path) {
        if (path.startsWith("ulv_")) return 0;
        if (path.startsWith("lv_")) return 1;
        return 2;
    }
}
