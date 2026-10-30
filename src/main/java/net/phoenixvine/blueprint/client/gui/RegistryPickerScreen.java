package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** A searchable list of ids to pick one from: blocks, recipe types, recipe modifiers. */
public final class RegistryPickerScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 220;
    private static final int HEADER = 22;
    private static final int ROW = 14;

    private static final int BG = 0xE0101018;
    private static final int PANEL = 0xFF16161F;
    private static final int BORDER = 0xFF2A2A3A;
    private static final int HOVER = 0xFF1E1E2A;
    private static final int ACCENT = 0xFF00AA55;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int FAINT = 0xFF8888AA;

    private final String label;
    private final List<ResourceLocation> all;
    private final Consumer<ResourceLocation> onPick;
    private List<ResourceLocation> shown = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int scroll = 0;
    private int left, top;

    public RegistryPickerScreen(String label, Collection<ResourceLocation> ids, Consumer<ResourceLocation> onPick) {
        super(Component.literal(label));
        this.label = label;
        this.all = new ArrayList<>(ids);
        this.all.sort(null);
        this.onPick = onPick;
        refilter();
    }

    private void refilter() {
        shown = new ArrayList<>();
        String q = query.toLowerCase().trim();
        for (ResourceLocation id : all) {
            if (q.isEmpty() || id.toString().contains(q)) shown.add(id);
        }
        scroll = 0;
    }

    private int visibleRows() {
        return (PANEL_H - HEADER - 24) / ROW;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        search = new EditBox(font, left + 4, top + HEADER + 2, PANEL_W - 8, 16, Component.empty());
        search.setHint(Component.literal("Search"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(q -> {
            query = q;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, BG);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL);
        g.renderOutline(left, top, PANEL_W, PANEL_H, BORDER);
        g.drawCenteredString(font, label, left + PANEL_W / 2, top + 7, TEXT);

        int listTop = top + HEADER + 22;
        for (int row = 0; row < visibleRows(); row++) {
            int index = scroll + row;
            if (index >= shown.size()) break;
            int y = listTop + row * ROW;
            boolean hover = mx >= left + 4 && mx < left + PANEL_W - 4 && my >= y && my < y + ROW;
            if (hover) g.fill(left + 4, y, left + PANEL_W - 4, y + ROW, HOVER);
            g.drawString(font, font.plainSubstrByWidth(shown.get(index).toString(), PANEL_W - 16), left + 8, y + 3,
                    hover ? ACCENT : TEXT, false);
        }
        g.drawString(font, shown.size() + " found", left + 6, top + PANEL_H - 11, FAINT, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int listTop = top + HEADER + 22;
        if (button == 0 && mx >= left + 4 && mx < left + PANEL_W - 4 && my >= listTop) {
            int index = scroll + (int) ((my - listTop) / ROW);
            if (index >= 0 && index < shown.size() && (my - listTop) / ROW < visibleRows()) {
                onPick.accept(shown.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
