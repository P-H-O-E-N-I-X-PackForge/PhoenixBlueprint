package net.phoenixvine.blueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.blueprint.client.gui.AnimatedTexture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picks a block texture from a grid of what the textures look like: the thumbnails are drawn with {@link AnimatedTexture},
 * which reads each texture's .mcmeta so animated textures play.
 */
final class WandTextureBrowser extends Screen {

    private static final int THUMB = 48;
    private static final int PAD = 6;
    private static final int LABEL = 12;
    private static final int HEADER = 42;
    private static final int FOOTER = 28;

    private static final int BG = 0xE0101018;
    private static final int PANEL = 0xFF16161F;
    private static final int BORDER = 0xFF2A2A3A;
    private static final int ACCENT = 0xFF00AA55;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int FAINT = 0xFF777788;

    private final Screen parent;
    private final List<ResourceLocation> all;
    private final Consumer<ResourceLocation> onPick;
    private List<ResourceLocation> filtered = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int scroll = 0;
    private int hovered = -1;

    WandTextureBrowser(Screen parent, List<ResourceLocation> textures, Consumer<ResourceLocation> onPick) {
        super(Component.literal("Pick a texture"));
        this.parent = parent;
        this.all = new ArrayList<>(textures);
        this.all.sort(null);
        this.onPick = onPick;
        refilter();
    }

    private void refilter() {
        filtered = new ArrayList<>();
        String q = query.toLowerCase().trim();
        for (ResourceLocation id : all) {
            if (q.isEmpty() || id.toString().contains(q)) filtered.add(id);
        }
        scroll = 0;
    }

    @Override
    protected void init() {
        search = new EditBox(font, width / 2 - 100, HEADER / 2 - 5, 200, 14, Component.empty());
        search.setHint(Component.literal("Search"));
        search.setValue(query);
        search.setResponder(q -> {
            query = q;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private int columns() {
        return Math.max(1, (width - 16) / (THUMB + PAD));
    }

    private int cellHeight() {
        return THUMB + PAD + LABEL;
    }

    private int maxScroll() {
        int rows = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, rows * cellHeight() + 8 - (height - FOOTER - HEADER));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, width, HEADER, PANEL);
        g.fill(0, HEADER - 1, width, HEADER, BORDER);
        g.drawCenteredString(font, "Pick a texture", width / 2, 4, TEXT);

        int footerY = height - FOOTER;
        g.fill(0, footerY, width, height, PANEL);
        g.fill(0, footerY, width, footerY + 1, BORDER);
        g.drawString(font, filtered.size() + " textures  -  click to pick  -  Esc to cancel", 8, footerY + 10, FAINT,
                false);

        int cols = columns();
        hovered = -1;
        g.enableScissor(0, HEADER, width, footerY);
        for (int i = 0; i < filtered.size(); i++) {
            int tx = 8 + (i % cols) * (THUMB + PAD);
            int ty = HEADER + 4 - scroll + (i / cols) * cellHeight();
            if (ty + THUMB + LABEL < HEADER || ty > footerY) continue;

            boolean hover = mx >= tx && mx < tx + THUMB && my >= ty && my < ty + THUMB && my >= HEADER && my < footerY;
            if (hover) hovered = i;
            ResourceLocation id = filtered.get(i);

            for (int cy = 0; cy < THUMB; cy += 8) {
                for (int cx = 0; cx < THUMB; cx += 8) {
                    g.fill(tx + cx, ty + cy, tx + cx + 8, ty + cy + 8, ((cx + cy) / 8) % 2 == 0 ? 0xFF666666 : 0xFF444444);
                }
            }
            try {
                // the file behind a model texture id: namespace:textures/<path>.png
                AnimatedTexture.blit(g, ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                        "textures/" + id.getPath() + ".png"), tx, ty, THUMB, THUMB);
            } catch (Exception e) {
                g.fill(tx, ty, tx + THUMB, ty + THUMB, 0xFF441144);
                g.drawCenteredString(font, "?", tx + THUMB / 2, ty + THUMB / 2 - 4, 0xFFFF4444);
            }
            if (hover) {
                g.fill(tx - 1, ty - 1, tx + THUMB + 1, ty, ACCENT);
                g.fill(tx - 1, ty + THUMB, tx + THUMB + 1, ty + THUMB + 1, ACCENT);
                g.fill(tx - 1, ty - 1, tx, ty + THUMB + 1, ACCENT);
                g.fill(tx + THUMB, ty - 1, tx + THUMB + 1, ty + THUMB + 1, ACCENT);
            }

            String name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            if (font.width(name) > THUMB) name = font.plainSubstrByWidth(name, THUMB - 4) + "..";
            g.drawString(font, name, tx, ty + THUMB + 2, FAINT, false);
        }
        g.disableScissor();
        super.render(g, mx, my, partial);
        if (hovered >= 0 && hovered < filtered.size()) {
            g.renderTooltip(font, Component.literal(filtered.get(hovered).toString()), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && hovered >= 0 && hovered < filtered.size()) {
            onPick.accept(filtered.get(hovered));
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (scrollY * (THUMB + PAD))));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
