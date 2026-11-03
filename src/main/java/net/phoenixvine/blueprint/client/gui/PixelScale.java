package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

/**
 * Shrinking a screen to fit by a fraction of the GUI scale (0.77 of a 3x scale is 2.3 real pixels per GUI pixel) blurs text
 * and makes item icons uneven. When a whole number of real pixels per GUI pixel costs only a little size, this takes it.
 * When it would cost a lot (at a 2x scale the choices are 1x or 0.5x), the fit scale is kept: a small blur is better than
 * a tiny screen.
 */
public final class PixelScale {

    /** The smallest share of the fit scale a whole-pixel scale may have. */
    private static final float MIN_SHARE = 0.85f;

    private PixelScale() {}

    /** Space to leave along the bottom of the window, in GUI units: EMI draws its search bar there. */
    public static float bottomReserve() {
        return ModList.get().isLoaded("emi") ? 24f : 0f;
    }

    /** The scale to use for a screen that fits at {@code fit} (at most 1). */
    public static float snap(float fit) {
        if (fit >= 1f) return 1f;
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        double pixels = Math.floor(fit * gui + 1e-4);
        if (pixels < 1) return fit;
        float whole = (float) (pixels / gui);
        return whole >= fit * MIN_SHARE ? whole : fit;
    }
}
