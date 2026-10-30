package net.phoenixvine.blueprint.common.export;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Turns a selection of blocks into KubeJS for GTM 8's pattern builder: {@code .slice(...)} lines (GTM 8 renamed aisle to
 * slice) and one {@code .where(...)} per kind of block. {@link #scan} reads the world in the order the builder reads it and
 * {@link #format} letters the blocks and writes the text; the second part needs no Minecraft classes.
 */
public final class KubeJsExport {

    /** Longest alphabet the blocks are lettered from (no quote, backslash or '=' in it). */
    private static final String LOWER_FIRST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&'()*+,-./:;<>?@[]^_`{|}~";
    private static final String UPPER_FIRST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&'()*+,-./:;<>?@[]^_`{|}~";

    /** What the controller is written as in the pattern (GTM's own examples use K). */
    public static final char CONTROLLER = 'K';

    /** Most blocks a selection may cover (it is read on the server in one go). */
    public static final int MAX_VOLUME = 2_000_000;
    /** Longest text sent to the player (a clientbound payload may not exceed 1 MiB). */
    public static final int MAX_CHARS = 1_000_000;
    public static final int MAX_TYPES = LOWER_FIRST.length();

    /** Slice (or aisle) lines, then where lines. */
    public static final int STYLE_FRAGMENT = 0;
    /** The whole .pattern(...) call. */
    public static final int STYLE_FULL = 1;
    public static final int STYLE_SLICES = 2;
    public static final int STYLE_WHERES = 3;
    /** The whole machine definition: id, recipe types and modifiers, appearance block, patterns and casing model. */
    public static final int STYLE_MACHINE = 4;
    public static final int STYLES = 5;

    /** What the Output chip cycles through: the pattern lines, the whole .pattern(...) call, the full machine definition. */
    public static int nextStyle(int style) {
        return switch (style) {
            case STYLE_FRAGMENT -> STYLE_FULL;
            case STYLE_FULL -> STYLE_MACHINE;
            default -> STYLE_FRAGMENT;
        };
    }

    /** A saved style number; the lines-only styles of older wands are the pattern lines. */
    public static int validStyle(int style) {
        return style == STYLE_FULL || style == STYLE_MACHINE ? style : STYLE_FRAGMENT;
    }

    public static final int AIR_SPACE = 0;
    public static final int AIR_PREDICATE = 1;
    public static final int AIR_BLOCK = 2;

    /**
     * @param uppercaseFirst  letter blocks A, B, C ... before a, b, c ...
     * @param air             {@link #AIR_SPACE} (anything), {@link #AIR_PREDICATE} (Predicates.air()) or {@link #AIR_BLOCK}
     * @param partsAsAbilities write GTM hatches and buses as Predicates.abilities(...)
     * @param style           {@link #STYLE_FRAGMENT}, {@link #STYLE_FULL}, {@link #STYLE_SLICES} or {@link #STYLE_WHERES}
     * @param java            write Java for a machine definition (char literals, registry fields) instead of KubeJS
     * @param gt7             GregTech Modern 1.20.1 names: {@code .aisle(...)} and {@code FactoryBlockPattern} instead of {@code .slice(...)}
     */
    public record Options(boolean uppercaseFirst, int air, boolean partsAsAbilities, int style, boolean java,
                          boolean gt7) {

        public static final Options DEFAULT = new Options(false, AIR_SPACE, true, STYLE_FRAGMENT, false, false);

        public Options(boolean uppercaseFirst, int air, boolean partsAsAbilities, boolean fullPattern) {
            this(uppercaseFirst, air, partsAsAbilities, fullPattern ? STYLE_FULL : STYLE_FRAGMENT, false, false);
        }

        public Options(boolean uppercaseFirst, int air, boolean partsAsAbilities, boolean fullPattern, boolean java) {
            this(uppercaseFirst, air, partsAsAbilities, fullPattern ? STYLE_FULL : STYLE_FRAGMENT, java, false);
        }

        public boolean fullPattern() {
            return style == STYLE_FULL;
        }

        public Options toggleCase() {
            return new Options(!uppercaseFirst, air, partsAsAbilities, style, java, gt7);
        }

        public Options nextAir() {
            return new Options(uppercaseFirst, (air + 1) % 3, partsAsAbilities, style, java, gt7);
        }

        public Options toggleParts() {
            return new Options(uppercaseFirst, air, !partsAsAbilities, style, java, gt7);
        }

        public Options toggleStyle() {
            return new Options(uppercaseFirst, air, partsAsAbilities, nextStyle(style), java, gt7);
        }

        public Options toggleJava() {
            return new Options(uppercaseFirst, air, partsAsAbilities, style, !java, gt7);
        }

        public Options withStyle(int style) {
            return new Options(uppercaseFirst, air, partsAsAbilities, style, java, gt7);
        }

        public Options toggleVersion() {
            return new Options(uppercaseFirst, air, partsAsAbilities, style, java, !gt7);
        }
    }

    /** How Java code names a block: an expression such as {@code PhoenixBlocks.X.get()} and the class it needs imported. */
    public record BlockRef(String expression, String importName) {}

    /**
     * The blocks in pattern order: {@code slices} of {@code rows} rows of {@code chars} cells, each an index into
     * {@code ids}, or -1 for the controller.
     */
    public record Grid(int slices, int rows, int chars, List<String> ids, int[] cells, boolean hasController) {}

    private KubeJsExport() {}

    // ------------------------------------------------------------------ reading the world

    /**
     * Reads the box between the corners in the directions of {@code frame} (see {@link ControllerFrame}), or null when it has
     * more than {@link #MAX_VOLUME} blocks.
     */
    public static @Nullable Grid scan(Level level, BlockPos a, BlockPos b, int[] frame, @Nullable BlockPos controller) {
        int minX = Math.min(a.getX(), b.getX()), minY = Math.min(a.getY(), b.getY()), minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()), maxY = Math.max(a.getY(), b.getY()), maxZ = Math.max(a.getZ(), b.getZ());
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        if ((long) sx * sy * sz > MAX_VOLUME) return null;

        // the unit step of a slice, a row and a char, as (x, y, z) each; every axis is used by exactly one of them
        int slices = frame[0] * frame[0] * sx + frame[1] * frame[1] * sy + frame[2] * frame[2] * sz;
        int rows = frame[3] * frame[3] * sx + frame[4] * frame[4] * sy + frame[5] * frame[5] * sz;
        int chars = frame[6] * frame[6] * sx + frame[7] * frame[7] * sy + frame[8] * frame[8] * sz;

        // the first cell sits at the low end of an axis that is walked upwards and the high end of one walked downwards
        int ox = frame[0] + frame[3] + frame[6] < 0 ? maxX : minX;
        int oy = frame[1] + frame[4] + frame[7] < 0 ? maxY : minY;
        int oz = frame[2] + frame[5] + frame[8] < 0 ? maxZ : minZ;

        Map<String, Integer> index = new LinkedHashMap<>();
        int[] cells = new int[slices * rows * chars];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int s = 0; s < slices; s++) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < chars; c++) {
                    int x = ox + s * frame[0] + r * frame[3] + c * frame[6];
                    int y = oy + s * frame[1] + r * frame[4] + c * frame[7];
                    int z = oz + s * frame[2] + r * frame[5] + c * frame[8];
                    if (controller != null && x == controller.getX() && y == controller.getY() && z == controller.getZ()) {
                        cells[n++] = -1;
                    } else {
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock())
                                .toString();
                        cells[n++] = index.computeIfAbsent(id, k -> index.size());
                    }
                }
            }
        }
        return new Grid(slices, rows, chars, new ArrayList<>(index.keySet()), cells, controller != null);
    }

    // ------------------------------------------------------------------ writing the text

    private static boolean isAir(String id) {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /**
     * The PartAbility constants (comma separated) a GTM hatch or bus provides, judged from its id, or "" when the block is not a
     * recognised part. Only the id is read, so an addon part that names itself differently is written as a plain block.
     */
    public static String partAbilities(String id) {
        if (!id.startsWith("gtceu:")) return "";
        String path = id.substring(6);
        if (path.contains("substation_input_hatch")) return "SUBSTATION_INPUT_ENERGY";
        if (path.contains("substation_output_hatch")) return "SUBSTATION_OUTPUT_ENERGY";
        if (path.contains("laser_target")) return "INPUT_LASER";
        if (path.contains("laser_source")) return "OUTPUT_LASER";
        if (path.contains("energy_input_hatch")) return "INPUT_ENERGY";
        if (path.contains("energy_output_hatch")) return "OUTPUT_ENERGY";
        if (path.contains("steam_input_bus")) return "STEAM_IMPORT_ITEMS";
        if (path.contains("steam_output_bus")) return "STEAM_EXPORT_ITEMS";
        if (path.contains("steam_hatch")) return "STEAM";
        if (path.contains("dual_input_hatch")) return "IMPORT_ITEMS, IMPORT_FLUIDS";
        if (path.contains("dual_output_hatch")) return "EXPORT_ITEMS, EXPORT_FLUIDS";
        if (path.contains("quadruple_input_hatch")) return "IMPORT_FLUIDS_4X";
        if (path.contains("quadruple_output_hatch")) return "EXPORT_FLUIDS_4X";
        if (path.contains("nonuple_input_hatch")) return "IMPORT_FLUIDS_9X";
        if (path.contains("nonuple_output_hatch")) return "EXPORT_FLUIDS_9X";
        if (path.contains("input_hatch")) return "IMPORT_FLUIDS";
        if (path.contains("output_hatch")) return "EXPORT_FLUIDS";
        if (path.contains("input_bus")) return "IMPORT_ITEMS";
        if (path.contains("output_bus")) return "EXPORT_ITEMS";
        if (path.contains("maintenance_hatch")) return "MAINTENANCE";
        if (path.contains("muffler_hatch")) return "MUFFLER";
        if (path.contains("rotor_holder")) return "ROTOR_HOLDER";
        if (path.contains("data_access_hatch")) return "DATA_ACCESS";
        return "";
    }

    private static String abilitiesPredicate(String abilities) {
        StringBuilder out = new StringBuilder("Predicates.abilities(");
        String[] names = abilities.split(", ");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) out.append(", ");
            out.append("PartAbility.").append(names[i]);
        }
        return out.append(")").toString();
    }

    static String jsEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** One way of writing a block: blocks that are written the same way share a letter. */
    private record Entry(String key, String predicate, String note, String letter) {}

    /** The KubeJS (or Java) for the grid, or null when it needs more letters than there are ({@link #MAX_TYPES}). */
    public static @Nullable String format(Grid grid, Options o) {
        return format(grid, o, id -> null);
    }

    /** As {@link #format(Grid, Options)}; {@code refs} names a block by a registry field for Java output (null: look it up). */
    public static @Nullable String format(Grid grid, Options o, Function<String, BlockRef> refs) {
        return format(grid, o, refs, null, false);
    }

    /**
     * As {@link #format(Grid, Options)} for one of several structures of a machine. GTM 8 machines can have named patterns
     * ({@code .pattern("name", definition -> ...)}) besides the default one, "main", which has no name in the call. When
     * {@code labelled}, the lines-only styles start with a comment naming the structure.
     */
    public static @Nullable String format(Grid grid, Options o, Function<String, BlockRef> refs, @Nullable String name,
                                          boolean labelled) {
        return format(grid, o, refs, name, labelled, null);
    }

    /**
     * As above for a machine definition: the blocks equal to {@code appearanceId} (and the hatches and buses, when those are
     * written as abilities) become one casing predicate that also allows maintenance and the recipe types' hatches, which is how
     * a machine's casing is written.
     */
    public static @Nullable String format(Grid grid, Options o, Function<String, BlockRef> refs, @Nullable String name,
                                          boolean labelled, @Nullable String appearanceId) {
        boolean java = o.java();
        Set<String> imports = new TreeSet<>();
        String alphabet = o.uppercaseFirst() ? UPPER_FIRST : LOWER_FIRST;
        int nextLetter = 0;
        List<Entry> entries = new ArrayList<>();
        Map<String, Integer> byKey = new LinkedHashMap<>();
        String[] letterOf = new String[grid.ids().size()];

        for (int b = 0; b < grid.ids().size(); b++) {
            String id = grid.ids().get(b);
            String key;
            String predicate;
            String note = "";
            if (isAir(id) && o.air() == AIR_SPACE) {
                letterOf[b] = " ";
                continue;
            } else if (isAir(id) && o.air() == AIR_PREDICATE) {
                key = "air";
                predicate = "Predicates.air()";
            } else if (appearanceId != null && (id.equals(appearanceId) ||
                    o.partsAsAbilities() && !partAbilities(id).isEmpty())) {
                key = "appearance";
                predicate = (java ? javaBlock(appearanceId, refs, imports) :
                        "Predicates.blocks(\"" + jsEscape(appearanceId) + "\")") +
                        "\n                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))" +
                        "\n                .or(Predicates.autoAbilities(definition.getRecipeTypes()))";
            } else {
                String abilities = o.partsAsAbilities() ? partAbilities(id) : "";
                if (!abilities.isEmpty()) {
                    key = "abilities:" + abilities;
                    predicate = abilitiesPredicate(abilities);
                    note = id;
                } else {
                    key = "block:" + id;
                    predicate = java ? javaBlock(id, refs, imports) : "Predicates.blocks(\"" + jsEscape(id) + "\")";
                }
            }

            Integer found = byKey.get(key);
            if (found == null) {
                char candidate;
                do {
                    if (nextLetter >= alphabet.length()) return null;
                    candidate = alphabet.charAt(nextLetter++);
                } while (grid.hasController() && candidate == CONTROLLER);
                entries.add(new Entry(key, predicate, note, String.valueOf(candidate)));
                found = entries.size() - 1;
                byKey.put(key, found);
            }
            letterOf[b] = entries.get(found).letter();
        }

        boolean full = o.style() == STYLE_FULL || o.style() == STYLE_MACHINE;
        String slice = o.gt7() ? ".aisle(" : ".slice(";
        String indent = full ? "    " : "";
        StringBuilder slices = new StringBuilder();
        int n = 0;
        for (int s = 0; s < grid.slices(); s++) {
            slices.append(indent).append(slice);
            for (int r = 0; r < grid.rows(); r++) {
                if (r > 0) slices.append(", ");
                slices.append('"');
                for (int c = 0; c < grid.chars(); c++) {
                    int cell = grid.cells()[n++];
                    slices.append(cell < 0 ? String.valueOf(CONTROLLER) : jsEscape(letterOf[cell]));
                }
                slices.append('"');
            }
            slices.append(")\n");
        }

        StringBuilder wheres = new StringBuilder();
        if (grid.hasController()) {
            wheres.append(indent).append(".where(").append(java ? "'" + CONTROLLER + "'" : "\"" + CONTROLLER + "\"")
                    .append(java ? (o.gt7() ? ", Predicates.controller(Predicates.blocks(definition.getBlock())))\n" :
                            ", Predicates.controller(definition))\n") :
                            ", Predicates.controller(Predicates.blocks(definition.get())))\n");
        }
        for (Entry e : entries) {
            wheres.append(indent).append(".where(").append(java ? charLiteral(e.letter()) :
                    "\"" + jsEscape(e.letter()) + "\"").append(", ").append(e.predicate()).append(")");
            if (!e.note().isEmpty()) wheres.append(" // ").append(e.note());
            wheres.append("\n");
        }

        StringBuilder out = new StringBuilder();
        if (labelled && name != null && !full) out.append("// ").append(name).append("\n");
        if (o.style() == STYLE_SLICES) {
            out.append(slices);
        } else if (o.style() == STYLE_WHERES) {
            out.append(wheres);
        } else if (!full) {
            out.append(slices).append("\n").append(wheres);
        } else {
            String start = o.gt7() ? "FactoryBlockPattern.start()" : "MultiblockPatternBuilder.start()";
            String head = name == null || name.equals("main") ? "definition" : "\"" + jsEscape(name) + "\", definition";
            out.append(java ? ".pattern(" + head + " -> " + start + "\n" :
                    ".pattern(" + head + " => FactoryBlockPattern.start()\n");
            out.append(slices).append(wheres).append(indent).append(".build())\n");
            if (!grid.hasController()) {
                out.append(java ?
                        "// no controller was marked: add .where('K', " + (o.gt7() ?
                                "Predicates.controller(Predicates.blocks(definition.getBlock()))" :
                                "Predicates.controller(definition)") + ") and a K in a slice\n" :
                        "// no controller was marked: add one with .where(\"K\", Predicates.controller(Predicates.blocks(" +
                                "definition.get()))) and a K in a slice\n");
            }
        }
        if (java && !imports.isEmpty()) out.append("// imports: ").append(String.join(", ", imports)).append("\n");
        return out.toString();
    }

    /** How the machine definition names things: the block, recipe type and modifier ids it was given, by registry field. */
    public record MachineContext(MachineSettings settings, Function<String, BlockRef> blocks,
                                 Function<String, BlockRef> types, Function<String, BlockRef> modifiers) {}

    private static final String[] MACHINE_IMPORTS_JAVA = {
            "com.gregtechceu.gtceu.api.data.RotationState",
            "com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition",
            "com.gregtechceu.gtceu.api.machine.multiblock.PartAbility",
            "com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine",
            "com.gregtechceu.gtceu.api.multiblock.Predicates",
            "com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry"
    };

    private static String identifier(String id, boolean java, Set<String> imports) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        if (!java) return "\"" + jsEscape(namespace + ":" + path) + "\"";
        if (namespace.equals("gtceu")) {
            imports.add("com.gregtechceu.gtceu.GTCEu");
            return "GTCEu.id(\"" + jsEscape(path) + "\")";
        }
        imports.add("net.minecraft.resources.ResourceLocation");
        return "ResourceLocation.fromNamespaceAndPath(\"" + jsEscape(namespace) + "\", \"" + jsEscape(path) + "\")";
    }

    /**
     * The whole machine definition around the patterns (each from {@link #format} in the full style): id, class
     * WorkableElectricMultiblockMachine, rotation, recipe types and modifiers, appearance block, the patterns and the casing
     * model, as Java for a registrate or as a KubeJS machine registration.
     */
    public static String machine(List<String> patterns, Options o, MachineContext ctx) {
        MachineSettings m = ctx.settings();
        boolean java = o.java();
        Set<String> imports = new TreeSet<>();
        StringBuilder chain = new StringBuilder();

        chain.append("        .rotationState(RotationState.").append(m.rotationName()).append(")\n");

        if (!m.recipeTypes.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : m.recipeTypes) {
                BlockRef ref = ctx.types().apply(id);
                if (!java) {
                    names.add("\"" + jsEscape(id) + "\"");
                } else if (ref != null) {
                    imports.add(ref.importName());
                    names.add(ref.expression());
                } else {
                    imports.add("net.minecraft.core.registries.BuiltInRegistries");
                    imports.add("net.minecraft.resources.ResourceLocation");
                    imports.add("com.gregtechceu.gtceu.api.recipe.GTRecipeType");
                    names.add("(GTRecipeType) BuiltInRegistries.RECIPE_TYPE.get(" + identifier(id, true, imports) + ")");
                }
            }
            chain.append("        .recipeTypes(").append(String.join(", ", names)).append(")\n");
        }

        if (!m.recipeModifiers.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : m.recipeModifiers) {
                BlockRef ref = ctx.modifiers().apply(id);
                if (ref != null) {
                    imports.add(ref.importName());
                    names.add(ref.expression());
                } else {
                    names.add("/* unknown modifier " + id + " */");
                }
            }
            chain.append("        .recipeModifiers(").append(String.join(", ", names)).append(")\n");
        }

        if (!m.appearanceBlock.isEmpty()) {
            String block;
            if (!java) {
                block = "() => Block.getBlock(\"" + jsEscape(m.appearanceBlock) + "\")";
            } else {
                BlockRef ref = ctx.blocks().apply(m.appearanceBlock);
                if (ref != null) {
                    imports.add(ref.importName());
                    // a registry entry is already a supplier; a plain block (vanilla) needs wrapping
                    block = ref.expression().endsWith(".get()") ?
                            ref.expression().substring(0, ref.expression().length() - ".get()".length()) :
                            "() -> " + ref.expression();
                } else {
                    imports.add("net.minecraft.core.registries.BuiltInRegistries");
                    imports.add("net.minecraft.resources.ResourceLocation");
                    block = "() -> BuiltInRegistries.BLOCK.get(" + identifier(m.appearanceBlock, true, imports) + ")";
                }
            }
            chain.append("        .appearanceBlock(").append(block).append(")\n");
        } else {
            chain.append("        // .appearanceBlock(...) is missing: pick the main casing block\n");
        }

        for (String pattern : patterns) {
            for (String line : pattern.split("\n", -1)) {
                if (line.startsWith("// imports: ")) {
                    for (String i : line.substring("// imports: ".length()).split(", ")) imports.add(i);
                } else if (!line.isEmpty()) {
                    chain.append("        ").append(line).append("\n");
                }
            }
        }

        if (!m.casingTexture.isEmpty() && !m.overlayTexture.isEmpty()) {
            chain.append("        .workableCasingModel(").append(identifier(m.casingTexture, java, imports)).append(",\n")
                    .append("                ").append(identifier(m.overlayTexture, java, imports)).append(")\n");
        } else {
            chain.append("        // .workableCasingModel(...) is missing: pick the casing and overlay textures\n");
        }

        StringBuilder out = new StringBuilder();
        if (java) {
            for (String i : MACHINE_IMPORTS_JAVA) imports.add(i);
            out.append("public static final MachineEntry<MultiblockMachineDefinition> ").append(m.constantName())
                    .append(" = REGISTRATE\n        .multiblock(\"").append(m.id())
                    .append("\", WorkableElectricMultiblockMachine::new)\n");
            out.append(chain).append("        .register();\n");
            out.append("// imports: ").append(String.join(", ", imports)).append("\n");
        } else {
            out.append("GTCEuStartupEvents.registry('gtceu:machine', event => {\n");
            out.append("    event.create(\"").append(m.id()).append("\", \"multiblock\")\n");
            out.append(chain);
            out.append("})\n");
        }
        return out.toString();
    }

    /** A char literal for a letter: 'a', '\\'' for the apostrophe. */
    static String charLiteral(String letter) {
        return letter.equals("'") ? "'\\''" : letter.equals("\\") ? "'\\\\'" : "'" + letter + "'";
    }

    /** Java for a block: its registry field when it has one, else a lookup by id, as the existing multiblocks do. */
    private static String javaBlock(String id, Function<String, BlockRef> refs, Set<String> imports) {
        BlockRef ref = refs.apply(id);
        if (ref != null) {
            imports.add(ref.importName());
            return "Predicates.blocks(" + ref.expression() + ")";
        }
        imports.add("net.minecraft.core.registries.BuiltInRegistries");
        imports.add("net.minecraft.resources.ResourceLocation");
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return "Predicates.blocks(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(\"" +
                jsEscape(namespace) + "\", \"" + jsEscape(path) + "\")))";
    }
}
