package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.Minecraft;

import brachy.modularui.api.layout.IViewportStack;
import brachy.modularui.screen.ModularPanel;

/**
 * ModularUI draws a panel at the size it was built at, whatever the window. This makes a panel that does not fit shrink about
 * its centre (drawing and clicking both follow), leaving room along the top and bottom for EMI's search bar.
 */
public final class PanelFit {

    private PanelFit() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void apply(ModularPanel<?> panel, int width, int height) {
        ((ModularPanel) panel).transform((widget, stack) -> {
            float scale = fitScale(width, height);
            if (scale >= 0.999f) return;
            IViewportStack viewport = (IViewportStack) stack;
            viewport.translate(width / 2f, height / 2f);
            viewport.scale(scale, scale);
            viewport.translate(-width / 2f, -height / 2f);
        });
    }

    /**
     * Where the mouse is within a widget, allowing for the panel having been shrunk by {@link #apply}. A widget that works
     * out its own mouse position from the raw screen mouse (instead of using ModularUI's own hit-testing) must use this, or
     * its clicks land where the widget would have been unscaled.
     *
     * @return {x, y} of the mouse relative to the widget's top-left corner, in the widget's own (unscaled) units
     */
    public static int[] localMouse(brachy.modularui.api.widget.IWidget widget, int absX, int absY, int panelWidth,
                                   int panelHeight) {
        float scale = fitScale(panelWidth, panelHeight);
        float x = absX;
        float y = absY;
        if (scale < 0.999f) {
            var panel = widget.getPanel().getArea();
            float cx = panel.x + panel.width / 2f;
            float cy = panel.y + panel.height / 2f;
            x = cx + (absX - cx) / scale;
            y = cy + (absY - cy) / scale;
        }
        var area = widget.getArea();
        return new int[] { Math.round(x) - area.x, Math.round(y) - area.y };
    }

    /** The scale at which a panel of this size fits the window; 1 when it already does. */
    public static float fitScale(int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        float margin = Math.max(PixelScale.bottomReserve(), 8f);
        float fit = Math.min(1f, Math.min((window.getGuiScaledHeight() - 2 * margin) / height,
                (window.getGuiScaledWidth() - 16f) / width));
        return PixelScale.snap(Math.max(0.3f, fit));
    }
}
