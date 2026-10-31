package net.phoenixvine.blueprint.common.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternImportPredicatesTest {

    @Test
    void theClassMayBeStaticallyImported() {
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, "PhoenixFissionBlocks", "FISSILE_CASING"),
                PatternImport.classify("blocks(PhoenixFissionBlocks.FISSILE_CASING.get())\n        .setMinGlobalLimited(10)"));
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, "Blocks", "TINTED_GLASS"),
                PatternImport.classify("blocks(Blocks.TINTED_GLASS)"));
        assertEquals(PatternImport.Kind.AIR, PatternImport.classify("air()").kind());
        assertEquals(PatternImport.Kind.CONTROLLER, PatternImport.classify("controller(definition)").kind());
        assertEquals(new PatternImport.Pred(PatternImport.Kind.ABILITY, "IMPORT_ITEMS", null),
                PatternImport.classify("abilities(PartAbility.IMPORT_ITEMS)"));
    }

    @Test
    void customPredicateMethodsAreCalls() {
        assertEquals(new PatternImport.Pred(PatternImport.Kind.CALL, "PhoenixFissionPredicates", "fissionModerators"),
                PatternImport.classify("PhoenixFissionPredicates.fissionModerators()\n        .or(PhoenixFissionPredicates.fissionBlankets())"));
        assertEquals(new PatternImport.Pred(PatternImport.Kind.CALL, null, "fissionCoolers"),
                PatternImport.classify("fissionCoolers().or(fissionFuelRods())"));
        // a Predicates method that is not understood stays unknown rather than being taken for a custom call
        assertEquals(PatternImport.Kind.UNKNOWN, PatternImport.classify("Predicates.autoAbilities(x)").kind());
        assertEquals(PatternImport.Kind.UNKNOWN, PatternImport.classify("Predicates.frame(x)").kind());
    }

    @Test
    void aWholeHandWrittenReactorPattern() {
        String text = """
                .slice("BBCCCCCBB", "BBDEEEDBB", "BBCFFFCBB")
                        .slice("CCCCCCCCC", "EAALILAAE", "BCCGGGCCB")
                        .where('A', Predicates.air())
                        .where('B', Predicates.any())
                        .where('C', blocks(PhoenixFissionBlocks.FISSILE_REACTION_SAFE_CASING.get())
                                .setMinGlobalLimited(10)
                                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                                .or(Predicates.abilities(PhoenixPartAbility.FISSION_SCRAM).setMaxGlobalLimited(1)))
                        .where('D', blocks(PhoenixFissionBlocks.FISSILE_HEAT_SAFE_CASING.get()))
                        .where('E', blocks(Blocks.TINTED_GLASS))
                        .where('F', Predicates.blocks(GCYMBlocks.CASING_HIGH_TEMPERATURE_SMELTING.get()))
                        .where('G', Predicates.blocks(GCYMBlocks.HEAT_VENT.get()))
                        .where('I', PhoenixFissionPredicates.fissionModerators()
                                .or(PhoenixFissionPredicates.fissionBlankets()))
                        .where('L', Predicates.blocks(CASING_TUNGSTENSTEEL_PIPE.get()))
                        .where('M', Predicates.controller(definition))
                """;
        PatternImport.Layout layout = PatternImport.parse(text);
        assertEquals(2, layout.slices().size());
        assertEquals(PatternImport.Kind.FIELD, layout.symbols().get('C').kind());
        assertEquals("FISSILE_REACTION_SAFE_CASING", layout.symbols().get('C').b());
        assertEquals(PatternImport.Kind.FIELD, layout.symbols().get('E').kind());
        assertEquals(PatternImport.Kind.CALL, layout.symbols().get('I').kind());
        assertEquals(new PatternImport.Pred(PatternImport.Kind.FIELD, null, "CASING_TUNGSTENSTEEL_PIPE"),
                layout.symbols().get('L'));
        assertEquals(PatternImport.Kind.CONTROLLER, layout.symbols().get('M').kind());
        assertTrue(layout.raws().get('I').startsWith("PhoenixFissionPredicates.fissionModerators()"));
        assertTrue(layout.problems().isEmpty(), layout.problems().toString());
    }
}
