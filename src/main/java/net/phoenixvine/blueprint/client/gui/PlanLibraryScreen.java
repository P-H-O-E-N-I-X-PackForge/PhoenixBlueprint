package net.phoenixvine.blueprint.client.gui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.blueprint.client.PlanLibrary;
import net.phoenixvine.blueprint.client.ShapeExportClient;
import net.phoenixvine.blueprint.client.WandEditor;
import net.phoenixvine.blueprint.common.ShapePlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The plan library: the shape plans saved on this computer (see {@link PlanLibrary}). Type a name and save the wand's plan
 * under it, pick a saved plan to load it onto the wand, or copy the plan as a line of text to share and paste one back in.
 * Opens over the editor and returns to it.
 */
public final class PlanLibraryScreen extends Screen {

    private static final int ROWS = 7;
    private static final int PANEL_W = 260;

    private final Screen parent;
    private EditBox nameBox;
    private List<PlanLibrary.Entry> entries = new ArrayList<>();
    private int page = 0;
    private String selected = "";
    private boolean confirmDelete = false;

    private String status = "";
    private int statusColor = 0xFFFFFF;

    public PlanLibraryScreen(Screen parent) {
        super(Component.literal("Plan library"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries = PlanLibrary.list();
        page = Math.max(0, Math.min(page, (Math.max(1, entries.size()) - 1) / ROWS));

        int x = width / 2 - PANEL_W / 2;
        int y = height / 2 - 100;

        nameBox = new EditBox(font, x, y + 14, PANEL_W, 18, Component.literal("Name"));
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.literal("Name for this plan"));
        nameBox.setValue(selected);
        nameBox.setResponder(text -> confirmDelete = false);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int third = (PANEL_W - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, y + 36, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), b -> load()).bounds(x + third + 4, y + 36, third, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Sure?" : "Delete"), b -> delete())
                .bounds(x + 2 * (third + 4), y + 36, third, 18).build());

        int listY = y + 60;
        int from = page * ROWS;
        for (int i = 0; i < ROWS && from + i < entries.size(); i++) {
            PlanLibrary.Entry entry = entries.get(from + i);
            String label = entry.name() + "  -  " + entry.summary();
            if (font.width(label) > PANEL_W - 10) label = font.plainSubstrByWidth(label, PANEL_W - 10 - font.width("..")) + "..";
            Button row = Button.builder(Component.literal(label), b -> pick(entry.name()))
                    .bounds(x, listY + i * 20, PANEL_W, 18).build();
            addRenderableWidget(row);
        }

        int pagerY = listY + ROWS * 20 + 2;
        Button prev = Button.builder(Component.literal("<"), b -> turn(-1)).bounds(x, pagerY, 24, 18).build();
        Button next = Button.builder(Component.literal(">"), b -> turn(1)).bounds(x + PANEL_W - 24, pagerY, 24, 18).build();
        prev.active = page > 0;
        next.active = (page + 1) * ROWS < entries.size();
        addRenderableWidget(prev);
        addRenderableWidget(next);

        int half = (PANEL_W - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Copy as text"), b -> copy())
                .bounds(x, pagerY + 24, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Paste from text"), b -> paste())
                .bounds(x + half + 4, pagerY + 24, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> openFolder())
                .bounds(x, pagerY + 46, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x + half + 4, pagerY + 46, half, 18).build());
    }

    private void turn(int by) {
        page += by;
        rebuildWidgets();
    }

    private void pick(String name) {
        selected = name;
        confirmDelete = false;
        rebuildWidgets();
    }

    private void say(String text, boolean good) {
        status = text;
        statusColor = good ? 0x55FF55 : 0xFF5555;
    }

    private @org.jetbrains.annotations.Nullable ItemStack wand() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : ShapeExportClient.wand(player);
    }

    private String name() {
        return PlanLibrary.cleanName(nameBox.getValue());
    }

    private void save() {
        ItemStack stack = wand();
        String name = name();
        if (stack == null) {
            say("Hold the blueprint wand first", false);
        } else if (name.isEmpty()) {
            say("Type a name first", false);
        } else {
            try {
                boolean replaced = PlanLibrary.exists(name);
                PlanLibrary.save(name, ShapePlan.read(stack));
                selected = name;
                say((replaced ? "Replaced " : "Saved ") + name, true);
            } catch (IOException e) {
                say("Could not save: " + e.getMessage(), false);
            }
        }
        confirmDelete = false;
        rebuildWidgets();
    }

    private void load() {
        String name = name();
        if (name.isEmpty() || !PlanLibrary.exists(name)) {
            say(name.isEmpty() ? "Pick a plan or type its name" : "No plan called " + name, false);
            return;
        }
        try {
            WandEditor.applyPlan(PlanLibrary.load(name));
            selected = name;
            say("Loaded " + name + " (pins are not kept)", true);
        } catch (IOException | CommandSyntaxException | RuntimeException e) {
            say("Could not read " + name, false);
        }
    }

    private void delete() {
        String name = name();
        if (name.isEmpty() || !PlanLibrary.exists(name)) {
            say(name.isEmpty() ? "Pick a plan or type its name" : "No plan called " + name, false);
            return;
        }
        if (!confirmDelete) {
            confirmDelete = true;
            say("Click Delete again to remove " + name, false);
            rebuildWidgets();
            return;
        }
        try {
            PlanLibrary.delete(name);
            say("Deleted " + name, true);
            selected = "";
        } catch (IOException e) {
            say("Could not delete: " + e.getMessage(), false);
        }
        confirmDelete = false;
        rebuildWidgets();
    }

    private void copy() {
        ItemStack stack = wand();
        if (stack == null) {
            say("Hold the blueprint wand first", false);
            return;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(PlanLibrary.toShareString(ShapePlan.read(stack)));
            say("Copied: paste it in chat or a file to share", true);
        } catch (IOException e) {
            say("Could not copy the plan", false);
        }
    }

    private void paste() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            WandEditor.applyPlan(PlanLibrary.fromShareString(text));
            say("Pasted: the wand has the plan (Save keeps it)", true);
        } catch (IOException | CommandSyntaxException | IllegalArgumentException e) {
            say("The clipboard does not hold a blueprint plan", false);
        }
    }

    private void openFolder() {
        try {
            java.nio.file.Files.createDirectories(PlanLibrary.dir());
            net.minecraft.Util.getPlatform().openFile(PlanLibrary.dir().toFile());
        } catch (IOException e) {
            say("Could not open the folder", false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = width / 2 - PANEL_W / 2;
        int y = height / 2 - 100;
        g.drawString(font, "PLAN LIBRARY", x, y, 0xFFFFFF, false);
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "Nothing saved yet", width / 2, y + 60 + 4, 0xAAAAAA);
        } else {
            String count = (page + 1) + "/" + ((entries.size() - 1) / ROWS + 1);
            g.drawCenteredString(font, count, width / 2, y + 60 + ROWS * 20 + 7, 0xAAAAAA);
        }
        if (!status.isEmpty()) g.drawCenteredString(font, status, width / 2, y + 60 + ROWS * 20 + 118, statusColor);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
