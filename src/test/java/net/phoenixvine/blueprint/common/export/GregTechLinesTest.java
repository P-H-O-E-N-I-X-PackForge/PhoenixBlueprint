package net.phoenixvine.blueprint.common.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The machine definition is written for either GregTech API line on 1.20.1. */
class GregTechLinesTest {

    private static final String CASING = "gtceu:solid_machine_casing";

    private static String machine(boolean java, boolean gt7) {
        MachineSettings m = new MachineSettings();
        m.apply(MachineSettings.NAME, "test_machine");
        m.apply(MachineSettings.APPEARANCE, CASING);
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING), new int[] { 0, -1 }, true);
        KubeJsExport.Options o = new KubeJsExport.Options(false, 0, true, KubeJsExport.STYLE_MACHINE, java, gt7);
        KubeJsExport.MachineContext ctx = new KubeJsExport.MachineContext(m, id -> null, id -> null, id -> null);
        return KubeJsExport.machine(List.of(KubeJsExport.format(grid, o, ctx.blocks(), "main", false, CASING)), o, ctx);
    }

    @Test
    void theOlderLineUsesAisleFactoryBlockPatternAndItsOwnPackages() {
        String text = machine(true, true);
        assertTrue(text.contains(".aisle(\"aK\")"), text);
        assertTrue(text.contains("FactoryBlockPattern.start()"), text);
        assertTrue(text.contains("Predicates.controller(Predicates.blocks(definition.getBlock()))"), text);
        assertTrue(text.contains("com.gregtechceu.gtceu.api.pattern.Predicates"), text);
        assertTrue(text.contains("com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern"), text);
        assertFalse(text.contains("api.multiblock"), text);
    }

    @Test
    void theNewerLineUsesSliceAndMultiblockPatternBuilder() {
        String text = machine(true, false);
        assertTrue(text.contains(".slice(\"aK\")"), text);
        assertTrue(text.contains("MultiblockPatternBuilder.start()"), text);
        assertTrue(text.contains("Predicates.controller(definition)"), text);
        assertTrue(text.contains("com.gregtechceu.gtceu.api.multiblock.Predicates"), text);
        assertTrue(text.contains("com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder"), text);
        assertFalse(text.contains("api.pattern."), text);
    }

    @Test
    void registerGivesTheDefinitionOn1201WithNoMachineEntry() {
        for (boolean gt7 : new boolean[] { true, false }) {
            String text = machine(true, gt7);
            assertTrue(text.startsWith("public static final MultiblockMachineDefinition TEST_MACHINE = REGISTRATE"), text);
            assertFalse(text.contains("MachineEntry"), text);
        }
    }

    @Test
    void kubeJsIsTheSameApartFromTheNamesOfTheLine() {
        assertTrue(machine(false, true).contains(".aisle("));
        assertTrue(machine(false, false).contains(".slice("));
        assertTrue(machine(false, true).contains("FactoryBlockPattern.start()"));
    }
}
