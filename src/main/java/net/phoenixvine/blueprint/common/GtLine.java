package net.phoenixvine.blueprint.common;

/**
 * GregTech Modern on 1.20.1 comes in two API lines: the older one (7.x and the 8.0 release: LDLib, {@code api.pattern},
 * {@code .aisle}, {@code FactoryBlockPattern}) and the newer snapshots (ModularUI, {@code api.multiblock}, {@code .slice},
 * {@code MultiblockPatternBuilder}). Blueprint runs with either; this says which one is installed, so the output defaults to
 * the names that machine's GregTech uses.
 */
public final class GtLine {

    private static Boolean legacy;

    private GtLine() {}

    /** True for the older line ({@code .aisle}, {@code FactoryBlockPattern}), false for the newer ({@code .slice}). */
    public static synchronized boolean legacy() {
        if (legacy == null) {
            try {
                Class.forName("com.gregtechceu.gtceu.api.multiblock.MultiPredicate", false,
                        GtLine.class.getClassLoader());
                legacy = false;
            } catch (ClassNotFoundException | LinkageError e) {
                legacy = true;
            }
        }
        return legacy;
    }
}
