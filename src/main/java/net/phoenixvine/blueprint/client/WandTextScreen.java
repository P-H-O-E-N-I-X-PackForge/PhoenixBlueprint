package net.phoenixvine.blueprint.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small box to type a name into (the machine's id, a substructure's name); the result goes to the wand in a packet. */
public final class WandTextScreen extends Screen {

    private final Screen parent;
    private final int field;
    private final String label;
    private final String initial;
    private EditBox box;

    public WandTextScreen(Screen parent, int field, String label, String initial) {
        super(Component.literal(label));
        this.parent = parent;
        this.field = field;
        this.label = label;
        this.initial = initial;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;
        box = new EditBox(font, x, y, 200, 20, Component.literal(label));
        box.setMaxLength(48);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirm()).bounds(x, y + 26, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 102, y + 26, 98, 20)
                .build());
    }

    private void confirm() {
        String text = box.getValue().trim();
        if (!text.isEmpty()) WandEditor.machine(field, text);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, label, width / 2, height / 2 - 34, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
