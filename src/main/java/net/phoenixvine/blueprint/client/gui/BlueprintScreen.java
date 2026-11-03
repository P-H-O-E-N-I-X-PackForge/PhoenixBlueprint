package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.client.ImportHologram;
import net.phoenixvine.blueprint.client.ShapeExportClient;
import net.phoenixvine.blueprint.client.ShapeHologram;
import net.phoenixvine.blueprint.client.WandEditor;
import net.phoenixvine.blueprint.client.PlanLibrary;
import net.phoenixvine.blueprint.client.WandPickers;
import net.phoenixvine.blueprint.client.gui.hud.GuiDraw;
import net.phoenixvine.blueprint.client.gui.hud.HudModel;
import net.phoenixvine.blueprint.client.gui.hud.HudTheme;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.ShapeKind;
import net.phoenixvine.blueprint.common.ShapePlan;
import net.phoenixvine.blueprint.common.ShapeSpec;
import net.phoenixvine.blueprint.common.WandEdits;
import net.phoenixvine.blueprint.common.export.ExportCheck;
import net.phoenixvine.blueprint.common.export.KubeJsExport;
import net.phoenixvine.blueprint.common.export.PredicateRules;
import net.phoenixvine.blueprint.common.export.MachineSettings;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The blueprint wand's editor: a vanilla screen drawn like the machine screens (themed backdrop, accent-bordered buttons, bar
 * sliders). Every change is applied to the wand in the hand at once and sent to the server ({@link WandEditor}), so what is
 * shown and the hologram in the world follow as you click. The screen is laid out at a fixed size and scaled to fit the
 * window, so it stays usable at any GUI scale. Pickers open on top of it and come back to it.
 */
public final class BlueprintScreen extends Screen {

    private static final int W = 380;
    private static final int H = 336;
    private static final HudTheme T = HudTheme.TEAL;

    /** How long a click lights its button, and how long the latest message stays in the footer. */
    private static final long FLASH_MS = 260;
    private static final long STATUS_MS = 6000;

    // sliders
    private static final int SIZE_0 = 0; // 0..3
    private static final int WALL = 10;
    private static final int YAW = 11;
    private static final int PITCH = 12;
    private static final int ROLL = 13;
    private static final int OFFSET_X = 14;
    private static final int OFFSET_Y = 15;
    private static final int OFFSET_Z = 16;
    private static final int ARRAY_COUNT = 17;
    private static final int ARRAY_SPACING = 18;
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
    private static final int PIN_PART = 260;
    private static final int PIN_ALL = 261;
    private static final int ORIENT = 262;
    private static final int LIB_TAB = 500;
    private static final int LIB_SAVE = 501;
    private static final int LIB_LOAD = 502;
    private static final int LIB_DELETE = 503;
    private static final int LIB_COPY = 504;
    private static final int LIB_PASTE = 505;
    private static final int LIB_FOLDER = 506;
    private static final int LIB_ROW = 520;
    private static final int LIB_TOP = 40;
    private static final int LIB_ROWS = 13;
    private static final int LIB_ROW_H = 14;
    private static final int LIB_BAR_X = 228;
    private static final int ARRAY_MODE = 264;
    private static final int MIRROR_X = 265;
    private static final int MIRROR_Y = 266;
    private static final int MIRROR_Z = 267;
    private static final int PART_BLOCK = 268;
    private static final int OPEN_ENDS = 285;
    private static final int SEND_EXPORT = 269;

    // the shape list: a window of this many rows onto all the shapes, scrolled with the wheel or the bar beside it
    private static final int SHAPE_TOP = 55;
    private static final int SHAPE_ROWS = 4;
    private static final int SHAPE_BAR_X = 226;
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
    private static final int X_REMOVE_STRUCTURE = 311;
    private static final int X_PREVIEW = 312;
    private static final int X_MACHINE_PAGE = 313;
    private static final int X_RENAME = 314;
    private static final int X_AUTO_SELECT = 315;
    private static final int X_OPEN_FOLDER = 316;
    private static final int X_IMPORT = 317;
    private static final int X_IMPORT_TURN = 318;
    private static final int X_IMPORT_PLACE = 319;
    private static final int X_IMPORT_CLEAR = 331;
    private static final int X_IMPORT_LOCK = 335;
    private static final int X_NUDGE = 340; // + 0..5
    private static final int X_RULES_PAGE = 332;
    private static final int R_BACK = 333;
    private static final int R_RESET_ALL = 334;
    /** The buttons of the limits page are numbered from here: 8 per row, then which button (see ROW_*). */
    private static final int R_ROWS = 1000;
    private static final int ROW_MODE = 0;
    private static final int ROW_MINUS = 1;
    private static final int ROW_PLUS = 2;
    private static final int ROW_HATCHES = 3;
    private static final int ROW_MAINTENANCE = 4;
    private static final int ROW_CONTROLLER = 5;
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

    private enum Kind {
        SLIDER,
        CHIP
    }

    /** One thing that can be clicked, found again from where it was drawn. */
    private record Control(int id, Kind kind, int x, int y, int w, int h, int min, int max, int step, String label) {

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final InteractionHand hand;

    // read from the wand each frame, so everything drawn in a frame sees the same one
    private ShapePlan plan = new ShapePlan();
    private WandSelection selection = new WandSelection();
    private MachineSettings machine = new MachineSettings();
    private PredicateRules limits = new PredicateRules();

    private boolean machinePage = false;
    private boolean rulesPage = false;
    private int rulesScroll = 0;
    private final List<Control> controls = new ArrayList<>();
    private final Map<Integer, Long> flashes = new HashMap<>();
    private Control dragging;
    private int shapeScroll = 0;
    private int shapeMaxScroll = 0;
    private int shapeBarThumb = 8;
    private boolean shapeBarDrag = false;
    private ShapeKind shownKind;

    // the library tab: the plans saved on this computer
    private boolean libraryTab = false;
    private boolean libDirty = true;
    private java.util.List<PlanLibrary.Entry> libEntries = new java.util.ArrayList<>();
    private String libSelected = "";
    private ShapePlan libPreview;
    private int libScroll = 0;
    private int libMaxScroll = 0;
    private int libBarThumb = 8;
    private boolean libBarDrag = false;
    private boolean libConfirmDelete = false;
    private int lastSent = Integer.MIN_VALUE;

    private String status = "";
    private int statusColor = HudModel.WHITE;
    private long statusAt = 0;

    private String tooltip;

    public BlueprintScreen(InteractionHand hand) {
        super(Component.literal("Blueprint"));
        this.hand = hand;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A note from the client (an export was saved...) shown in the footer. */
    public void note(String text) {
        status(text, T.accent());
    }

    /** A message from the server (copied, selected, nothing to undo...) shown in the footer. */
    public void serverMessage(String text) {
        status(text, HudModel.GOLD);
    }

    private void status(String text, int color) {
        status = text;
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ the window

    private float scale() {
        float margin = 8f;
        float fit = Math.min(1f, Math.min((height - 2 * margin) / H, (width - 2 * margin) / W));
        return PixelScale.snap(Math.max(0.3f, fit));
    }

    private int originX() {
        return Math.round((width - W * scale()) / 2f);
    }

    private int originY() {
        return Math.round((height - H * scale()) / 2f);
    }

    private double localX(double mx) {
        return (mx - originX()) / scale();
    }

    private double localY(double my) {
        return (my - originY()) / scale();
    }

    private @org.jetbrains.annotations.Nullable ItemStack wandStack() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof BlueprintWandItem) return stack;
        return ShapeExportClient.wand(player);
    }

    private boolean refresh() {
        ItemStack stack = wandStack();
        if (stack == null) return false;
        plan = ShapePlan.read(stack);
        selection = WandSelection.read(stack);
        machine = MachineSettings.read(stack);
        limits = PredicateRules.read(stack);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!refresh()) {
            onClose();
            return;
        }
        Font font = Minecraft.getInstance().font;
        controls.clear();
        tooltip = null;

        float s = scale();
        int mx = (int) Math.floor(localX(mouseX));
        int my = (int) Math.floor(localY(mouseY));

        g.pose().pushPose();
        g.pose().translate(originX(), originY(), 0);
        g.pose().scale(s, s, 1f);

        GuiDraw.drawGradientRect(g, 0, 0, W, H, T.bgTop(), T.bgBottom());
        T.pattern(g, 0, 0, W, H);
        GuiDraw.drawBorder(g, 0, 0, W, H, T.accent2(), 1);
        GuiDraw.drawBorder(g, 2, 2, W - 4, H - 4, GuiDraw.withAlpha(T.accent(), 0x22), 1);

        g.drawString(font, "BLUEPRINT", 10, 8, T.accent(), false);
        g.fill(10, 20, W - 10, 21, T.line());

        boolean exporting = selection.exportMode;
        if (libraryTab || !(exporting && rulesPage)) {
            chip(g, font, MODE_BUILD, "BUILD", 84, 4, 46, !exporting && !libraryTab, mx, my, false);
            chip(g, font, LIB_TAB, "LIBRARY", 134, 4, 56, libraryTab, mx, my, false);
            chip(g, font, MODE_EXPORT, "EXPORT", 194, 4, 48, exporting && !libraryTab, mx, my, false);
        }
        if (libraryTab) {
            paintLibrary(g, font, mx, my);
        } else if (!exporting) {
            String summary = ShapeHologramInfo.summary();
            int summaryColor = ShapeHologramInfo.tooBig ? HudModel.RED :
                    ShapeHologramInfo.haveBlock ? HudModel.GREEN : HudModel.GOLD;
            // the mode chips end at x=242, so the summary gets what is left of the header and is cut to fit
            String full = ShapeHologramInfo.fullSummary();
            summary = font.plainSubstrByWidth(summary, W - 10 - 246);
            int summaryX = W - 10 - font.width(summary);
            g.drawString(font, summary, summaryX, 8, summaryColor, false);
            if (!ShapeHologramInfo.haveBlock && isOver(mx, my, summaryX, 6, font.width(summary), 11)) tooltip = full;
            paintBuild(g, font, mx, my);
        } else if (machinePage) {
            paintMachine(g, font, mx, my);
        } else if (rulesPage) {
            paintRules(g, font, mx, my);
        } else {
            paintExport(g, font, mx, my);
        }

        g.pose().popPose();

        if (tooltip != null) g.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ the library tab

    private void refreshLibrary() {
        libEntries = PlanLibrary.list();
        if (!libSelected.isEmpty() && !PlanLibrary.exists(libSelected)) libSelected = "";
        loadPreview();
        libDirty = false;
    }

    private void loadPreview() {
        libPreview = null;
        if (libSelected.isEmpty()) return;
        try {
            libPreview = PlanLibrary.load(libSelected);
        } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            libPreview = null;
        }
    }

    /** One line about a part: its shape, sizes and what is special about it. */
    private static String describePart(int index, ShapeSpec part) {
        StringBuilder text = new StringBuilder().append(index + 1).append(": ").append(part.kind.displayName());
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            if (part.kind.param(i) == null) continue;
            text.append(first ? "  " : " x ").append(part.size[i]);
            first = false;
        }
        if (!part.block.isEmpty()) {
            text.append("  [").append(part.block.startsWith("minecraft:") ? part.block.substring(10) : part.block).append("]");
        }
        if (part.hollow) text.append(part.open ? "  open" : "  hollow");
        if (part.arrayMode != ShapeSpec.ARRAY_OFF) text.append("  repeat ").append(part.arrayCount);
        if (part.mirrorX || part.mirrorY || part.mirrorZ) text.append("  mirrored");
        return text.toString();
    }

    private void paintLibrary(GuiGraphics g, Font font, int mx, int my) {
        if (libDirty) refreshLibrary();
        g.drawString(font, "SAVED PLANS (" + libEntries.size() + ")", 10, 28, HudModel.DIM, false);

        // the list
        int listBottom = LIB_TOP + LIB_ROWS * LIB_ROW_H;
        g.fill(10, LIB_TOP - 2, 226, listBottom, GuiDraw.withAlpha(T.bgTop(), 0xCC));
        GuiDraw.drawBorder(g, 10, LIB_TOP - 2, 216, listBottom - LIB_TOP + 2, GuiDraw.withAlpha(T.accent(), 0x55), 1);
        libMaxScroll = Math.max(0, libEntries.size() - LIB_ROWS);
        libScroll = Mth.clamp(libScroll, 0, libMaxScroll);
        if (libEntries.isEmpty()) {
            g.drawCenteredString(font, "Nothing saved yet", 118, LIB_TOP + 20, HudModel.DIM);
            g.drawCenteredString(font, "SAVE WAND PLAN keeps what is on the wand", 118, LIB_TOP + 32, HudModel.DIM);
        }
        for (int r = 0; r < LIB_ROWS; r++) {
            int i = libScroll + r;
            if (i >= libEntries.size()) break;
            PlanLibrary.Entry entry = libEntries.get(i);
            int y = LIB_TOP + r * LIB_ROW_H;
            boolean selected = entry.name().equals(libSelected);
            boolean hover = mx >= 10 && mx < 226 && my >= y && my < y + LIB_ROW_H - 1;
            if (selected || hover) {
                g.fill(11, y, 225, y + LIB_ROW_H - 1, GuiDraw.mix(T.bgTop(), T.accent(), selected ? 0.30f : 0.14f));
            }
            g.drawString(font, font.plainSubstrByWidth(entry.name(), 98), 14, y + 3, selected ? HudModel.WHITE : T.accent(),
                    false);
            g.drawString(font, font.plainSubstrByWidth(entry.summary(), 104), 116, y + 3, HudModel.DIM, false);
            if (hover && (font.width(entry.name()) > 98 || font.width(entry.summary()) > 104)) {
                tooltip = entry.name() + " - " + entry.summary();
            }
            controls.add(new Control(LIB_ROW + i, Kind.CHIP, 10, y, 216, LIB_ROW_H - 1, 0, 0, 0, null));
        }
        if (libMaxScroll > 0) {
            int trackH = LIB_ROWS * LIB_ROW_H - 2;
            libBarThumb = Math.max(8, trackH * LIB_ROWS / libEntries.size());
            int thumbY = LIB_TOP - 2 + (trackH - libBarThumb) * libScroll / libMaxScroll;
            g.fill(LIB_BAR_X, LIB_TOP - 2, LIB_BAR_X + 4, LIB_TOP - 2 + trackH, T.dim());
            g.fill(LIB_BAR_X, thumbY, LIB_BAR_X + 4, thumbY + libBarThumb, libBarDrag ? HudModel.WHITE : T.accent());
        }

        // what is in the chosen plan
        int dy = listBottom + 6;
        g.drawString(font, libSelected.isEmpty() ? "NOTHING PICKED" : libSelected.toUpperCase(java.util.Locale.ROOT), 10, dy,
                T.accent(), false);
        if (libPreview == null) {
            if (!libSelected.isEmpty()) g.drawString(font, "This plan could not be read", 10, dy + 11, HudModel.RED, false);
        } else {
            int shown = Math.min(libPreview.parts.size(), 5);
            for (int i = 0; i < shown; i++) {
                g.drawString(font, font.plainSubstrByWidth(describePart(i, libPreview.parts.get(i)), 214), 10, dy + 11 + i * 10,
                        HudModel.DIM, false);
            }
            if (libPreview.parts.size() > shown) {
                g.drawString(font, "+" + (libPreview.parts.size() - shown) + " more parts", 10, dy + 11 + shown * 10,
                        HudModel.DIM, false);
            }
        }

        // the actions
        int rx = 240;
        int rw = 130;
        chip(g, font, LIB_SAVE, "SAVE WAND PLAN...", rx, LIB_TOP, rw, false, mx, my, true);
        chip(g, font, LIB_LOAD, "LOAD SELECTED", rx, LIB_TOP + 17, rw, false, mx, my, true);
        chip(g, font, LIB_DELETE, libConfirmDelete ? "CLICK AGAIN TO DELETE" : "DELETE SELECTED", rx, LIB_TOP + 34, rw,
                libConfirmDelete, mx, my, true);
        chip(g, font, LIB_COPY, "COPY WAND PLAN AS TEXT", rx, LIB_TOP + 60, rw, false, mx, my, true);
        chip(g, font, LIB_PASTE, "PASTE PLAN FROM TEXT", rx, LIB_TOP + 77, rw, false, mx, my, true);
        chip(g, font, LIB_FOLDER, "OPEN PLANS FOLDER", rx, LIB_TOP + 103, rw, false, mx, my, true);

        g.drawString(font, "ON THE WAND", rx, LIB_TOP + 128, HudModel.DIM, false);
        int wy = LIB_TOP + 139;
        int wandShown = Math.min(plan.parts.size(), 6);
        for (int i = 0; i < wandShown; i++) {
            g.drawString(font, font.plainSubstrByWidth(describePart(i, plan.parts.get(i)), rw), rx, wy + i * 10, T.accent(),
                    false);
        }
        if (plan.parts.size() > wandShown) {
            g.drawString(font, "+" + (plan.parts.size() - wandShown) + " more parts", rx, wy + wandShown * 10, HudModel.DIM,
                    false);
        }

        footer(g, font, 10, "Plans are saved as text files in blueprint/plans in your game folder. Copy as text shares one in "
                + "chat; loading keeps the sizes, rotations, blocks, repeats and mirrors, but not pins.");
    }

    private void footer(GuiGraphics g, Font font, int textX, String hint) {
        g.fill(10, H - 30, W - 10, H - 29, T.line());
        boolean fresh = !status.isEmpty() && System.currentTimeMillis() - statusAt < STATUS_MS;
        String text = fresh ? status : hint;
        int color = fresh ? statusColor : HudModel.DIM;
        int y = H - 25;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), W - 10 - textX);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), textX, y, color, false);
            y += 10;
        }
    }

    private void paintBuild(GuiGraphics g, Font font, int mx, int my) {
        ShapeSpec spec = plan.current();

        // the parts of the plan
        int x = 10;
        g.drawString(font, "PARTS", x, 28, HudModel.DIM, false);
        x += 34;
        for (int i = 0; i < plan.parts.size(); i++) {
            chip(g, font, PART + i, (i + 1) + (plan.parts.get(i).locked ? "*" : ""), x, 25, 18, plan.selected == i, mx,
                    my, false);
            x += 20;
        }
        chip(g, font, ADD_PART, "+", x + 4, 25, 18, false, mx, my, false);
        chip(g, font, REMOVE_PART, "-", x + 26, 25, 18, false, mx, my, false);

        // left column: the shape and its sizes. The shapes are a list that scrolls, so more of them never cost room.
        int lx = 10;
        g.drawString(font, "SHAPE", lx, 44, HudModel.DIM, false);
        ShapeKind[] kinds = ShapeKind.values();
        int rows = (kinds.length + 2) / 3;
        shapeMaxScroll = Math.max(0, rows - SHAPE_ROWS);
        if (spec.kind != shownKind) {
            // bring the chosen shape into view when it changes: another part, a preset, the first look
            int row = spec.kind.ordinal() / 3;
            if (row < shapeScroll) shapeScroll = row;
            else if (row >= shapeScroll + SHAPE_ROWS) shapeScroll = row - SHAPE_ROWS + 1;
            shownKind = spec.kind;
        }
        shapeScroll = Mth.clamp(shapeScroll, 0, shapeMaxScroll);
        for (int row = shapeScroll; row < Math.min(rows, shapeScroll + SHAPE_ROWS); row++) {
            for (int col = 0; col < 3; col++) {
                int i = row * 3 + col;
                if (i >= kinds.length) break;
                chip(g, font, SHAPE + i, kinds[i].displayName(), lx + col * 71, SHAPE_TOP + (row - shapeScroll) * 15, 69,
                        kinds[i] == spec.kind, mx, my, false);
            }
        }
        if (shapeMaxScroll > 0) {
            int trackH = SHAPE_ROWS * 15 - 3;
            shapeBarThumb = Math.max(8, trackH * SHAPE_ROWS / rows);
            int thumbY = SHAPE_TOP + (trackH - shapeBarThumb) * shapeScroll / shapeMaxScroll;
            g.fill(SHAPE_BAR_X, SHAPE_TOP, SHAPE_BAR_X + 4, SHAPE_TOP + trackH, T.dim());
            g.fill(SHAPE_BAR_X, thumbY, SHAPE_BAR_X + 4, thumbY + shapeBarThumb,
                    shapeBarDrag ? HudModel.WHITE : T.accent());
        }
        int sy = SHAPE_TOP + SHAPE_ROWS * 15 + 6;
        g.drawString(font, "SIZE", lx, sy, HudModel.DIM, false);
        for (int i = 0; i < 4; i++) {
            ShapeKind.Param param = spec.kind.param(i);
            if (param == null) continue;
            slider(g, font, SIZE_0 + i, param.label(), lx, sy + 11 + i * 15, 212, 78, spec.size[i], param.min(),
                    param.max(), 1, mx, my);
        }
        int hy = sy + 11 + 4 * 15 + 2;
        chip(g, font, HOLLOW, spec.hollow ? "HOLLOW" : "SOLID", lx, hy, 52, spec.hollow, mx, my, false);
        chip(g, font, OPEN_ENDS, spec.open ? "OPEN" : "CLOSED", lx + 54, hy, 46, spec.hollow && spec.open, mx, my, false);
        slider(g, font, WALL, "Wall", lx + 104, hy, 108, 28, spec.wall, 1, 16, 1, mx, my);

        // right column: how it is turned and where it sits
        int rx = 238;
        int rw = W - 10 - rx;
        g.drawString(font, "ROTATION", rx, 44, HudModel.DIM, false);
        slider(g, font, YAW, "Yaw", rx, 55, rw, 34, spec.yaw, 0, 345, 15, mx, my);
        slider(g, font, PITCH, "Pitch", rx, 70, rw, 34, spec.pitch, 0, 345, 15, mx, my);
        slider(g, font, ROLL, "Roll", rx, 85, rw, 34, spec.roll, 0, 345, 15, mx, my);
        String[] presets = { "Flat", "Face Z", "Face X", "Tilt 45" };
        int half = (rw - 4) / 2;
        for (int i = 0; i < 4; i++) {
            chip(g, font, PRESET + i, presets[i], rx + (i % 2) * (half + 4), 101 + (i / 2) * 15, half, false, mx, my,
                    false);
        }

        g.drawString(font, "OFFSET", rx, 136, HudModel.DIM, false);
        slider(g, font, OFFSET_X, "X", rx, 147, rw, 34, spec.dx, -64, 64, 1, mx, my);
        slider(g, font, OFFSET_Y, "Y", rx, 162, rw, 34, spec.dy, -64, 64, 1, mx, my);
        slider(g, font, OFFSET_Z, "Z", rx, 177, rw, 34, spec.dz, -64, 64, 1, mx, my);

        chip(g, font, SIT_ON_BASE, "SIT ON THE BASE", rx, 198, rw, plan.sitOnBase, mx, my, true);
        chip(g, font, CONNECTED, "NO DIAGONAL GAPS", rx, 213, rw, plan.connected, mx, my, true);
        chip(g, font, OVERWRITE, "OVERWRITE BLOCKS", rx, 228, rw, plan.overwrite, mx, my, true);

        // pins: a pinned part stays where it was pinned however the wand is aimed
        boolean allPinned = plan.parts.stream().allMatch(part -> part.locked);
        chip(g, font, PIN_PART, spec.locked ? "UNPIN PART" : "PIN PART", rx, 246, half, spec.locked, mx, my, true);
        chip(g, font, PIN_ALL, allPinned ? "UNPIN ALL" : "PIN ALL", rx + half + 4, 246, half, allPinned, mx, my, true);
        String pinNote = spec.locked ? "Pinned at " + spec.pinX + " " + spec.pinY + " " + spec.pinZ : "Follows the aim";
        if (ShapeHologramInfo.pinnedElsewhere > 0) {
            pinNote = ShapeHologramInfo.pinnedElsewhere + " pinned in another dimension";
        }
        // the block this part is built from (shift-click: back to the block in your other hand), then where it is pinned
        String blockName = spec.block.isEmpty() ? "IN YOUR HAND" :
                spec.block.startsWith("minecraft:") ? spec.block.substring("minecraft:".length()) : spec.block;
        chip(g, font, PART_BLOCK, "BLOCK: " + blockName, lx, hy + 15, 212, !spec.block.isEmpty(), mx, my, false);
        g.drawString(font, font.plainSubstrByWidth(pinNote, 212), lx, hy + 30, spec.locked ? T.accent() : HudModel.DIM,
                false);

        // repeats and mirror images of this part
        int ay = 238;
        g.drawString(font, "REPEAT", lx, ay, HudModel.DIM, false);
        String[] arrayNames = { "OFF", "LINE X", "LINE Y", "LINE Z", "RING" };
        chip(g, font, ARRAY_MODE, arrayNames[Math.floorMod(spec.arrayMode, arrayNames.length)], lx, ay + 11, 64,
                spec.arrayMode != ShapeSpec.ARRAY_OFF, mx, my, false);
        if (spec.arrayMode != ShapeSpec.ARRAY_OFF) {
            slider(g, font, ARRAY_COUNT, "Copies", lx + 70, ay + 11, 142, 40, spec.arrayCount, 2, ShapeSpec.MAX_ARRAY, 1,
                    mx, my);
        }
        if (spec.arrayMode >= ShapeSpec.ARRAY_X && spec.arrayMode <= ShapeSpec.ARRAY_Z) {
            slider(g, font, ARRAY_SPACING, "Spacing", lx, ay + 26, 212, 78, spec.arraySpacing, 1, 64, 1, mx, my);
        }
        chip(g, font, MIRROR_X, "MIRROR X", lx, ay + 41, 69, spec.mirrorX, mx, my, false);
        chip(g, font, MIRROR_Y, "MIRROR Y", lx + 71, ay + 41, 69, spec.mirrorY, mx, my, false);
        chip(g, font, MIRROR_Z, "MIRROR Z", lx + 142, ay + 41, 69, spec.mirrorZ, mx, my, false);

        String[] orientNames = { "OFF", "OUT", "IN" };
        chip(g, font, ORIENT, "TURN BLOCKS: " + orientNames[Math.floorMod(plan.orient, 3)], rx, 262, rw,
                plan.orient != ShapePlan.ORIENT_OFF, mx, my, false);
        if (isOver(mx, my, rx, 262, rw, 12)) {
            tooltip = "Turns logs, stairs, slabs and other directional blocks to follow the shape";
        }
        chip(g, font, SEND_EXPORT, "SEND TO EXPORT >", rx, 277, rw, false, mx, my, true);

        chip(g, font, UNDO, "UNDO", 10, H - 25, 44, false, mx, my, false);
        chip(g, font, RESET, "RESET", 58, H - 25, 44, false, mx, my, false);
        footer(g, font, 112, "Builds with the block in your other hand. Right-click builds, left-click undoes.");
    }

    private void paintExport(GuiGraphics g, Font font, int mx, int my) {
        WandSelection sel = selection;
        KubeJsExport.Options o = sel.options;

        // the machine's structures: the default "main" and its substructures
        g.drawString(font, "PARTS", 10, 28, HudModel.DIM, false);
        int cx = 44;
        for (int i = 0; i < sel.structureCount(); i++) {
            chip(g, font, X_STRUCTURE + i, i == 0 ? "M" : String.valueOf(i), cx, 25, 18, sel.currentIndex() == i, mx,
                    my, false);
            cx += 20;
        }
        if (sel.structureCount() < WandSelection.MAX_STRUCTURES) {
            chip(g, font, X_ADD_STRUCTURE, "+", cx + 4, 25, 18, false, mx, my, false);
        }
        if (sel.currentIndex() > 0) chip(g, font, X_REMOVE_STRUCTURE, "-", cx + 26, 25, 18, false, mx, my, false);

        int lw = 216;
        String selectionLabel = "SELECTION: " + sel.name();
        g.drawString(font, selectionLabel, 10, 46, HudModel.DIM, false);
        if (sel.currentIndex() > 0) {
            chip(g, font, X_RENAME, "RENAME", 10 + font.width(selectionLabel) + 8, 43, 46, false, mx, my, false);
        }
        g.drawString(font, "Corner 1: " + pos(sel.corner1), 10, 58, HudModel.WHITE, false);
        g.drawString(font, "Corner 2: " + pos(sel.corner2), 10, 69, HudModel.WHITE, false);
        g.drawString(font, "Controller: " + (sel.controller == null ? "- (optional)" : pos(sel.controller)), 10, 80,
                HudModel.GOLD, false);
        if (sel.hasBothCorners()) {
            var low = sel.low();
            var high = sel.high();
            int sx = high.getX() - low.getX() + 1, sy = high.getY() - low.getY() + 1, sz = high.getZ() - low.getZ() + 1;
            g.drawString(font, sx + " x " + sy + " x " + sz + "  (" + (long) sx * sy * sz + " blocks)", 10, 91,
                    T.accent(), false);
        }

        // right column: what to do with the selection
        int rx = 238;
        int rw = W - 10 - rx;
        int ry = 46;
        ry += chip(g, font, X_CONTROLLER, selection.controllerMode ? "MARKING CONTROLLER" : "MARK CONTROLLER", rx, ry,
                rw, selection.controllerMode, mx, my, true) + 3;
        ry += chip(g, font, X_SELECT_BUILD, "SELECT LAST BUILD", rx, ry, rw, false, mx, my, true) + 3;
        ry += chip(g, font, X_AUTO_SELECT, "FROM CONTROLLER", rx, ry, rw, false, mx, my, true) + 3;
        ry += chip(g, font, X_CLEAR, "CLEAR SELECTION", rx, ry, rw, false, mx, my, true) + 3;
        ry += chip(g, font, X_COPY, "COPY OUTPUT", rx, ry, rw, sel.hasBothCorners(), mx, my, true) + 3;
        if (ShapeExportClient.phantasiaAvailable()) {
            ry += chip(g, font, X_PREVIEW, "PREVIEW (PHANTASIA)", rx, ry, rw, false, mx, my, true) + 3;
        }
        ry += chip(g, font, X_MACHINE_PAGE, "MACHINE DEFINITION >", rx, ry, rw, o.style() == KubeJsExport.STYLE_MACHINE,
                mx, my, true) + 3;
        ry += chip(g, font, X_RULES_PAGE, "LIMITS PER BLOCK >", rx, ry, rw, !limits.rules.isEmpty(), mx, my, true) + 3;
        ry += chip(g, font, X_OPEN_FOLDER, "OPEN EXPORTS FOLDER", rx, ry, rw, false, mx, my, true) + 8;

        // a pattern from the clipboard, shown as a hologram where the wand is aimed
        g.drawString(font, "IMPORT", rx, ry, HudModel.DIM, false);
        ry += 11;
        ry += chip(g, font, X_IMPORT, "IMPORT FROM CLIPBOARD", rx, ry, rw, ImportHologram.active(), mx, my, true) + 3;
        if (ImportHologram.active()) {
            ry += chip(g, font, X_IMPORT_TURN, "TURN IT", rx, ry, rw, false, mx, my, true) + 3;
            ry += chip(g, font, X_IMPORT_LOCK, ImportHologram.locked() ? "UNLOCK IT" : "LOCK IT IN PLACE", rx, ry, rw,
                    ImportHologram.locked(), mx, my, true) + 3;
            if (ImportHologram.locked()) {
                // move the locked hologram a block at a time along the world's axes
                String[] names = { "-X", "+X", "-Y", "+Y", "-Z", "+Z" };
                int nw = (rw - 5 * 2) / 6;
                for (int i = 0; i < 6; i++) {
                    chip(g, font, X_NUDGE + i, names[i], rx + i * (nw + 2), ry, nw, false, mx, my, false);
                }
                ry += 15;
            }
            ry += chip(g, font, X_IMPORT_PLACE, "BUILD IT", rx, ry, rw, false, mx, my, true) + 3;
            chip(g, font, X_IMPORT_CLEAR, "CLEAR IMPORT", rx, ry, rw, false, mx, my, true);
        }

        // left column: how the output is written
        int y = 110;
        g.drawString(font, "OUTPUT", 10, y, HudModel.DIM, false);
        y += 12;
        y += chip(g, font, X_CASE, "Letters: " + (o.uppercaseFirst() ? "A-Z first, then a-z" : "a-z first, then A-Z"), 10,
                y, lw, false, mx, my, true) + 3;
        y += chip(g, font, X_AIR, "Air: " + airName(o), 10, y, lw, false, mx, my, true) + 3;
        y += chip(g, font, X_PARTS, "Hatches and buses: " + (o.partsAsAbilities() ? "as abilities" : "exact blocks"), 10,
                y, lw, false, mx, my, true) + 3;
        y += chip(g, font, X_STYLE, "Output: " + outputName(o), 10, y, lw, false, mx, my, true) + 3;
        y += chip(g, font, X_LANGUAGE, "Language: " + (o.java() ? "Java (registry fields)" : "KubeJS"), 10, y, lw,
                o.java(), mx, my, true) + 3;
        y += chip(g, font, X_VERSION, "GregTech: " + (o.gt7() ? "7 / 8.0 release (.aisle)" : "8 snapshot (.slice)"), 10, y, lw,
                o.gt7(), mx, my, true) + 3;

        y += 4;
        var sorted = new ArrayList<>(findings());
        sorted.sort(java.util.Comparator.comparing(f -> f.level().ordinal()));
        for (var finding : sorted) {
            if (y > H - 38) break;
            int color = switch (finding.level()) {
                case ERROR -> HudModel.RED;
                case WARNING -> HudModel.GOLD;
                default -> HudModel.DIM;
            };
            String mark = switch (finding.level()) {
                case ERROR -> "! ";
                case WARNING -> "* ";
                default -> "- ";
            };
            for (var line : font.split(Component.literal(mark + finding.text()), lw)) {
                if (y > H - 38) break;
                g.drawString(font, line, 10, y, color, false);
                y += 10;
            }
        }
        y += 2;
        String help = "Right-click a block: corner 2 (sneak: corner 1). Right-click air or COPY OUTPUT to copy. " +
                "Arrows at the controller: red = slices, green = rows (up), blue = characters.";
        for (var line : font.split(Component.literal(help), lw)) {
            if (y > H - 38) break;
            g.drawString(font, line, 10, y, HudModel.DIM, false);
            y += 10;
        }

        footer(g, font, 10, "Sneak + right-click air opens this editor.");
    }

    private void paintMachine(GuiGraphics g, Font font, int mx, int my) {
        MachineSettings m = machine;
        chip(g, font, M_BACK, "< BACK", 10, 25, 50, false, mx, my, false);
        g.drawString(font, "MACHINE DEFINITION", 66, 28, HudModel.DIM, false);

        int label = 10;
        int value = 104;
        int vw = W - 10 - value;
        int y = 46;

        g.drawString(font, "Class", label, y + 2, HudModel.DIM, false);
        g.drawString(font, "WorkableElectricMultiblockMachine", value, y + 2, T.accent(), false);
        y += 15;

        g.drawString(font, "Id", label, y + 2, HudModel.DIM, false);
        y += chip(g, font, M_NAME, m.id(), value, y, vw, false, mx, my, true) + 3;

        g.drawString(font, "Rotation", label, y + 2, HudModel.DIM, false);
        y += chip(g, font, M_ROTATION, m.rotationName(), value, y, vw, false, mx, my, true) + 3;

        g.drawString(font, "Recipe types", label, y + 2, HudModel.DIM, false);
        y += listRow(g, font, m.recipeTypes, y, value, vw, M_ADD_TYPE, M_CLEAR_TYPES, mx, my) + 3;

        g.drawString(font, "Modifiers", label, y + 2, HudModel.DIM, false);
        y += listRow(g, font, m.recipeModifiers, y, value, vw, M_ADD_MODIFIER, M_CLEAR_MODIFIERS, mx, my) + 3;

        String controllerBlock = null;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (var s : selection.structures()) {
                if (s.controller != null && !level.getBlockState(s.controller).isAir()) {
                    controllerBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(s.controller).getBlock())
                            .toString();
                    break;
                }
            }
        }
        String appearance = !m.appearanceBlock.isEmpty() ? m.appearanceBlock :
                controllerBlock != null ? "(controller) " + controllerBlock : "pick a block";
        g.drawString(font, "Appearance", label, y + 2, HudModel.DIM, false);
        y += chip(g, font, M_APPEARANCE, appearance, value, y, vw, !m.appearanceBlock.isEmpty(), mx, my, true) + 3;

        String casing = !m.casingTexture.isEmpty() ? m.casingTexture :
                !m.derivedCasingTexture.isEmpty() ? "(from the block) " + m.derivedCasingTexture : "pick a texture";
        g.drawString(font, "Casing texture", label, y + 2, HudModel.DIM, false);
        y += chip(g, font, M_CASING, casing, value, y, vw, !m.casingTexture.isEmpty(), mx, my, true) + 3;

        g.drawString(font, "Overlay texture", label, y + 2, HudModel.DIM, false);
        y += chip(g, font, M_OVERLAY, m.overlayTexture.isEmpty() ? "pick a texture" : m.overlayTexture, value, y, vw,
                !m.overlayTexture.isEmpty(), mx, my, true) + 3;

        boolean on = selection.options.style() == KubeJsExport.STYLE_MACHINE;
        y += chip(g, font, M_OUTPUT, on ? "OUTPUT: THE FULL MACHINE DEFINITION" :
                "OUTPUT: JUST THE PATTERN (CLICK FOR THE MACHINE)", 10, y, 250, on, mx, my, true) + 6;

        String help = "With the output set to the full machine, COPY OUTPUT writes the registration: the id, rotation, " +
                "recipe types and modifiers, appearance block, every structure as a pattern, and the casing model. Blocks " +
                "that match the appearance block (and hatches, when written as abilities) become the casing, which also " +
                "allows maintenance and the recipe types' hatches.";
        for (var line : font.split(Component.literal(help), W - 20)) {
            if (y > H - 38) break;
            g.drawString(font, line, 10, y, HudModel.DIM, false);
            y += 10;
        }

        footer(g, font, 10, "Sneak + right-click air opens this editor.");
    }

    private List<net.phoenixvine.blueprint.common.export.ExportCheck.Finding> cachedFindings = List.of();
    private long findingsAt = 0;

    /** The export check's findings, worked out at most twice a second since they look through the selected blocks. */
    private List<net.phoenixvine.blueprint.common.export.ExportCheck.Finding> findings() {
        long now = System.currentTimeMillis();
        var level = Minecraft.getInstance().level;
        if (level != null && now - findingsAt > 500) {
            findingsAt = now;
            cachedFindings = net.phoenixvine.blueprint.common.export.ExportCheck.check(level, selection, machine);
        }
        return cachedFindings;
    }

    // ------------------------------------------------------------------ limits per kind of block

    /** One kind of predicate in the selection: its key (see {@code KubeJsExport.keyFor}), what to show, and how many. */
    private record RuleRow(String key, String label, int count) {}

    private List<RuleRow> cachedRows = List.of();
    private long rowsAt = 0;

    /** The kinds of predicate the selection will be written as, most common first; worked out twice a second at most. */
    private List<RuleRow> ruleRows() {
        long now = System.currentTimeMillis();
        var level = Minecraft.getInstance().level;
        if (level == null || now - rowsAt < 500) return cachedRows;
        rowsAt = now;

        String controllerBlock = null;
        for (var s : selection.structures()) {
            if (s.controller != null && !level.getBlockState(s.controller).isAir()) {
                controllerBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(s.controller).getBlock()).toString();
                break;
            }
        }
        String appearance = selection.options.style() == KubeJsExport.STYLE_MACHINE ?
                machine.effective(controllerBlock).appearanceBlock : null;
        if (appearance != null && appearance.isEmpty()) appearance = null;

        Map<String, Integer> counts = new HashMap<>();
        int budget = ExportCheck.SCAN_LIMIT;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (var s : selection.structures()) {
            if (s.corner1 == null || s.corner2 == null) continue;
            int x0 = Math.min(s.corner1.getX(), s.corner2.getX()), x1 = Math.max(s.corner1.getX(), s.corner2.getX());
            int y0 = Math.min(s.corner1.getY(), s.corner2.getY()), y1 = Math.max(s.corner1.getY(), s.corner2.getY());
            int z0 = Math.min(s.corner1.getZ(), s.corner2.getZ()), z1 = Math.max(s.corner1.getZ(), s.corner2.getZ());
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > budget) continue;
            budget -= (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (s.controller != null && s.controller.getX() == x && s.controller.getY() == y &&
                                s.controller.getZ() == z) {
                            continue;
                        }
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock())
                                .toString();
                        String key = KubeJsExport.keyFor(id, selection.options, appearance);
                        if (key != null && !key.equals("air")) counts.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        List<RuleRow> rows = new ArrayList<>();
        for (var e : counts.entrySet()) {
            String key = e.getKey();
            String label = key.equals("appearance") ? "Casing (the appearance block)" :
                    key.startsWith("abilities:") ? "Hatches: " + key.substring("abilities:".length()) :
                            key.substring("block:".length());
            rows.add(new RuleRow(key, label, e.getValue()));
        }
        rows.sort((a, b) -> b.count() != a.count() ? Integer.compare(b.count(), a.count()) :
                a.label().compareTo(b.label()));
        cachedRows = rows;
        return rows;
    }

    private static final int RULES_TOP = 72;
    private static final int RULE_ROW = 15;

    private int visibleRuleRows() {
        return (H - 36 - RULES_TOP) / RULE_ROW;
    }

    private static PredicateRules.Rule defaultRule(String key) {
        return key.equals("appearance") ? PredicateRules.Rule.CASING : PredicateRules.Rule.PLAIN;
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case PredicateRules.MIN -> "at least";
            case PredicateRules.MAX -> "at most";
            case PredicateRules.EXACT -> "exactly";
            default -> "any";
        };
    }

    /** The limits page: how many of each kind of block the machine needs, and where hatches may stand in. */
    private void paintRules(GuiGraphics g, Font font, int mx, int my) {
        chip(g, font, R_BACK, "< BACK", 10, 25, 50, false, mx, my, false);
        g.drawString(font, "LIMITS PER BLOCK", 66, 28, HudModel.DIM, false);
        chip(g, font, R_RESET_ALL, "RESET ALL", W - 10 - 64, 25, 64, false, mx, my, false);
        g.drawString(font, "How many of each the machine needs, written on its predicate.", 10, 46, HudModel.DIM, false);

        List<RuleRow> rows = ruleRows();
        int visible = visibleRuleRows();
        rulesScroll = Mth.clamp(rulesScroll, 0, Math.max(0, rows.size() - visible));

        g.drawString(font, "BLOCK", 10, RULES_TOP - 11, HudModel.DIM, false);
        g.drawString(font, "IN IT", 108, RULES_TOP - 11, HudModel.DIM, false);
        if (rows.isEmpty()) {
            g.drawString(font, "Select a multiblock on the export page first.", 10, RULES_TOP + 4, HudModel.GOLD, false);
        }
        for (int i = 0; i < visible; i++) {
            int index = rulesScroll + i;
            if (index >= rows.size()) break;
            RuleRow row = rows.get(index);
            PredicateRules.Rule rule = limits.get(row.key(), defaultRule(row.key()));
            int y = RULES_TOP + i * RULE_ROW;
            int base = R_ROWS + index * 8;

            // columns: name 10-100, count 102-132, mode 136-188, - number + 192-242, then three switches
            String label = font.plainSubstrByWidth(row.label(), 88);
            g.drawString(font, label, 10, y + 2, limits.rules.containsKey(row.key()) ? HudModel.WHITE : HudModel.DIM,
                    false);
            if (isOver(mx, my, 10, y, 90, 12) && !label.equals(row.label())) tooltip = row.label();
            String count = "x" + row.count();
            g.drawString(font, font.plainSubstrByWidth(count, 32), 102, y + 2, T.accent(), false);

            chip(g, font, base + ROW_MODE, modeName(rule.mode()), 136, y, 52, rule.mode() != PredicateRules.NONE, mx, my,
                    false);
            if (rule.mode() != PredicateRules.NONE) {
                chip(g, font, base + ROW_MINUS, "-", 192, y, 12, false, mx, my, false);
                String limit = String.valueOf(rule.count());
                g.drawString(font, limit, 206 + (22 - font.width(limit)) / 2, y + 2, HudModel.WHITE, false);
                chip(g, font, base + ROW_PLUS, "+", 230, y, 12, false, mx, my, false);
            }
            if (!row.key().startsWith("abilities:")) {
                chip(g, font, base + ROW_HATCHES, "HATCH", 248, y, 40, rule.hatches(), mx, my, false);
                chip(g, font, base + ROW_MAINTENANCE, "MAINT", 292, y, 34, rule.maintenance(), mx, my, false);
                chip(g, font, base + ROW_CONTROLLER, "CTRL", 330, y, 32, rule.controller(), mx, my, false);
            }
        }
        if (rows.size() > visible) {
            g.drawString(font, (rulesScroll + 1) + "-" + Math.min(rows.size(), rulesScroll + visible) + " of " +
                    rows.size() + " (scroll)", W - 10 - 110, RULES_TOP - 11, HudModel.DIM, false);
        }
        footer(g, font, 10, "HATCH: the recipe types' hatches may stand in. MAINT: one maintenance hatch may. CTRL: the controller may.");
    }

    /** A click on one button of a row of the limits page. */
    private void pressRule(int id) {
        int index = (id - R_ROWS) / 8;
        int button = (id - R_ROWS) % 8;
        List<RuleRow> rows = cachedRows;
        if (index < 0 || index >= rows.size()) return;
        RuleRow row = rows.get(index);
        PredicateRules.Rule rule = limits.get(row.key(), defaultRule(row.key()));
        int step = hasShiftDown() ? 5 : 1;
        switch (button) {
            case ROW_MODE -> {
                int next = (rule.mode() + 1) % 4;
                WandEditor.rule(row.key(), PredicateRules.FIELD_MODE, next);
                if (next != PredicateRules.NONE && rule.count() < 1) WandEditor.rule(row.key(), PredicateRules.FIELD_COUNT, 1);
                status(row.label() + ": " + modeName(next), T.accent());
            }
            case ROW_MINUS -> WandEditor.rule(row.key(), PredicateRules.FIELD_COUNT, Math.max(1, rule.count() - step));
            case ROW_PLUS -> WandEditor.rule(row.key(), PredicateRules.FIELD_COUNT, rule.count() + step);
            case ROW_HATCHES -> {
                WandEditor.rule(row.key(), PredicateRules.FIELD_HATCHES, 0);
                status(row.label() + ": hatches " + (rule.hatches() ? "not allowed" : "allowed") + " in its place", T.accent());
            }
            case ROW_CONTROLLER -> {
                WandEditor.rule(row.key(), PredicateRules.FIELD_CONTROLLER, 0);
                status(row.label() + ": the controller " + (rule.controller() ? "may not" : "may") + " stand here", T.accent());
            }
            case ROW_MAINTENANCE -> {
                WandEditor.rule(row.key(), PredicateRules.FIELD_MAINTENANCE, 0);
                status(row.label() + ": a maintenance hatch " + (rule.maintenance() ? "not allowed" : "allowed"), T.accent());
            }
            default -> {}
        }
    }

    private int listRow(GuiGraphics g, Font font, List<String> values, int y, int x, int vw, int addId, int clearId,
                        int mx, int my) {
        int buttons = 40 + 4 + 46;
        String text = values.isEmpty() ? "none" : String.join(", ", values);
        List<FormattedCharSequence> lines = font.split(Component.literal(text), vw - buttons - 6);
        int shown = Math.max(1, Math.min(3, lines.size()));
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, y + 3 + i * 9, values.isEmpty() ? HudModel.DIM : HudModel.WHITE, false);
        }
        if (lines.size() > 3 && isOver(mx, my, x, y, vw - buttons, 12 + 9 * 2)) tooltip = text;
        chip(g, font, addId, "+ ADD", x + vw - buttons, y, 40, false, mx, my, false);
        chip(g, font, clearId, "CLEAR", x + vw - 46, y, 46, false, mx, my, false);
        return Math.max(12, 6 + shown * 9);
    }

    private static boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String airName(KubeJsExport.Options o) {
        return switch (o.air()) {
            case KubeJsExport.AIR_PREDICATE -> "Predicates.air()";
            case KubeJsExport.AIR_BLOCK -> "a plain block";
            default -> "a space (anything)";
        };
    }

    private static String outputName(KubeJsExport.Options o) {
        String slice = o.gt7() ? "aisle" : "slice";
        return switch (o.style()) {
            case KubeJsExport.STYLE_MACHINE -> "the full machine definition";
            case KubeJsExport.STYLE_FULL -> "the whole .pattern(...) call";
            default -> "just the pattern (." + slice + " and .where lines)";
        };
    }

    private static String pos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * Draws a button and returns its height. Text that does not fit wraps onto further lines when {@code wrap} is set (the
     * button grows to match); otherwise it is cut and the whole text shows when the mouse is over it.
     */
    private int chip(GuiGraphics g, Font font, int id, String text, int x, int y, int w, boolean active, int mx, int my,
                     boolean wrap) {
        int inner = w - 6;
        List<FormattedCharSequence> lines;
        boolean cut = false;
        if (font.width(text) <= inner) {
            lines = List.of(Component.literal(text).getVisualOrderText());
        } else if (wrap) {
            lines = font.split(Component.literal(text), inner);
            if (lines.size() > 3) {
                lines = lines.subList(0, 3);
                cut = true;
            }
        } else {
            String shown = font.plainSubstrByWidth(text, inner - font.width("..")) + "..";
            lines = List.of(Component.literal(shown).getVisualOrderText());
            cut = true;
        }
        int h = 12 + 9 * (lines.size() - 1);

        boolean hover = isOver(mx, my, x, y, w, h);
        long age = System.currentTimeMillis() - flashes.getOrDefault(id, 0L);
        float flash = age < FLASH_MS ? 1f - age / (float) FLASH_MS : 0f;

        int fill = active ? GuiDraw.mix(T.bgTop(), T.accent(), 0.28f) : hover ?
                GuiDraw.mix(T.bgTop(), T.accent(), 0.14f) : T.bgTop();
        if (flash > 0f) fill = GuiDraw.mix(fill, 0xFFFFFFFF, 0.45f * flash);
        g.fill(x, y, x + w, y + h, fill);
        GuiDraw.drawBorder(g, x, y, w, h, flash > 0f ? HudModel.WHITE :
                active || hover ? T.accent() : GuiDraw.withAlpha(T.accent(), 0x77), 1);
        int color = active || flash > 0f ? HudModel.WHITE : T.accent();
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + (w - font.width(lines.get(i))) / 2, y + 2 + i * 9, color, false);
        }
        if (cut && hover) tooltip = text;
        controls.add(new Control(id, Kind.CHIP, x, y, w, h, 0, 0, 0, null));
        return h;
    }

    private void slider(GuiGraphics g, Font font, int id, String label, int x, int y, int w, int labelW, int value, int min,
                        int max, int step, int mx, int my) {
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
        g.fill(hx, y + 1, hx + 4, y + 11, hover || dragging != null && dragging.id() == id ? HudModel.WHITE : T.accent());
        g.drawString(font, String.valueOf(value), bx + bw + 6, y + 2, HudModel.WHITE, false);
        controls.add(new Control(id, Kind.SLIDER, bx - 2, y, bw + 4, 12, min, max, step, label));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double mx = localX(mouseX);
            double my = localY(mouseY);
            if (libraryTab && libMaxScroll > 0 && mx >= LIB_BAR_X - 3 && mx < LIB_BAR_X + 7 && my >= LIB_TOP - 2 &&
                    my < LIB_TOP - 2 + LIB_ROWS * LIB_ROW_H - 2) {
                libBarDrag = true;
                scrollLibraryTo(my);
                return true;
            }
            if (!libraryTab && !selection.exportMode && shapeMaxScroll > 0 && mx >= SHAPE_BAR_X - 3 && mx < SHAPE_BAR_X + 7 &&
                    my >= SHAPE_TOP && my < SHAPE_TOP + SHAPE_ROWS * 15 - 3) {
                shapeBarDrag = true;
                scrollShapesTo(my);
                return true;
            }
            for (Control c : controls) {
                if (!c.contains(mx, my)) continue;
                if (c.kind() == Kind.SLIDER) {
                    dragging = c;
                    lastSent = Integer.MIN_VALUE;
                    setFromMouse(c, mx);
                } else {
                    clicked(c.id());
                }
                return true;
            }
        }
        if (button == 1 && hasShiftDown() && !selection.exportMode && !libraryTab) {
            double mx = localX(mouseX);
            double my = localY(mouseY);
            for (Control c : controls) {
                if (c.id() == PART_BLOCK && c.contains(mx, my)) {
                    WandEditor.partBlock("");
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shapeBarDrag) {
            scrollShapesTo(localY(mouseY));
            return true;
        }
        if (libBarDrag) {
            scrollLibraryTo(localY(mouseY));
            return true;
        }
        if (dragging != null) {
            setFromMouse(dragging, localX(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        shapeBarDrag = false;
        libBarDrag = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double mx = localX(mouseX);
        double my = localY(mouseY);

        if (rulesPage && selection.exportMode && my >= RULES_TOP + 11 && my < H - 36) {
            rulesScroll = Math.max(0, rulesScroll - (int) Math.signum(delta) * 3);
            return true;
        }
        if (libraryTab) {
            if (my >= LIB_TOP + 2 && my < LIB_TOP + LIB_ROWS * LIB_ROW_H && mx >= 10 && mx < LIB_BAR_X + 7) {
                libScroll = Mth.clamp(libScroll - (int) Math.signum(delta) * 2, 0, libMaxScroll);
            }
            return true;
        }
        if (!selection.exportMode && shapeMaxScroll > 0 && mx >= 10 && mx < SHAPE_BAR_X + 7 && my >= SHAPE_TOP &&
                my < SHAPE_TOP + SHAPE_ROWS * 15) {
            shapeScroll = Mth.clamp(shapeScroll - (int) Math.signum(delta), 0, shapeMaxScroll);
            return true;
        }
        for (Control c : controls) {
            if (!c.contains(mx, my)) continue;
            int direction = delta > 0 ? 1 : -1;
            if (c.kind() == Kind.SLIDER) {
                int step = c.step() * (hasShiftDown() ? 3 : 1);
                applySlider(c, Mth.clamp(current(c.id()) + direction * step, c.min(), c.max()));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Scrolls the shape list so the bar's thumb is centred on the mouse. */
    private void scrollShapesTo(double my) {
        int trackH = SHAPE_ROWS * 15 - 3;
        double t = Mth.clamp((my - SHAPE_TOP - shapeBarThumb / 2.0) / Math.max(1, trackH - shapeBarThumb), 0.0, 1.0);
        shapeScroll = (int) Math.round(t * shapeMaxScroll);
    }

    private void setFromMouse(Control c, double mx) {
        double t = Mth.clamp((mx - (c.x() + 2)) / Math.max(1, c.w() - 4), 0.0, 1.0);
        int raw = c.min() + (int) Math.round(t * (c.max() - c.min()));
        int value = c.step() > 1 ? Math.round(raw / (float) c.step()) * c.step() : raw;
        value = Mth.clamp(value, c.min(), c.max());
        if (value == lastSent) return;
        lastSent = value;
        applySlider(c, value);
    }

    private int current(int id) {
        ShapeSpec spec = plan.current();
        return switch (id) {
            case SIZE_0, SIZE_0 + 1, SIZE_0 + 2, SIZE_0 + 3 -> spec.size[id - SIZE_0];
            case WALL -> spec.wall;
            case YAW -> spec.yaw;
            case PITCH -> spec.pitch;
            case ROLL -> spec.roll;
            case OFFSET_X -> spec.dx;
            case OFFSET_Y -> spec.dy;
            case OFFSET_Z -> spec.dz;
            case ARRAY_COUNT -> spec.arrayCount;
            case ARRAY_SPACING -> spec.arraySpacing;
            default -> 0;
        };
    }

    private void applySlider(Control c, int value) {
        int id = c.id();
        switch (id) {
            case SIZE_0, SIZE_0 + 1, SIZE_0 + 2, SIZE_0 + 3 -> WandEditor.setSize(id - SIZE_0, value);
            case WALL -> WandEditor.setField(WandEdits.FIELD_WALL, value);
            case YAW -> WandEditor.setField(WandEdits.FIELD_YAW, value);
            case PITCH -> WandEditor.setField(WandEdits.FIELD_PITCH, value);
            case ROLL -> WandEditor.setField(WandEdits.FIELD_ROLL, value);
            case OFFSET_X -> WandEditor.setField(WandEdits.FIELD_DX, value);
            case OFFSET_Y -> WandEditor.setField(WandEdits.FIELD_DY, value);
            case OFFSET_Z -> WandEditor.setField(WandEdits.FIELD_DZ, value);
            case ARRAY_COUNT -> WandEditor.setField(WandEdits.FIELD_ARRAY_COUNT, value);
            case ARRAY_SPACING -> WandEditor.setField(WandEdits.FIELD_ARRAY_SPACING, value);
            default -> {}
        }
        if (c.label() != null) status(c.label() + ": " + value, T.accent());
    }

    private void libSave() {
        String suggestion = libSelected.isEmpty() ? "my plan" : libSelected;
        Minecraft.getInstance().setScreen(new NameInputScreen(this, "Save the wand's plan as", suggestion, 40, typed -> {
            String name = PlanLibrary.cleanName(typed);
            if (name.isEmpty()) {
                status("A name needs letters or digits", HudModel.RED);
                return;
            }
            try {
                boolean replaced = PlanLibrary.exists(name);
                PlanLibrary.save(name, plan);
                libSelected = name;
                libDirty = true;
                libConfirmDelete = false;
                status((replaced ? "Replaced " : "Saved ") + name, HudModel.GREEN);
            } catch (java.io.IOException e) {
                status("Could not save: " + e.getMessage(), HudModel.RED);
            }
        }));
    }

    private void libLoad() {
        if (libSelected.isEmpty() || libPreview == null) {
            status(libSelected.isEmpty() ? "Pick a plan in the list first" : "That plan could not be read", HudModel.RED);
            return;
        }
        WandEditor.applyPlan(libPreview);
        status("Loaded " + libSelected + " onto the wand (pins are not kept)", HudModel.GREEN);
        libraryTab = false;
        WandEditor.export(ShapeExportActions.SET_BUILD);
    }

    private void libDelete() {
        if (libSelected.isEmpty()) {
            status("Pick a plan in the list first", HudModel.RED);
            return;
        }
        if (!libConfirmDelete) {
            libConfirmDelete = true;
            status("Click delete again to remove " + libSelected, HudModel.GOLD);
            return;
        }
        try {
            PlanLibrary.delete(libSelected);
            status("Deleted " + libSelected, HudModel.GREEN);
            libSelected = "";
        } catch (java.io.IOException e) {
            status("Could not delete: " + e.getMessage(), HudModel.RED);
        }
        libConfirmDelete = false;
        libDirty = true;
    }

    private void libCopy() {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(PlanLibrary.toShareString(plan));
            status("Copied the wand's plan: paste it in chat or a file to share it", HudModel.GREEN);
        } catch (java.io.IOException e) {
            status("Could not copy the plan", HudModel.RED);
        }
    }

    private void libPaste() {
        try {
            WandEditor.applyPlan(PlanLibrary.fromShareString(Minecraft.getInstance().keyboardHandler.getClipboard()));
            status("Pasted: the wand has the plan (SAVE WAND PLAN keeps it)", HudModel.GREEN);
            libraryTab = false;
            WandEditor.export(ShapeExportActions.SET_BUILD);
        } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException e) {
            status("The clipboard does not hold a blueprint plan", HudModel.RED);
        }
    }

    private void libFolder() {
        try {
            java.nio.file.Files.createDirectories(PlanLibrary.dir());
            net.minecraft.Util.getPlatform().openFile(PlanLibrary.dir().toFile());
        } catch (java.io.IOException e) {
            status("Could not open the folder", HudModel.RED);
        }
    }

    private void scrollLibraryTo(double my) {
        int trackH = LIB_ROWS * LIB_ROW_H - 2;
        double t = Mth.clamp((my - (LIB_TOP - 2) - libBarThumb / 2.0) / Math.max(1, trackH - libBarThumb), 0.0, 1.0);
        libScroll = (int) Math.round(t * libMaxScroll);
    }

    /** A button was clicked: light it, click, do it, and say what changed. */
    private void clicked(int id) {
        flashes.put(id, System.currentTimeMillis());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        press(id);
        if (refresh()) feedback(id);
    }

    /** What the click did, read from the wand after the change was applied. */
    private void feedback(int id) {
        KubeJsExport.Options o = selection.options;
        ShapeSpec spec = plan.current();
        String text = switch (id) {
            case MODE_BUILD -> "Build mode";
            case MODE_EXPORT -> "Export mode";
            case LIB_TAB -> "Plan library";
            case X_STYLE, M_OUTPUT -> "Output: " + outputName(o);
            case X_CASE -> "Letters: " + (o.uppercaseFirst() ? "A-Z first" : "a-z first");
            case X_AIR -> "Air: " + airName(o);
            case X_PARTS -> "Hatches and buses: " + (o.partsAsAbilities() ? "as abilities" : "exact blocks");
            case X_LANGUAGE -> "Language: " + (o.java() ? "Java" : "KubeJS");
            case X_VERSION -> "GregTech " + (o.gt7() ? "7 / 8.0 (.aisle)" : "8 snapshot (.slice)");
            case X_CONTROLLER -> selection.controllerMode ? "Right-click a block to mark the controller" :
                    "Right-click a block to set corners";
            case X_CLEAR -> "Selection cleared";
            case X_COPY -> "Copying the output...";
            case X_SELECT_BUILD -> "Selecting the last build...";
            case X_AUTO_SELECT -> "Selecting the structure around the controller...";
            case X_OPEN_FOLDER -> "Opening blueprint/exports";
            case X_IMPORT_TURN -> "Turned a quarter turn";
            case X_IMPORT_LOCK -> ImportHologram.locked() ? "Locked in place: aim anywhere, it stays" : "Follows the aim again";
            case X_IMPORT_PLACE -> "Building the import...";
            case X_IMPORT_CLEAR -> "Import cleared";
            case X_ADD_STRUCTURE -> "Added " + selection.name() + ": mark its corners and controller";
            case X_REMOVE_STRUCTURE -> "Structure removed";
            case M_ROTATION -> "Rotation: " + machine.rotationName();
            case M_CLEAR_TYPES -> "Recipe types cleared";
            case M_CLEAR_MODIFIERS -> "Recipe modifiers cleared";
            case HOLLOW -> spec.hollow ? "Hollow" : "Solid";
            case OPEN_ENDS -> spec.open ? "Open: a hollow shape with no ends or base, like a pipe or tower (shapes with sides only)" :
                    "Closed: a hollow shape keeps its ends";
            case SIT_ON_BASE -> "Sit on the base: " + (plan.sitOnBase ? "on" : "off");
            case CONNECTED -> "No diagonal gaps: " + (plan.connected ? "on" : "off");
            case OVERWRITE -> "Overwrite blocks: " + (plan.overwrite ? "on" : "off");
            case PIN_PART -> spec.locked ? "Part pinned at " + spec.pinX + " " + spec.pinY + " " + spec.pinZ :
                    "Part follows the aim";
            case PIN_ALL -> plan.parts.stream().allMatch(part -> part.locked) ? "All parts pinned" : "Parts follow the aim";
            case PART_BLOCK -> spec.block.isEmpty() ? "This part builds from the block in your other hand: click to pick its own" :
                    spec.block + " (shift + right-click to use the block in your other hand again)";
            case SEND_EXPORT -> "Selects the shape you just built and switches to export mode";
            case ARRAY_MODE -> switch (spec.arrayMode) {
                case ShapeSpec.ARRAY_OFF -> "No repeats: click to repeat this part";
                case ShapeSpec.ARRAY_RING -> "Repeats around the anchor's vertical axis";
                default -> "Repeats in a line";
            };
            case MIRROR_X, MIRROR_Y, MIRROR_Z -> "Adds a mirror image of this part across the plane through the anchor";
            case ORIENT -> "Turn blocks: " + (plan.orient == ShapePlan.ORIENT_OFF ? "off, blocks keep their default direction" :
                    plan.orient == ShapePlan.ORIENT_OUT ? "facing away from the surface" : "facing toward the surface");
            case UNDO -> "Undoing the last build...";
            case RESET -> "Reset to one default ring";
            case ADD_PART -> "Part " + (plan.selected + 1) + " added";
            case REMOVE_PART -> "Part removed";
            default -> id >= SHAPE && id < SHAPE + ShapeKind.values().length ? "Shape: " + spec.kind.displayName() :
                    id >= PRESET && id < PRESET + 4 ? "Rotation: yaw " + spec.yaw + ", pitch " + spec.pitch + ", roll " +
                            spec.roll :
                            id >= PART && id < PART + ShapePlan.MAX_PARTS ? "Part " + (plan.selected + 1) :
                                    id >= X_STRUCTURE && id < X_STRUCTURE + WandSelection.MAX_STRUCTURES ?
                                            "Structure: " + selection.name() : null;
        };
        if (text != null) status(text, T.accent());
    }

    private void press(int id) {
        if (id >= LIB_ROW && id < LIB_ROW + 200) {
            int index = id - LIB_ROW;
            if (index < libEntries.size()) {
                libSelected = libEntries.get(index).name();
                libConfirmDelete = false;
                loadPreview();
            }
            return;
        }
        if (id >= X_NUDGE && id < X_NUDGE + 6) {
            int axis = (id - X_NUDGE) / 2;
            int sign = (id - X_NUDGE) % 2 == 0 ? -1 : 1;
            ImportHologram.nudge(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);
            return;
        }
        if (id >= R_ROWS) {
            pressRule(id);
            return;
        }
        ShapeSpec spec = plan.current();
        if (id >= SHAPE && id < SHAPE + ShapeKind.values().length) {
            WandEditor.setKind(id - SHAPE);
        } else if (id >= PART && id < PART + ShapePlan.MAX_PARTS) {
            WandEditor.select((id - PART) - plan.selected);
        } else if (id >= X_STRUCTURE && id < X_STRUCTURE + WandSelection.MAX_STRUCTURES) {
            WandEditor.export(ShapeExportActions.SELECT_STRUCTURE + (id - X_STRUCTURE));
        } else if (id >= PRESET && id < PRESET + 4) {
            WandEditor.preset(id - PRESET);
        } else {
            switch (id) {
                case ADD_PART -> WandEditor.send(WandEdits.ADD);
                case REMOVE_PART -> WandEditor.send(WandEdits.REMOVE);
                case HOLLOW -> WandEditor.setFlag(WandEdits.FLAG_HOLLOW, !spec.hollow);
                case OPEN_ENDS -> WandEditor.setFlag(WandEdits.FLAG_OPEN, !spec.open);
                case SIT_ON_BASE -> WandEditor.setFlag(WandEdits.FLAG_SIT_ON_BASE, !plan.sitOnBase);
                case OVERWRITE -> WandEditor.setFlag(WandEdits.FLAG_OVERWRITE, !plan.overwrite);
                case CONNECTED -> WandEditor.setFlag(WandEdits.FLAG_CONNECTED, !plan.connected);
                case PART_BLOCK -> WandPickers.pickPartBlock(this);
                case SEND_EXPORT -> WandEditor.export(ShapeExportActions.SEND_TO_EXPORT);
                case ARRAY_MODE -> WandEditor.setField(WandEdits.FIELD_ARRAY_MODE, spec.arrayMode + 1);
                case MIRROR_X -> WandEditor.setFlag(WandEdits.FLAG_MIRROR_X, !spec.mirrorX);
                case MIRROR_Y -> WandEditor.setFlag(WandEdits.FLAG_MIRROR_Y, !spec.mirrorY);
                case MIRROR_Z -> WandEditor.setFlag(WandEdits.FLAG_MIRROR_Z, !spec.mirrorZ);
                case ORIENT -> WandEditor.orient(Math.floorMod(plan.orient + 1, 3));
                case UNDO -> WandEditor.send(WandEdits.UNDO);
                case RESET -> WandEditor.send(WandEdits.RESET);
                case MODE_BUILD -> {
                    libraryTab = false;
                    WandEditor.export(ShapeExportActions.SET_BUILD);
                }
                case MODE_EXPORT -> {
                    libraryTab = false;
                    WandEditor.export(ShapeExportActions.SET_EXPORT);
                }
                case LIB_TAB -> {
                    libraryTab = true;
                    libDirty = true;
                }
                case LIB_SAVE -> libSave();
                case LIB_LOAD -> libLoad();
                case LIB_DELETE -> libDelete();
                case LIB_COPY -> libCopy();
                case LIB_PASTE -> libPaste();
                case LIB_FOLDER -> libFolder();
                case X_CONTROLLER -> WandEditor.export(ShapeExportActions.TOGGLE_CONTROLLER);
                case X_SELECT_BUILD -> WandEditor.export(ShapeExportActions.SELECT_BUILD);
                case X_CLEAR -> WandEditor.export(ShapeExportActions.CLEAR);
                case X_COPY -> WandEditor.export(ShapeExportActions.COPY);
                case X_CASE -> WandEditor.export(ShapeExportActions.CYCLE_CASE);
                case X_AIR -> WandEditor.export(ShapeExportActions.CYCLE_AIR);
                case X_PARTS -> WandEditor.export(ShapeExportActions.TOGGLE_PARTS);
                case X_STYLE -> WandEditor.export(ShapeExportActions.TOGGLE_STYLE);
                case X_LANGUAGE -> WandEditor.export(ShapeExportActions.TOGGLE_LANGUAGE);
                case X_VERSION -> WandEditor.export(ShapeExportActions.TOGGLE_VERSION);
                case X_ADD_STRUCTURE -> WandEditor.export(ShapeExportActions.ADD_STRUCTURE);
                case X_REMOVE_STRUCTURE -> WandEditor.export(ShapeExportActions.REMOVE_STRUCTURE);
                case X_MACHINE_PAGE -> machinePage = true;
                case X_RULES_PAGE -> {
                    rulesPage = true;
                    rulesScroll = 0;
                }
                case R_BACK -> rulesPage = false;
                case R_RESET_ALL -> {
                    for (String key : new ArrayList<>(limits.rules.keySet())) {
                        WandEditor.rule(key, PredicateRules.FIELD_RESET, 0);
                    }
                    status("All limits reset", T.accent());
                }
                case X_AUTO_SELECT -> WandEditor.export(ShapeExportActions.AUTO_SELECT);
                case X_OPEN_FOLDER -> ShapeExportClient.openExportsFolder();
                case X_IMPORT -> status(ImportHologram.importClipboard(), T.accent());
                case X_IMPORT_TURN -> ImportHologram.turn();
                case X_IMPORT_LOCK -> {
                    if (!ImportHologram.toggleLock()) status("Aim the wand at a block first", HudModel.GOLD);
                }
                case X_IMPORT_PLACE -> {
                    if (!ImportHologram.place()) status("Aim the wand at a block first", HudModel.GOLD);
                }
                case X_IMPORT_CLEAR -> ImportHologram.clear();
                case M_BACK -> machinePage = false;
                case X_RENAME -> WandPickers.typeName(this, MachineSettings.RENAME_STRUCTURE, "Name this structure",
                        selection.name());
                case M_NAME -> WandPickers.typeName(this, MachineSettings.NAME, "Machine id", machine.id());
                case M_ROTATION -> WandEditor.export(ShapeExportActions.CYCLE_ROTATION);
                case M_ADD_TYPE -> WandPickers.pickRecipeType(this);
                case M_CLEAR_TYPES -> WandEditor.export(ShapeExportActions.CLEAR_TYPES);
                case M_ADD_MODIFIER -> WandPickers.pickRecipeModifier(this);
                case M_CLEAR_MODIFIERS -> WandEditor.export(ShapeExportActions.CLEAR_MODIFIERS);
                case M_APPEARANCE -> WandPickers.pickBlock(this, MachineSettings.APPEARANCE, "Appearance block");
                case M_CASING -> WandPickers.pickTexture(this, MachineSettings.CASING_TEXTURE, "Casing texture");
                case M_OVERLAY -> WandPickers.pickTexture(this, MachineSettings.OVERLAY_TEXTURE, "Overlay texture");
                case M_OUTPUT -> WandEditor.export(ShapeExportActions.TOGGLE_MACHINE_STYLE);
                case X_PREVIEW -> {
                    var structures = selection.structures();
                    ShapeExportClient.openPreview(structures.get(selection.currentIndex()));
                }
                case PIN_PART, PIN_ALL -> {
                    boolean all = id == PIN_ALL;
                    boolean pinned = all ? plan.parts.stream().allMatch(part -> part.locked) : spec.locked;
                    BlockPos at = ShapeHologram.anchor();
                    if (pinned) WandEditor.unpin(all);
                    else if (at != null) WandEditor.pin(all, at, ShapeHologram.dimension());
                    else status("Aim the wand at a block first", HudModel.GOLD);
                }
                default -> {}
            }
        }
    }
}