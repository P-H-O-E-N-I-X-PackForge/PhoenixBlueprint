package net.phoenixvine.blueprint.client.gui;

/** What the hologram last worked out, for the editor to show (client side only). */
public final class ShapeHologramInfo {

    public static volatile int blocks = 0;
    public static volatile boolean tooBig = false;
    public static volatile boolean haveBlock = false;
    /** How many pinned parts are pinned in another dimension and so not shown. */
    public static volatile int pinnedElsewhere = 0;

    private ShapeHologramInfo() {}

    public static String summary() {
        if (tooBig) return "Too big (60,000 max)";
        String text = String.format("%,d blocks", blocks);
        return haveBlock ? text : text + " - no block";
    }
}
