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
import net.phoenixvine.blueprint.client.ShapeExportClient;
import net.phoenixvine.blueprint.client.ShapeHologram;
import net.phoenixvine.blueprint.client.WandEditor;
import net.phoenixvine.blueprint.client.WandPickers;
import net.phoenixvine.blueprint.client.gui.hud.GuiDraw;
import net.phoenixvine.blueprint.client.gui.hud.HudModel;
import net.phoenixvine.blueprint.client.gui.hud.HudTheme;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.ShapeKind;
import net.phoenixvine.blueprint.common.ShapePlan;
import net.phoenixvine.blueprint.common.ShapeSpec;
import net.phoenixvine.blueprint.common.WandEdits;
import net.phoenixvine.blueprint.common.export.KubeJsExport;
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
    private static final int H = 308;
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

    private boolean machinePage = false;
    private final List<Control> controls = new ArrayList<>();
    private final Map<Integer, Long> flashes = new HashMap<>();
    private Control dragging;
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
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!refresh()) {
            onClose();
            return;
        }
        renderBackground(g, mouseX, mouseY, partialTick);
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
        chip(g, font, MODE_BUILD, "BUILD", W - 10 - 98, 25, 46, !exporting, mx, my, false);
        chip(g, font, MODE_EXPORT, "EXPORT", W - 10 - 48, 25, 48, exporting, mx, my, false);
        if (!exporting) {
            String summary = ShapeHologramInfo.summary();
            int summaryColor = ShapeHologramInfo.tooBig ? HudModel.RED :
                    ShapeHologramInfo.haveBlock ? HudModel.GREEN : HudModel.GOLD;
            g.drawString(font, summary, W - 10 - font.width(summary), 8, summaryColor, false);
            paintBuild(g, font, mx, my);
        } else if (machinePage) {
            paintMachine(g, font, mx, my);
        } else {
            paintExport(g, font, mx, my);
        }

        g.pose().popPose();

        if (tooltip != null) g.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
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

        // left column: the shape and its sizes
        int lx = 10;
        g.drawString(font, "SHAPE", lx, 44, HudModel.DIM, false);
        ShapeKind[] kinds = ShapeKind.values();
        for (int i = 0; i < kinds.length; i++) {
            chip(g, font, SHAPE + i, kinds[i].displayName(), lx + (i % 3) * 71, 55 + (i / 3) * 15, 69,
                    kinds[i] == spec.kind, mx, my, false);
        }
        int sy = 55 + ((kinds.length + 2) / 3) * 15 + 6;
        g.drawString(font, "SIZE", lx, sy, HudModel.DIM, false);
        for (int i = 0; i < 4; i++) {
            ShapeKind.Param param = spec.kind.param(i);
            if (param == null) continue;
            slider(g, font, SIZE_0 + i, param.label(), lx, sy + 11 + i * 15, 212, 78, spec.size[i], param.min(),
                    param.max(), 1, mx, my);
        }
        int hy = sy + 11 + 4 * 15 + 2;
        chip(g, font, HOLLOW, spec.hollow ? "HOLLOW" : "SOLID", lx, hy, 64, spec.hollow, mx, my, false);
        slider(g, font, WALL, "Wall", lx + 70, hy, 142, 32, spec.wall, 1, 16, 1, mx, my);

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
        int ny = 242;
        for (var line : font.split(Component.literal(pinNote), 212)) {
            g.drawString(font, line, lx, ny, spec.locked ? T.accent() : HudModel.DIM, false);
            ny += 10;
        }

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
        ry += chip(g, font, X_CLEAR, "CLEAR SELECTION", rx, ry, rw, false, mx, my, true) + 3;
        ry += chip(g, font, X_COPY, "COPY OUTPUT", rx, ry, rw, sel.hasBothCorners(), mx, my, true) + 3;
        if (ShapeExportClient.phantasiaAvailable()) {
            ry += chip(g, font, X_PREVIEW, "PREVIEW (PHANTASIA)", rx, ry, rw, false, mx, my, true) + 3;
        }
        chip(g, font, X_MACHINE_PAGE, "MACHINE DEFINITION >", rx, ry, rw, o.style() == KubeJsExport.STYLE_MACHINE, mx,
                my, true);

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
        y += chip(g, font, X_VERSION, "GregTech: " + (o.gt7() ? "7 (1.20.1, .aisle)" : "8 (1.21.1, .slice)"), 10, y, lw,
                o.gt7(), mx, my, true) + 3;

        y += 4;
        String help = "Right-click a block: corner 2 (sneak: corner 1). Mark the controller first if it is not a block " +
                "facing south. Right-click air or COPY OUTPUT to put the result on your clipboard. Build a design in " +
                "BUILD mode, then SELECT LAST BUILD.";
        for (var line : font.split(Component.literal(help), W - 20)) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            setFromMouse(dragging, localX(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = localX(mouseX);
        double my = localY(mouseY);
        for (Control c : controls) {
            if (!c.contains(mx, my)) continue;
            int direction = scrollY > 0 ? 1 : -1;
            if (c.kind() == Kind.SLIDER) {
                int step = c.step() * (hasShiftDown() ? 3 : 1);
                applySlider(c, Mth.clamp(current(c.id()) + direction * step, c.min(), c.max()));
                return true;
            }
            if (c.id() >= SHAPE && c.id() < SHAPE + ShapeKind.values().length) {
                WandEditor.changeKind(-direction);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
            default -> {}
        }
        if (c.label() != null) status(c.label() + ": " + value, T.accent());
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
            case X_STYLE, M_OUTPUT -> "Output: " + outputName(o);
            case X_CASE -> "Letters: " + (o.uppercaseFirst() ? "A-Z first" : "a-z first");
            case X_AIR -> "Air: " + airName(o);
            case X_PARTS -> "Hatches and buses: " + (o.partsAsAbilities() ? "as abilities" : "exact blocks");
            case X_LANGUAGE -> "Language: " + (o.java() ? "Java" : "KubeJS");
            case X_VERSION -> "GregTech " + (o.gt7() ? "7 (.aisle)" : "8 (.slice)");
            case X_CONTROLLER -> selection.controllerMode ? "Right-click a block to mark the controller" :
                    "Right-click a block to set corners";
            case X_CLEAR -> "Selection cleared";
            case X_COPY -> "Copying the output...";
            case X_SELECT_BUILD -> "Selecting the last build...";
            case X_ADD_STRUCTURE -> "Added " + selection.name() + ": mark its corners and controller";
            case X_REMOVE_STRUCTURE -> "Structure removed";
            case M_ROTATION -> "Rotation: " + machine.rotationName();
            case M_CLEAR_TYPES -> "Recipe types cleared";
            case M_CLEAR_MODIFIERS -> "Recipe modifiers cleared";
            case HOLLOW -> spec.hollow ? "Hollow" : "Solid";
            case SIT_ON_BASE -> "Sit on the base: " + (plan.sitOnBase ? "on" : "off");
            case CONNECTED -> "No diagonal gaps: " + (plan.connected ? "on" : "off");
            case OVERWRITE -> "Overwrite blocks: " + (plan.overwrite ? "on" : "off");
            case PIN_PART -> spec.locked ? "Part pinned at " + spec.pinX + " " + spec.pinY + " " + spec.pinZ :
                    "Part follows the aim";
            case PIN_ALL -> plan.parts.stream().allMatch(part -> part.locked) ? "All parts pinned" : "Parts follow the aim";
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
                case SIT_ON_BASE -> WandEditor.setFlag(WandEdits.FLAG_SIT_ON_BASE, !plan.sitOnBase);
                case OVERWRITE -> WandEditor.setFlag(WandEdits.FLAG_OVERWRITE, !plan.overwrite);
                case CONNECTED -> WandEditor.setFlag(WandEdits.FLAG_CONNECTED, !plan.connected);
                case UNDO -> WandEditor.send(WandEdits.UNDO);
                case RESET -> WandEditor.send(WandEdits.RESET);
                case MODE_BUILD -> WandEditor.export(ShapeExportActions.SET_BUILD);
                case MODE_EXPORT -> WandEditor.export(ShapeExportActions.SET_EXPORT);
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
