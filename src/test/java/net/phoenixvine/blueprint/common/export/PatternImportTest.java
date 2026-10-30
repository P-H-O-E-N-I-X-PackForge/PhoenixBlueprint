package net.phoenixvine.blueprint.common.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternImportTest {

    private static final String CASING = "gtceu:solid_machine_casing";
    private static final String HATCH = "gtceu:lv_energy_input_hatch";

    @Test
    void readsOurOwnKubeJsOutput() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(2, 1, 3, List.of(CASING, HATCH), new int[] { 0, -1, 0, 0, 1, 0 },
                true);
        String text = KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT);
        PatternImport.Layout layout = PatternImport.parse(text);
        assertEquals(List.of(List.of("aKa"), List.of("aba")), layout.slices());
        assertEquals(PatternImport.Kind.CONTROLLER, layout.symbols().get('K').kind());
        assertEquals(new PatternImport.Pred(PatternImport.Kind.BLOCK, CASING, null), layout.symbols().get('a'));
        assertEquals(new PatternImport.Pred(PatternImport.Kind.ABILITY, "INPUT_ENERGY", null), layout.symbols().get('b'));
        assertTrue(layout.problems().isEmpty(), layout.problems().toString());
    }

    @Test
    void readsOurOwnJavaOutputWithRegistryFields() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 3, List.of(CASING, "minecraft:glass", "mod:thing"),
                new int[] { 0, 1, 2 }, false);
        KubeJsExport.Options java = new KubeJsExport.Options(false, KubeJsExport.AIR_SPACE, true,
                KubeJsExport.STYLE_FULL, true, false);
        String text = KubeJsExport.format(grid, java, id -> switch (id) {
            case CASING -> new KubeJsExport.BlockRef("GTBlocks.CASING_STEEL_SOLID.get()", "x");
            case "minecraft:glass" -> new KubeJsExport.BlockRef("Blocks.GLASS", "y");
            default -> null;
        });
        PatternImport.Layout layout = PatternImport.parse(text);
        assertEquals(List.of(List.of("abc")), layout.slices());
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, "GTBlocks", "CASING_STEEL_SOLID"),
                layout.symbols().get('a'));
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, "Blocks", "GLASS"), layout.symbols().get('b'));
        assertEquals(new PatternImport.Pred(PatternImport.Kind.BLOCK, "mod:thing", null), layout.symbols().get('c'));
    }

    @Test
    void readsHandWrittenPatterns() {
        String text = """
                .pattern(definition -> {
                    return MultiblockPatternBuilder
                            .start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                            .slice("BCB", "BEB") // the bottom
                            .sliceRepeatable(2, 4, "BCB", "BEB")
                            .where('B', Predicates.any())
                            .where('C', Predicates.blocks(CASING_TITANIUM_STABLE.get()).setMinGlobalLimited(5)
                                    .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                                    .or(Predicates.controller(definition)))
                            .where('E', Predicates.blocks(ChemicalHelper.getBlock(frameGt, SOURCE)))
                            .where('I', Predicates.controller(definition))
                            .where('\\'', Predicates.air())
                            .build();
                })
                """;
        PatternImport.Layout layout = PatternImport.parse(text);
        assertEquals(3, layout.slices().size());
        assertEquals(PatternImport.Kind.ANY, layout.symbols().get('B').kind());
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, null, "CASING_TITANIUM_STABLE"),
                layout.symbols().get('C'));
        assertEquals(PatternImport.Kind.CONTROLLER, layout.symbols().get('I').kind());
        assertEquals(PatternImport.Kind.AIR, layout.symbols().get('\'').kind());
        // the comment "the bottom" and the wrapped .or(...) lines do not disturb anything
        assertTrue(layout.problems().isEmpty(), layout.problems().toString());
    }

    @Test
    void symbolsMayBeSlashesAndCommentsLookalikes() {
        String text = ".slice(\"a//b\", \"c/*d\")\n.where(\"/\", Predicates.air())\n.where('a', Predicates.air())";
        PatternImport.Layout layout = PatternImport.parse(text);
        assertEquals(List.of(List.of("a//b", "c/*d")), layout.slices());
        assertEquals(PatternImport.Kind.AIR, layout.symbols().get('/').kind());
    }

    @Test
    void reportsWhatItCannotRead() {
        assertTrue(PatternImport.parse("nothing here").problems().get(0).contains("no .slice"));
        assertTrue(PatternImport.parse(".slice(\"ab\", \"c\")").problems().stream()
                .anyMatch(p -> p.contains("different lengths")));
        assertEquals(PatternImport.Kind.UNKNOWN, PatternImport.classify("Predicates.autoAbilities(x)").kind());
    }
}
