package net.phoenixvine.blueprint.client.gui;

/** What the hologram last worked out, for the editor to show (client side only). */
public final class ShapeHologramInfo {

    public static volatile int blocks = 0;
    public static volatile boolean tooBig = false;
    public static volatile boolean haveBlock = false;
    /** How many pinned parts are pinned in another dimension and so not shown. */
    public static volatile int pinnedElsewhere = 0;

    private ShapeHologramInfo() {}

    /** Short enough for the editor's header, where it sits to the right of the mode tabs. */
    public static String summary() {
        if (tooBig) return "Too big (60,000 max)";
        String text = String.format("%,d blocks", blocks);
        return haveBlock ? text : text + ", no block";
    }

    /** The long form, shown as a tooltip when the header has to shorten the summary. */
    public static String fullSummary() {
        if (tooBig) return "Too big (60,000 max)";
        String text = String.format("%,d blocks", blocks);
        return haveBlock ? text : text + "  -  no block in your other hand";
    }
}