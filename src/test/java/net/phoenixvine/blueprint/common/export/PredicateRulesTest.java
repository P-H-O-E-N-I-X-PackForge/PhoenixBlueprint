package net.phoenixvine.blueprint.common.export;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredicateRulesTest {

    private static final String CASING = "gtceu:solid_machine_casing";
    private static final String HATCH = "gtceu:lv_energy_input_hatch";

    @Test
    void keysNameTheKindOfPredicate() {
        KubeJsExport.Options o = KubeJsExport.Options.DEFAULT;
        assertEquals("block:" + CASING, KubeJsExport.keyFor(CASING, o, null));
        assertEquals("abilities:INPUT_ENERGY", KubeJsExport.keyFor(HATCH, o, null));
        assertNull(KubeJsExport.keyFor("minecraft:air", o, null));
        // with an appearance block the casing and the hatches are one predicate
        assertEquals("appearance", KubeJsExport.keyFor(CASING, o, CASING));
        assertEquals("appearance", KubeJsExport.keyFor(HATCH, o, CASING));
        assertEquals("block:minecraft:glass", KubeJsExport.keyFor("minecraft:glass", o, CASING));
    }

    @Test
    void aRuleAddsItsLimitAndHatchesToTheBlock() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 2, List.of(CASING, HATCH), new int[] { 0, 1 }, false);
        Map<String, PredicateRules.Rule> rules = Map.of(
                "block:" + CASING, new PredicateRules.Rule(PredicateRules.EXACT, 4, true, false),
                "abilities:INPUT_ENERGY", new PredicateRules.Rule(PredicateRules.MIN, 2, false, false));
        String text = KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT, id -> null, "main", false, null, rules);
        assertTrue(text.contains(".where(\"a\", Predicates.blocks(\"" + CASING + "\").setExactLimit(4)\n" +
                "                .or(Predicates.autoAbilities(definition.getRecipeTypes())))"), text);
        assertTrue(text.contains("Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(2))"), text);
    }

    @Test
    void theCasingDefaultsToHatchesAndMaintenanceUnlessARuleSaysOtherwise() {
        KubeJsExport.Grid grid = new KubeJsExport.Grid(1, 1, 1, List.of(CASING), new int[] { 0 }, false);
        String plain = KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT, id -> null, "main", false, CASING, Map.of());
        assertTrue(plain.contains(".or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))"), plain);
        assertTrue(plain.contains(".or(Predicates.autoAbilities(definition.getRecipeTypes()))"), plain);

        String bare = KubeJsExport.format(grid, KubeJsExport.Options.DEFAULT, id -> null, "main", false, CASING,
                Map.of("appearance", new PredicateRules.Rule(PredicateRules.MIN, 5, false, false)));
        assertTrue(bare.contains("Predicates.blocks(\"" + CASING + "\").setMinGlobalLimited(5))"), bare);
        assertTrue(!bare.contains("autoAbilities"), bare);
    }

    @Test
    void editingARule() {
        PredicateRules rules = new PredicateRules();
        rules.apply("block:x", PredicateRules.FIELD_MODE, 2, PredicateRules.Rule.PLAIN);
        rules.apply("block:x", PredicateRules.FIELD_COUNT, 5000, PredicateRules.Rule.PLAIN);
        rules.apply("block:x", PredicateRules.FIELD_HATCHES, 0, PredicateRules.Rule.PLAIN);
        assertEquals(new PredicateRules.Rule(PredicateRules.MAX, 999, true, false), rules.rules.get("block:x"));
        // saved and read back
        assertEquals(rules.rules, PredicateRules.load(rules.save()).rules);
        rules.apply("block:x", PredicateRules.FIELD_RESET, 0, PredicateRules.Rule.PLAIN);
        assertTrue(rules.rules.isEmpty());
        // the casing starts from its default, not from nothing
        rules.apply("appearance", PredicateRules.FIELD_MODE, 1, PredicateRules.Rule.CASING);
        assertTrue(rules.rules.get("appearance").hatches() && rules.rules.get("appearance").maintenance());
    }
}
