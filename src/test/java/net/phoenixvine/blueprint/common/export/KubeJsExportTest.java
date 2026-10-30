package net.phoenixvine.blueprint.common.export;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeJsExportTest {

    private static final String CASING = "gtceu:solid_machine_casing";
    private static final String HATCH = "gtceu:lv_energy_input_hatch";

    @Test
    void writesSlicesAndWheresWithTheControllerAsK() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(2, 1, 3, List.of(CASING, HATCH), new int[] { 0, -1, 0, 0, 1, 0 },
                true);
        String text = KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT);
        assertEquals(".slice(\"aKa\")\n.slice(\"aba\")\n\n" +
                ".where(\"K\", Predicates.controller(Predicates.blocks(definition.get())))\n" +
                ".where(\"a\", Predicates.blocks(\"" + CASING + "\"))\n" +
                ".where(\"b\", Predicates.abilities(PartAbility.INPUT_ENERGY)) // " + HATCH + "\n", text);
    }

    @Test
    void hatchesCanBeWrittenAsExactBlocks() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 1, List.of(HATCH), new int[] { 0 }, false);
        String text = KubeJsExport.format(grid, new KubeJsExport.Options(false, KubeJsExport.AIR_SPACE, false, false));
        assertTrue(text.contains("Predicates.blocks(\"" + HATCH + "\")"));
    }

    @Test
    void airIsASpaceAPredicateOrABlock() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of("minecraft:air", CASING), new int[] { 0, 1 },
                false);
        assertTrue(KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT).startsWith(".slice(\" a\")"));
        assertTrue(KubeJsExport.format(grid, new KubeJsExport.Options(false, KubeJsExport.AIR_PREDICATE, true, false))
                .contains(".where(\"a\", Predicates.air())"));
        assertTrue(KubeJsExport.format(grid, new KubeJsExport.Options(false, KubeJsExport.AIR_BLOCK, true, false))
                .contains("Predicates.blocks(\"minecraft:air\")"));
    }

    @Test
    void theControllerLetterIsNeverUsedForABlock() {
        List<String> ids = new ArrayList<>();
        int[] cells = new int[12];
        for (int i = 0; i < 12; i++) {
            ids.add("minecraft:block_" + i);
            cells[i] = i;
        }
        cells[0] = -1;
        String text = KubeJsExport.format(new KubeJsExport.Grid(1, 1, 12, ids, cells, true),
                new KubeJsExport.Options(true, KubeJsExport.AIR_SPACE, true, false));
        assertTrue(text.contains(".where(\"L\", Predicates.blocks(\"minecraft:block_10\"))"), text);
        assertEquals(1, text.split("\\.where\\(\"K\"", -1).length - 1);
    }

    @Test
    void fullPatternWrapsEverything() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 1, List.of(CASING), new int[] { 0 }, false);
        String text = KubeJsExport.format(grid, new KubeJsExport.Options(false, KubeJsExport.AIR_SPACE, true, true));
        assertTrue(text.startsWith(".pattern(definition => FactoryBlockPattern.start()\n    .slice(\"a\")\n"));
        assertTrue(text.contains("    .build())"));
        assertTrue(text.contains("no controller was marked"));
    }

    @Test
    void tooManyKindsOfBlockGiveNothing() {
        List<String> ids = new ArrayList<>();
        int[] cells = new int[KubeJsExport.MAX_TYPES + 1];
        for (int i = 0; i < cells.length; i++) {
            ids.add("minecraft:block_" + i);
            cells[i] = i;
        }
        assertNull(KubeJsExport.format(new KubeJsExport.Grid(1, 1, cells.length, ids, cells, false),
                KubeJsExport.Options.DEFAULT));
    }

    @Test
    void javaOutputUsesCharLiteralsAndRegistryFields() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 3, List.of(CASING, HATCH), new int[] { 0, -1, 1 }, true);
        KubeJsExport.Options java = new KubeJsExport.Options(false, KubeJsExport.AIR_SPACE, true, false, true);
        String text = KubeJsExport.format(grid, java, id -> id.equals(CASING) ?
                new KubeJsExport.BlockRef("GTBlocks.CASING_STEEL_SOLID.get()", "com.gregtechceu.gtceu.common.data.GTBlocks") :
                null);
        assertTrue(text.contains(".where('K', Predicates.controller(definition))"), text);
        assertTrue(text.contains(".where('a', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))"), text);
        assertTrue(text.contains(".where('b', Predicates.abilities(PartAbility.INPUT_ENERGY))"), text);
        assertTrue(text.contains("// imports: com.gregtechceu.gtceu.common.data.GTBlocks"), text);
    }

    @Test
    void javaFallsBackToARegistryLookupAndEscapesTheApostrophe() {
        List<String> ids = new ArrayList<>();
        int[] cells = new int[30];
        for (int i = 0; i < 30; i++) {
            ids.add("mod:block_" + i);
            cells[i] = i;
        }
        String text = KubeJsExport.format(new KubeJsExport.Grid(1, 1, 30, ids, cells, false),
                new KubeJsExport.Options(false, KubeJsExport.AIR_SPACE, true, true, true));
        assertTrue(text.startsWith(".pattern(definition -> MultiblockPatternBuilder.start()\n"), text);
        assertTrue(text.contains("Predicates.blocks(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(" +
                "\"mod\", \"block_0\")))"), text);
        assertEquals("'\\''",KubeJsExport.charLiteral("'"));
    }

    @Test
    void stylesCanBeJustTheSlicesOrJustTheWheres() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING), new int[] { 0, -1 }, true);
        String slices = KubeJsExport.format(grid, new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_SLICES,
                false, false));
        assertEquals(".slice(\"aK\")\n", slices);
        String wheres = KubeJsExport.format(grid, new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_WHERES,
                false, false));
        assertTrue(wheres.startsWith(".where(\"K\""), wheres);
        assertTrue(!wheres.contains(".slice"), wheres);
    }

    @Test
    void gregTech7UsesAisleAndFactoryBlockPattern() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING), new int[] { 0, -1 }, true);
        String kjs = KubeJsExport.format(grid, new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_FULL, false,
                true));
        assertTrue(kjs.startsWith(".pattern(definition => FactoryBlockPattern.start()\n    .aisle(\"aK\")"), kjs);
        String java = KubeJsExport.format(grid, new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_FULL, true,
                true));
        assertTrue(java.startsWith(".pattern(definition -> FactoryBlockPattern.start()\n    .aisle(\"aK\")"), java);
        assertTrue(java.contains("Predicates.controller(Predicates.blocks(definition.getBlock()))"), java);
    }

    @Test
    void substructuresAreNamedPatterns() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING), new int[] { 0, -1 }, true);
        KubeJsExport.Options full = new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_FULL, false, false);
        assertTrue(KubeJsExport.format(grid, full, id -> null, "main", true)
                .startsWith(".pattern(definition => FactoryBlockPattern.start()\n"));
        assertTrue(KubeJsExport.format(grid, full, id -> null, "growth", true)
                .startsWith(".pattern(\"growth\", definition => FactoryBlockPattern.start()\n"));
        KubeJsExport.Options java = new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_FULL, true, false);
        assertTrue(KubeJsExport.format(grid, java, id -> null, "growth", true)
                .startsWith(".pattern(\"growth\", definition -> MultiblockPatternBuilder.start()\n"));
        assertTrue(KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT, id -> null, "growth", true)
                .startsWith("// growth\n.slice("));
        assertTrue(KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT, id -> null, "main", false)
                .startsWith(".slice("));
    }

    private static KubeJsExport.MachineContext context(MachineSettings m) {
        return new KubeJsExport.MachineContext(m, id -> id.equals("gtceu:solid_machine_casing") ?
                new KubeJsExport.BlockRef("GTBlocks.CASING_STEEL_SOLID.get()", "com.gregtechceu.gtceu.common.data.GTBlocks") :
                null,
                id -> id.equals("phoenixcore:dimensional_anchoring") ?
                        new KubeJsExport.BlockRef("PhoenixRecipeTypes.DIMENSIONAL_ANCHORING_RECIPES",
                                "net.phoenix.core.common.data.PhoenixRecipeTypes") :
                        null,
                id -> new KubeJsExport.BlockRef("GTRecipeModifiers.OC_PERFECT", "com.gregtechceu.gtceu.common.data.GTRecipeModifiers"));
    }

    private static MachineSettings anchor() {
        MachineSettings m = new MachineSettings();
        m.apply(MachineSettings.NAME, "Dimensional Anchor");
        m.apply(MachineSettings.APPEARANCE, "gtceu:solid_machine_casing");
        m.apply(MachineSettings.ADD_TYPE, "phoenixcore:dimensional_anchoring");
        m.apply(MachineSettings.ADD_MODIFIER, "gtceu:oc_perfect");
        m.apply(MachineSettings.CASING_TEXTURE, "gtceu:block/casings/solid/machine_casing_stable_titanium");
        m.apply(MachineSettings.OVERLAY_TEXTURE, "gtceu:block/multiblock/large_miner");
        m.rotation = 0;
        return m;
    }

    @Test
    void machineDefinitionInJava() {
        MachineSettings m = anchor();
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 3, List.of(CASING, HATCH), new int[] { 0, -1, 1 }, true);
        KubeJsExport.Options java = new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_MACHINE, true, false);
        KubeJsExport.MachineContext ctx = context(m);
        String pattern = KubeJsExport.format(grid, java, ctx.blocks(), "main", false, m.appearanceBlock);
        // the casing and the hatch are one predicate that also allows maintenance and the recipe types' hatches
        assertTrue(pattern.contains(".slice(\"aKa\")"), pattern);
        assertTrue(pattern.contains("Predicates.autoAbilities(definition.getRecipeTypes())"), pattern);

        String text = KubeJsExport.machine(List.of(pattern), java, ctx);
        assertTrue(text.startsWith("public static final MachineEntry<MultiblockMachineDefinition> DIMENSIONAL_ANCHOR = " +
                "REGISTRATE\n        .multiblock(\"dimensional_anchor\", WorkableElectricMultiblockMachine::new)\n" +
                "        .langValue(\"Dimensional Anchor\")\n" +
                "        .rotationState(RotationState.ALL)\n" +
                "        .recipeTypes(PhoenixRecipeTypes.DIMENSIONAL_ANCHORING_RECIPES)\n" +
                "        .recipeModifiers(GTRecipeModifiers.OC_PERFECT)\n" +
                "        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)\n        .pattern(definition -> "), text);
        assertTrue(text.contains("        .workableCasingModel(GTCEu.id(\"block/casings/solid/machine_casing_stable_titanium\"),\n" +
                "                GTCEu.id(\"block/multiblock/large_miner\"))\n        .register();\n"), text);
        assertTrue(text.contains("// imports: "), text);
        assertTrue(text.contains("net.phoenix.core.common.data.PhoenixRecipeTypes"), text);
    }

    @Test
    void machineDefinitionInKubeJs() {
        MachineSettings m = anchor();
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING), new int[] { 0, -1 }, true);
        KubeJsExport.Options kjs = new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_MACHINE, false, false);
        KubeJsExport.MachineContext ctx = context(m);
        String text = KubeJsExport.machine(List.of(KubeJsExport.format(grid, kjs, ctx.blocks(), "main", false,
                m.appearanceBlock)), kjs, ctx);
        assertTrue(text.startsWith("GTCEuStartupEvents.registry('gtceu:machine', event => {\n" +
                "    event.create(\"dimensional_anchor\", \"multiblock\")\n" +
                "        .langValue(\"Dimensional Anchor\")\n" +
                "        .rotationState(RotationState.ALL)\n" +
                "        .recipeTypes(\"phoenixcore:dimensional_anchoring\")\n"), text);
        assertTrue(text.contains(".appearanceBlock(() => Block.getBlock(\"gtceu:solid_machine_casing\"))"), text);
        assertTrue(text.contains(".workableCasingModel(\"gtceu:block/casings/solid/machine_casing_stable_titanium\","), text);
        assertTrue(text.endsWith("})\n"), text);
    }

    @Test
    void appearanceAndCasingFallBackToTheControllerBlock() {
        MachineSettings m = new MachineSettings();
        m.apply(MachineSettings.DERIVED_CASING, "gtceu:block/casings/solid/machine_casing_solid_steel");
        MachineSettings fallback = m.effective("gtceu:solid_machine_casing");
        assertEquals("gtceu:solid_machine_casing", fallback.appearanceBlock);
        assertEquals("gtceu:block/casings/solid/machine_casing_solid_steel", fallback.casingTexture);
        // what was picked wins, and the stored settings are not changed
        m.apply(MachineSettings.APPEARANCE, "minecraft:glass");
        m.apply(MachineSettings.CASING_TEXTURE, "minecraft:block/glass");
        MachineSettings picked = m.effective("gtceu:solid_machine_casing");
        assertEquals("minecraft:glass", picked.appearanceBlock);
        assertEquals("minecraft:block/glass", picked.casingTexture);
        assertEquals("", new MachineSettings().appearanceBlock);
    }

    @Test
    void theIdBecomesANameToShow() {
        MachineSettings m = new MachineSettings();
        m.apply(MachineSettings.NAME, "large_chemical_reactor");
        assertEquals("Large Chemical Reactor", m.displayName());
        assertEquals("My Machine", new MachineSettings().displayName());
    }
}
