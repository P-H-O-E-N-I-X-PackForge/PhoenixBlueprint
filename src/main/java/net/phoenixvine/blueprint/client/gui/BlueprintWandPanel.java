package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.client.gui.PanelFit;
import net.phoenixvine.blueprint.client.gui.hud.CanvasPainter;
import net.phoenixvine.blueprint.client.gui.hud.CanvasWidget;
import net.phoenixvine.blueprint.client.gui.hud.GuiDraw;
import net.phoenixvine.blueprint.client.gui.hud.HudModel;
import net.phoenixvine.blueprint.client.gui.hud.HudTheme;
import net.phoenixvine.blueprint.common.ShapeKind;
import net.phoenixvine.blueprint.common.ShapePlan;
import net.phoenixvine.blueprint.common.ShapeSpec;
import net.phoenixvine.blueprint.common.BlueprintWandSyncHandler;
import net.phoenixvine.blueprint.client.WandPickers;
import net.phoenixvine.blueprint.common.export.KubeJsExport;
import net.phoenixvine.blueprint.common.export.MachineSettings;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The blueprint wand's editor, drawn like the machine screens (themed backdrop, accent-bordered buttons, bar sliders). Every
 * change goes to the server through {@link BlueprintWandSyncHandler}, which edits the plan on the wand; what is shown is read back
 * from the wand, so the hologram in the world follows as you edit.
 */
public final class BlueprintWandPanel {

    private static final int WIDTH = 340;
    private static final int HEIGHT = 280;
    private static final HudTheme T = HudTheme.TEAL;

    /** The pickers replace the editor, so the machine page is remembered to come back to it. */
    private static boolean rememberMachinePage = false;

    private BlueprintWandPanel() {}

    public static ModularPanel<?> build(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        BlueprintWandSyncHandler handler = new BlueprintWandSyncHandler(data::getUsedItemStack,
                data.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null);
        sync.syncValue("blueprint_wand", handler);

        EditorState state = new EditorState();
        state.machinePage = rememberMachinePage;
        ModularPanel<?> panel = ModularPanel.defaultPanel("blueprint_wand", WIDTH, HEIGHT);
        panel.background(T.background());
        PanelFit.apply(panel, WIDTH, HEIGHT);
        panel.onUpdateListener(p -> {
            ItemStack stack = data.getUsedItemStack();
            if (stack != null && !stack.isEmpty()) {
                state.plan = ShapePlan.read(stack);
                state.selection = WandSelection.read(stack);
                state.machine = MachineSettings.read(stack);
            }
        });

        CanvasWidget canvas = new CanvasWidget(new Editor(state, handler));
        canvas.size(WIDTH, HEIGHT);
        canvas.pos(0, 0);
        canvas.fittedTo(WIDTH, HEIGHT);
        panel.child(canvas);
        return panel;
    }

    /** The plan on the wand as of this frame, read once so everything drawn sees the same one. */
    private static final class EditorState {

        ShapePlan plan = new ShapePlan();
        WandSelection selection = new WandSelection();
        MachineSettings machine = new MachineSettings();
        boolean machinePage = false;
    }

    private enum Kind {
        SLIDER,
        CHIP
    }

    /** One thing that can be clicked, found again from where it was drawn. */
    private record Control(int id, Kind kind, int x, int y, int w, int h, int min, int max, int step) {

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class Editor implements CanvasPainter {

        // sliders
        private static final int SIZE_0 = 0; // 0..3
        private static final int WALL = 10;
        private static final int YAW = 11;
        private static final int PITCH = 12;
        private static final int ROLL = 13;
        private static final int OFFSET_X = 14;
        private static final int OFFSET_Y = 15;
        private static final int OFFSET_Z = 16;
        // chips
        private static final int SHAPE = 100; // + ordinal
        private static final int PART = 200; // + index
        private static final int ADD_PART = 220;
        private static final int REMOVE_PART = 221;
        private static final int PRESET = 230; // + 0..3
        private static final int HOLLOW = 240;
        private static final int SIT_ON_BASE = 241;
        private static final int OVERWRITE = 242;
        private static final int CONNECTED = 243;
        private static final int UNDO = 250;
        private static final int RESET = 251;
        private static final int MODE_BUILD = 270;
        private static final int MODE_EXPORT = 271;
        private static final int X_CONTROLLER = 272;
        private static final int X_SELECT_BUILD = 273;
        private static final int X_CLEAR = 274;
        private static final int X_COPY = 275;
        private static final int X_CASE = 276;
        private static final int X_AIR = 277;
        private static final int X_PARTS = 278;
        private static final int X_STYLE = 279;
        private static final int X_LANGUAGE = 280;
        private static final int X_VERSION = 281;
        private static final int X_STRUCTURE = 300; // + index
        private static final int X_ADD_STRUCTURE = 310;
        private static final int X_PREVIEW = 312;
        private static final int X_MACHINE_PAGE = 313;
        private static final int X_RENAME = 314;
        private static final int M_BACK = 320;
        private static final int M_NAME = 321;
        private static final int M_ROTATION = 322;
        private static final int M_ADD_TYPE = 323;
        private static final int M_CLEAR_TYPES = 324;
        private static final int M_ADD_MODIFIER = 325;
        private static final int M_CLEAR_MODIFIERS = 326;
        private static final int M_APPEARANCE = 327;
        private static final int M_CASING = 328;
        private static final int M_OVERLAY = 329;
        private static final int M_OUTPUT = 330;
        private static final int X_REMOVE_STRUCTURE = 311;
        private static final int PIN_PART = 260;
        private static final int PIN_ALL = 261;

        private final EditorState state;
        private final BlueprintWandSyncHandler handler;
        private final List<Control> controls = new ArrayList<>();
        private Control dragging;
        private int lastSent = Integer.MIN_VALUE;

        Editor(EditorState state, BlueprintWandSyncHandler handler) {
            this.state = state;
            this.handler = handler;
        }

        // ------------------------------------------------------------------ drawing

        @Override
        public void render(GuiGraphics g, int w, int h, int mouseX, int mouseY) {
            Font font = Minecraft.getInstance().font;
            controls.clear();
            ShapePlan plan = state.plan;
            ShapeSpec spec = plan.current();

            // header
            g.drawString(font, "BLUEPRINT", 10, 8, T.accent(), false);
            String summary = ShapeHologramInfo.summary();
            int summaryColor = ShapeHologramInfo.tooBig ? HudModel.RED :
                    ShapeHologramInfo.haveBlock ? HudModel.GREEN : HudModel.GOLD;
            g.drawString(font, summary, w - 10 - font.width(summary), 8, summaryColor, false);
            g.fill(10, 20, w - 10, 21, T.line());

            // build or export: the same wand does both
            boolean exporting = state.selection.exportMode;
            chip(g, font, MODE_BUILD, "BUILD", w - 10 - 98, 25, 46, !exporting, mouseX, mouseY);
            chip(g, font, MODE_EXPORT, "EXPORT", w - 10 - 48, 25, 48, exporting, mouseX, mouseY);
            if (exporting) {
                if (state.machinePage) renderMachine(g, font, w, h, mouseX, mouseY);
                else renderExport(g, font, w, h, mouseX, mouseY);
                return;
            }

            // the parts of the plan
            int x = 10;
            g.drawString(font, "PARTS", x, 28, HudModel.DIM, false);
            x += 34;
            for (int i = 0; i < plan.parts.size(); i++) {
                chip(g, font, PART + i, (i + 1) + (plan.parts.get(i).locked ? "*" : ""), x, 25, 16, plan.selected == i, mouseX, mouseY);
                x += 18;
            }
            chip(g, font, ADD_PART, "+", x + 4, 25, 16, false, mouseX, mouseY);
            chip(g, font, REMOVE_PART, "-", x + 22, 25, 16, false, mouseX, mouseY);

            // left column: the shape and its sizes
            int lx = 10;
            g.drawString(font, "SHAPE", lx, 44, HudModel.DIM, false);
            ShapeKind[] kinds = ShapeKind.values();
            for (int i = 0; i < kinds.length; i++) {
                int col = i % 3;
                int row = i / 3;
                chip(g, font, SHAPE + i, kinds[i].displayName(), lx + col * 62, 55 + row * 14, 60,
                        kinds[i] == spec.kind, mouseX, mouseY);
            }
            int sy = 55 + ((kinds.length + 2) / 3) * 14 + 6;
            g.drawString(font, "SIZE", lx, sy, HudModel.DIM, false);
            for (int i = 0; i < 4; i++) {
                ShapeKind.Param param = spec.kind.param(i);
                if (param == null) continue;
                slider(g, font, SIZE_0 + i, param.label(), lx, sy + 11 + i * 14, 184, 74, spec.size[i], param.min(),
                        param.max(), 1, mouseX, mouseY);
            }
            int hy = sy + 11 + 4 * 14 + 2;
            chip(g, font, HOLLOW, spec.hollow ? "HOLLOW" : "SOLID", lx, hy, 60, spec.hollow, mouseX, mouseY);
            slider(g, font, WALL, "Wall", lx + 66, hy, 118, 30, spec.wall, 1, 16, 1, mouseX, mouseY);

            // right column: how it is turned and where it sits
            int rx = 206;
            int rw = w - 10 - rx;
            g.drawString(font, "ROTATION", rx, 44, HudModel.DIM, false);
            slider(g, font, YAW, "Yaw", rx, 55, rw, 34, spec.yaw, 0, 345, 15, mouseX, mouseY);
            slider(g, font, PITCH, "Pitch", rx, 69, rw, 34, spec.pitch, 0, 345, 15, mouseX, mouseY);
            slider(g, font, ROLL, "Roll", rx, 83, rw, 34, spec.roll, 0, 345, 15, mouseX, mouseY);
            String[] presets = { "Flat", "Face Z", "Face X", "Tilt 45" };
            int half = (rw - 4) / 2;
            for (int i = 0; i < 4; i++) {
                chip(g, font, PRESET + i, presets[i], rx + (i % 2) * (half + 4), 99 + (i / 2) * 14, half, false,
                        mouseX, mouseY);
            }

            g.drawString(font, "OFFSET", rx, 132, HudModel.DIM, false);
            slider(g, font, OFFSET_X, "X", rx, 143, rw, 34, spec.dx, -64, 64, 1, mouseX, mouseY);
            slider(g, font, OFFSET_Y, "Y", rx, 157, rw, 34, spec.dy, -64, 64, 1, mouseX, mouseY);
            slider(g, font, OFFSET_Z, "Z", rx, 171, rw, 34, spec.dz, -64, 64, 1, mouseX, mouseY);

            chip(g, font, SIT_ON_BASE, "SIT ON THE BASE", rx, 192, rw, plan.sitOnBase, mouseX, mouseY);
            chip(g, font, CONNECTED, "NO DIAGONAL GAPS", rx, 206, rw, plan.connected, mouseX, mouseY);
            chip(g, font, OVERWRITE, "OVERWRITE BLOCKS", rx, 220, rw, plan.overwrite, mouseX, mouseY);

            // pins: a pinned part stays where it was pinned however the wand is aimed
            boolean allPinned = plan.parts.stream().allMatch(part -> part.locked);
            int half2 = (rw - 4) / 2;
            chip(g, font, PIN_PART, spec.locked ? "UNPIN PART" : "PIN PART", rx, 236, half2, spec.locked, mouseX, mouseY);
            chip(g, font, PIN_ALL, allPinned ? "UNPIN ALL" : "PIN ALL", rx + half2 + 4, 236, half2, allPinned,
                    mouseX, mouseY);
            String pinNote = spec.locked ? "Pinned at " + spec.pinX + " " + spec.pinY + " " + spec.pinZ :
                    "Follows the aim";
            if (ShapeHologramInfo.pinnedElsewhere > 0) pinNote = ShapeHologramInfo.pinnedElsewhere + " pinned in another dimension";
            g.drawString(font, font.plainSubstrByWidth(pinNote, 190), lx, 238, spec.locked ? T.accent() : HudModel.DIM,
                    false);

            // footer
            g.fill(10, h - 24, w - 10, h - 23, T.line());
            chip(g, font, UNDO, "UNDO", 10, h - 19, 44, false, mouseX, mouseY);
            chip(g, font, RESET, "RESET", 58, h - 19, 44, false, mouseX, mouseY);
            int noteY = h - 20;
            for (var line : font.split(net.minecraft.network.chat.Component.literal(
                    "Builds with the block in your other hand. Right-click builds, left-click undoes."), w - 120 - 10)) {
                g.drawString(font, line, 112, noteY, HudModel.DIM, false);
                noteY += 10;
            }
        }

        /** The export page: pick the blocks of a built multiblock and copy them as KubeJS for GTM's pattern builder. */
        private void renderExport(GuiGraphics g, Font font, int w, int h, int mouseX, int mouseY) {
            WandSelection sel = state.selection;
            KubeJsExport.Options o = sel.options;

            // the machine's structures: the default "main" and its substructures
            g.drawString(font, "PARTS", 10, 28, HudModel.DIM, false);
            int cx = 44;
            for (int i = 0; i < sel.structureCount(); i++) {
                chip(g, font, X_STRUCTURE + i, i == 0 ? "M" : String.valueOf(i), cx, 25, 16, sel.currentIndex() == i,
                        mouseX, mouseY);
                cx += 18;
            }
            if (sel.structureCount() < WandSelection.MAX_STRUCTURES) {
                chip(g, font, X_ADD_STRUCTURE, "+", cx + 4, 25, 16, false, mouseX, mouseY);
            }
            if (sel.currentIndex() > 0) {
                chip(g, font, X_REMOVE_STRUCTURE, "-", cx + 22, 25, 16, false, mouseX, mouseY);
            }

            String selectionLabel = "SELECTION: " + sel.name();
            g.drawString(font, selectionLabel, 10, 46, HudModel.DIM, false);
            if (sel.currentIndex() > 0) {
                chip(g, font, X_RENAME, "RENAME", 10 + font.width(selectionLabel) + 6, 43, 46, false, mouseX, mouseY);
            }
            g.drawString(font, "Corner 1: " + pos(sel.corner1), 10, 58, HudModel.WHITE, false);
            g.drawString(font, "Corner 2: " + pos(sel.corner2), 10, 69, HudModel.WHITE, false);
            g.drawString(font, "Controller: " + (sel.controller == null ? "- (optional)" : pos(sel.controller)), 10, 80,
                    HudModel.GOLD, false);
            if (sel.hasBothCorners()) {
                var low = sel.low();
                var high = sel.high();
                int sx = high.getX() - low.getX() + 1, sy = high.getY() - low.getY() + 1,
                        sz = high.getZ() - low.getZ() + 1;
                g.drawString(font, sx + " x " + sy + " x " + sz + "  (" + (long) sx * sy * sz + " blocks)", 10, 91,
                        T.accent(), false);
            }

            int rx = 206;
            int rw = w - 10 - rx;
            chip(g, font, X_CONTROLLER, sel.controllerMode ? "MARKING CONTROLLER" : "MARK CONTROLLER", rx, 46, rw,
                    sel.controllerMode, mouseX, mouseY);
            chip(g, font, X_SELECT_BUILD, "SELECT LAST BUILD", rx, 60, rw, false, mouseX, mouseY);
            chip(g, font, X_CLEAR, "CLEAR SELECTION", rx, 74, rw, false, mouseX, mouseY);
            chip(g, font, X_COPY, "COPY OUTPUT", rx, 90, rw, sel.hasBothCorners(), mouseX, mouseY);
            if (net.phoenixvine.blueprint.client.ShapeExportClient.phantasiaAvailable()) {
                chip(g, font, X_PREVIEW, "PREVIEW (PHANTASIA)", rx, 104, rw, false, mouseX, mouseY);
            }
            chip(g, font, X_MACHINE_PAGE, "MACHINE DEFINITION >", rx, 118, rw,
                    o.style() == KubeJsExport.STYLE_MACHINE, mouseX, mouseY);

            g.drawString(font, "OUTPUT", 10, 112, HudModel.DIM, false);
            chip(g, font, X_CASE, "Letters: " + (o.uppercaseFirst() ? "A-Z first, then a-z" : "a-z first, then A-Z"), 10, 124,
                    190, false, mouseX, mouseY);
            chip(g, font, X_AIR, "Air: " + switch (o.air()) {
                case KubeJsExport.AIR_PREDICATE -> "Predicates.air()";
                case KubeJsExport.AIR_BLOCK -> "a plain block";
                default -> "a space (anything)";
            }, 10, 138, 190, false, mouseX, mouseY);
            chip(g, font, X_PARTS, "Hatches and buses: " + (o.partsAsAbilities() ? "as abilities" : "exact blocks"), 10,
                    152, 190, false, mouseX, mouseY);
            String slice = o.gt7() ? "aisle" : "slice";
            chip(g, font, X_STYLE, "Output: " + switch (o.style()) {
                case KubeJsExport.STYLE_MACHINE -> "the full machine definition";
                case KubeJsExport.STYLE_FULL -> "full .pattern(...)";
                default -> "just the pattern (." + slice + " and .where)";
            }, 10,
                    166, 190, false, mouseX, mouseY);

            chip(g, font, X_LANGUAGE, "Language: " + (o.java() ? "Java (registry fields)" : "KubeJS"), 10, 180, 190, o.java(),
                    mouseX, mouseY);
            chip(g, font, X_VERSION, "GregTech: " + (o.gt7() ? "7 (1.20.1, .aisle)" : "8 (1.21.1, .slice)"), 10, 194, 190,
                    o.gt7(), mouseX, mouseY);

            int y = 212;
            String help = "Right-click a block: corner 2 (sneak: corner 1). Mark the controller first if it is not a " +
                    "block facing south. Right-click air or COPY to put the KubeJS on your clipboard. Build a design " +
                    "in BUILD mode, then SELECT LAST BUILD.";
            for (var line : font.split(net.minecraft.network.chat.Component.literal(help), w - 20)) {
                g.drawString(font, line, 10, y, HudModel.DIM, false);
                y += 10;
            }

            g.fill(10, h - 24, w - 10, h - 23, T.line());
            g.drawString(font, "Sneak + right-click air opens this editor.", 10, h - 18, HudModel.DIM, false);
        }

        /** The machine page: what a full machine definition needs besides the patterns. */
        private void renderMachine(GuiGraphics g, Font font, int w, int h, int mouseX, int mouseY) {
            MachineSettings m = state.machine;
            boolean pickers = true;
            chip(g, font, M_BACK, "< BACK", 10, 25, 50, false, mouseX, mouseY);
            g.drawString(font, "MACHINE DEFINITION", 66, 28, HudModel.DIM, false);

            int label = 10;
            int value = 104;
            int vw = w - 10 - value;
            g.drawString(font, "Class", label, 48, HudModel.DIM, false);
            g.drawString(font, "WorkableElectricMultiblockMachine", value, 48, T.accent(), false);

            g.drawString(font, "Id", label, 63, HudModel.DIM, false);
            chip(g, font, M_NAME, m.id(), value, 60, vw, false, mouseX, mouseY);

            g.drawString(font, "Rotation", label, 77, HudModel.DIM, false);
            chip(g, font, M_ROTATION, m.rotationName(), value, 74, vw, false, mouseX, mouseY);

            g.drawString(font, "Recipe types", label, 91, HudModel.DIM, false);
            listRow(g, font, m.recipeTypes, 88, value, vw, M_ADD_TYPE, M_CLEAR_TYPES, pickers, mouseX, mouseY);

            g.drawString(font, "Modifiers", label, 105, HudModel.DIM, false);
            listRow(g, font, m.recipeModifiers, 102, value, vw, M_ADD_MODIFIER, M_CLEAR_MODIFIERS, pickers, mouseX,
                    mouseY);

            String controllerBlock = null;
            var level = Minecraft.getInstance().level;
            if (level != null) {
                for (var s : state.selection.structures()) {
                    if (s.controller != null && !level.getBlockState(s.controller).isAir()) {
                        controllerBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(level.getBlockState(s.controller).getBlock()).toString();
                        break;
                    }
                }
            }
            String appearanceShown = !m.appearanceBlock.isEmpty() ? m.appearanceBlock :
                    controllerBlock != null ? "(controller) " + controllerBlock :
                            "pick a block";
            g.drawString(font, "Appearance", label, 119, HudModel.DIM, false);
            chip(g, font, M_APPEARANCE, appearanceShown, value, 116, vw, !m.appearanceBlock.isEmpty(), mouseX, mouseY);

            String casingShown = !m.casingTexture.isEmpty() ? m.casingTexture :
                    !m.derivedCasingTexture.isEmpty() ? "(from the block) " + m.derivedCasingTexture :
                            "pick a texture";
            g.drawString(font, "Casing texture", label, 133, HudModel.DIM, false);
            chip(g, font, M_CASING, casingShown, value, 130, vw, !m.casingTexture.isEmpty(), mouseX, mouseY);

            g.drawString(font, "Overlay texture", label, 147, HudModel.DIM, false);
            chip(g, font, M_OVERLAY, m.overlayTexture.isEmpty() ? "pick a texture" :
                    m.overlayTexture, value, 144, vw, !m.overlayTexture.isEmpty(), mouseX, mouseY);

            boolean on = state.selection.options.style() == KubeJsExport.STYLE_MACHINE;
            chip(g, font, M_OUTPUT, on ? "OUTPUT: THE FULL MACHINE DEFINITION" : "OUTPUT: JUST THE PATTERN (CLICK FOR MACHINE)",
                    10, 168, 230, on, mouseX, mouseY);

            int y = 190;
            String help = "With the output set to the full machine, COPY OUTPUT writes the registration: the id, rotation, "
                    + "recipe types and modifiers, appearance block, every structure as a pattern, and the casing model. "
                    + "Blocks that match the appearance block (and hatches, when written as abilities) become the casing, "
                    + "which also allows maintenance and the recipe types' hatches.";
            for (var line : font.split(net.minecraft.network.chat.Component.literal(help), w - 20)) {
                g.drawString(font, line, 10, y, HudModel.DIM, false);
                y += 10;
            }
        }

        private void listRow(GuiGraphics g, Font font, java.util.List<String> values, int y, int x, int vw, int addId,
                             int clearId, boolean pickers, int mouseX, int mouseY) {
            int buttons = 38 + 4 + 44;
            String text = values.isEmpty() ? "none" : String.join(", ", values);
            g.drawString(font, font.plainSubstrByWidth(text, vw - buttons - 6), x, y + 3,
                    values.isEmpty() ? HudModel.DIM : HudModel.WHITE, false);
            chip(g, font, addId, "+ ADD", x + vw - buttons, y, 38, false, mouseX, mouseY);
            chip(g, font, clearId, "CLEAR", x + vw - 44, y, 44, false, mouseX, mouseY);
        }

        private static String pos(net.minecraft.core.BlockPos pos) {
            return pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private void chip(GuiGraphics g, Font font, int id, String text, int x, int y, int w, boolean active, int mx,
                          int my) {
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + 12;
            int fill = active ? GuiDraw.mix(T.bgTop(), T.accent(), 0.28f) : hover ?
                    GuiDraw.mix(T.bgTop(), T.accent(), 0.14f) : T.bgTop();
            g.fill(x, y, x + w, y + 12, fill);
            GuiDraw.drawBorder(g, x, y, w, 12, active || hover ? T.accent() : GuiDraw.withAlpha(T.accent(), 0x77), 1);
            String shown = font.plainSubstrByWidth(text, w - 6);
            g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + 2, active ? HudModel.WHITE : T.accent(),
                    false);
            controls.add(new Control(id, Kind.CHIP, x, y, w, 12, 0, 0, 0));
        }

        private void slider(GuiGraphics g, Font font, int id, String label, int x, int y, int w, int labelW,
                            int value, int min, int max, int step, int mx, int my) {
            int bx = x + labelW;
            int valueW = font.width("-128") + 4;
            int bw = Math.max(20, w - labelW - valueW);
            g.drawString(font, font.plainSubstrByWidth(label, labelW - 2), x, y + 2, HudModel.DIM, false);

            g.fill(bx, y + 4, bx + bw, y + 8, T.dim());
            GuiDraw.drawBorder(g, bx, y + 3, bw, 6, GuiDraw.withAlpha(T.accent(), 0x66), 1);
            float frac = (value - min) / (float) Math.max(1, max - min);
            g.fill(bx + 1, y + 4, bx + 1 + Math.round((bw - 2) * Mth.clamp(frac, 0f, 1f)), y + 8,
                    GuiDraw.withAlpha(T.accent(), 0x88));
            int hx = bx + Math.round((bw - 4) * Mth.clamp(frac, 0f, 1f));
            boolean hover = mx >= bx - 2 && mx < bx + bw + 2 && my >= y && my < y + 12;
            g.fill(hx, y + 1, hx + 4, y + 11, hover || dragging != null && dragging.id() == id ? HudModel.WHITE :
                    T.accent());
            g.drawString(font, String.valueOf(value), bx + bw + 6, y + 2, HudModel.WHITE, false);
            controls.add(new Control(id, Kind.SLIDER, bx - 2, y, bw + 4, 12, min, max, step));
        }

        // ------------------------------------------------------------------ input

        @Override
        public boolean click(double mx, double my, int button) {
            if (button != 0) return false;
            for (Control c : controls) {
                if (!c.contains(mx, my)) continue;
                if (c.kind() == Kind.SLIDER) {
                    dragging = c;
                    lastSent = Integer.MIN_VALUE;
                    setFromMouse(c, mx);
                } else {
                    press(c.id());
                }
                return true;
            }
            return false;
        }

        @Override
        public void drag(double mx, double my, int button) {
            if (dragging != null) setFromMouse(dragging, mx);
        }

        @Override
        public void release(int button) {
            dragging = null;
        }

        @Override
        public boolean scroll(double mx, double my, double amount) {
            for (Control c : controls) {
                if (!c.contains(mx, my)) continue;
                int direction = amount > 0 ? 1 : -1;
                if (c.kind() == Kind.SLIDER) {
                    int step = c.step() * (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 3 : 1);
                    apply(c.id(), Mth.clamp(current(c.id()) + direction * step, c.min(), c.max()));
                    return true;
                }
                if (c.id() >= SHAPE && c.id() < SHAPE + ShapeKind.values().length) {
                    handler.changeKind(-direction);
                    return true;
                }
            }
            return false;
        }

        private void setFromMouse(Control c, double mx) {
            double t = Mth.clamp((mx - (c.x() + 2)) / Math.max(1, c.w() - 4), 0.0, 1.0);
            int raw = c.min() + (int) Math.round(t * (c.max() - c.min()));
            int value = c.step() > 1 ? Math.round(raw / (float) c.step()) * c.step() : raw;
            value = Mth.clamp(value, c.min(), c.max());
            if (value == lastSent) return;
            lastSent = value;
            apply(c.id(), value);
        }

        private int current(int id) {
            ShapeSpec spec = state.plan.current();
            return switch (id) {
                case SIZE_0, SIZE_0 + 1, SIZE_0 + 2, SIZE_0 + 3 -> spec.size[id - SIZE_0];
                case WALL -> spec.wall;
                case YAW -> spec.yaw;
                case PITCH -> spec.pitch;
                case ROLL -> spec.roll;
                case OFFSET_X -> spec.dx;
                case OFFSET_Y -> spec.dy;
                case OFFSET_Z -> spec.dz;
                default -> 0;
            };
        }

        private void apply(int id, int value) {
            switch (id) {
                case SIZE_0, SIZE_0 + 1, SIZE_0 + 2, SIZE_0 + 3 -> handler.setSize(id - SIZE_0, value);
                case WALL -> handler.setField(BlueprintWandSyncHandler.FIELD_WALL, value);
                case YAW -> handler.setField(BlueprintWandSyncHandler.FIELD_YAW, value);
                case PITCH -> handler.setField(BlueprintWandSyncHandler.FIELD_PITCH, value);
                case ROLL -> handler.setField(BlueprintWandSyncHandler.FIELD_ROLL, value);
                case OFFSET_X -> handler.setField(BlueprintWandSyncHandler.FIELD_DX, value);
                case OFFSET_Y -> handler.setField(BlueprintWandSyncHandler.FIELD_DY, value);
                case OFFSET_Z -> handler.setField(BlueprintWandSyncHandler.FIELD_DZ, value);
                default -> {}
            }
        }

        private void press(int id) {
            ShapePlan plan = state.plan;
            ShapeSpec spec = plan.current();
            if (id >= SHAPE && id < SHAPE + ShapeKind.values().length) {
                handler.setKind(id - SHAPE);
            } else if (id >= PART && id < PART + ShapePlan.MAX_PARTS) {
                handler.select((id - PART) - plan.selected);
            } else if (id >= X_STRUCTURE && id < X_STRUCTURE + WandSelection.MAX_STRUCTURES) {
                handler.export(ShapeExportActions.SELECT_STRUCTURE + (id - X_STRUCTURE));
            } else {
                switch (id) {
                    case ADD_PART -> handler.addPart();
                    case REMOVE_PART -> handler.removePart();
                    case PRESET, PRESET + 1, PRESET + 2, PRESET + 3 -> handler.preset(id - PRESET);
                    case HOLLOW -> handler.setFlag(BlueprintWandSyncHandler.FLAG_HOLLOW, !spec.hollow);
                    case SIT_ON_BASE -> handler.setFlag(BlueprintWandSyncHandler.FLAG_SIT_ON_BASE, !plan.sitOnBase);
                    case OVERWRITE -> handler.setFlag(BlueprintWandSyncHandler.FLAG_OVERWRITE, !plan.overwrite);
                    case CONNECTED -> handler.setFlag(BlueprintWandSyncHandler.FLAG_CONNECTED, !plan.connected);
                    case UNDO -> handler.undo();
                    case RESET -> handler.reset();
                    case MODE_BUILD -> handler.export(ShapeExportActions.SET_BUILD);
                    case MODE_EXPORT -> handler.export(ShapeExportActions.SET_EXPORT);
                    case X_CONTROLLER -> handler.export(ShapeExportActions.TOGGLE_CONTROLLER);
                    case X_SELECT_BUILD -> handler.export(ShapeExportActions.SELECT_BUILD);
                    case X_CLEAR -> handler.export(ShapeExportActions.CLEAR);
                    case X_COPY -> handler.export(ShapeExportActions.COPY);
                    case X_CASE -> handler.export(ShapeExportActions.CYCLE_CASE);
                    case X_AIR -> handler.export(ShapeExportActions.CYCLE_AIR);
                    case X_PARTS -> handler.export(ShapeExportActions.TOGGLE_PARTS);
                    case X_STYLE -> handler.export(ShapeExportActions.TOGGLE_STYLE);
                    case X_LANGUAGE -> handler.export(ShapeExportActions.TOGGLE_LANGUAGE);
                    case X_VERSION -> handler.export(ShapeExportActions.TOGGLE_VERSION);
                    case X_ADD_STRUCTURE -> handler.export(ShapeExportActions.ADD_STRUCTURE);
                    case X_MACHINE_PAGE -> state.machinePage = rememberMachinePage = true;
                    case M_BACK -> state.machinePage = rememberMachinePage = false;
                    case X_RENAME -> WandPickers.typeName(MachineSettings.RENAME_STRUCTURE, "Name this structure",
                            state.selection.name());
                    case M_NAME -> WandPickers.typeName(MachineSettings.NAME, "Machine id", state.machine.id());
                    case M_ROTATION -> handler.export(ShapeExportActions.CYCLE_ROTATION);
                    case M_ADD_TYPE -> WandPickers.pickRecipeType();
                    case M_CLEAR_TYPES -> handler.export(ShapeExportActions.CLEAR_TYPES);
                    case M_ADD_MODIFIER -> WandPickers.pickRecipeModifier();
                    case M_CLEAR_MODIFIERS -> handler.export(ShapeExportActions.CLEAR_MODIFIERS);
                    case M_APPEARANCE -> WandPickers.pickBlock(MachineSettings.APPEARANCE, "Appearance block");
                    case M_CASING -> WandPickers.pickTexture(MachineSettings.CASING_TEXTURE, "Casing texture");
                    case M_OVERLAY -> WandPickers.pickTexture(MachineSettings.OVERLAY_TEXTURE, "Overlay texture");
                    case M_OUTPUT -> handler.export(ShapeExportActions.TOGGLE_MACHINE_STYLE);
                    case X_PREVIEW -> {
                        var structures = state.selection.structures();
                        net.phoenixvine.blueprint.client.ShapeExportClient.openPreview(
                                structures.get(state.selection.currentIndex()));
                    }
                    case X_REMOVE_STRUCTURE -> handler.export(ShapeExportActions.REMOVE_STRUCTURE);
                    case PIN_PART, PIN_ALL -> {
                        boolean all = id == PIN_ALL;
                        boolean pinned = all ? plan.parts.stream().allMatch(part -> part.locked) : spec.locked;
                        net.minecraft.core.BlockPos at = net.phoenixvine.blueprint.client.ShapeHologram.anchor();
                        if (pinned) handler.unpin(all);
                        else if (at != null) handler.pin(all, at, net.phoenixvine.blueprint.client.ShapeHologram.dimension());
                    }
                    default -> {}
                }
            }
        }
    }
}
