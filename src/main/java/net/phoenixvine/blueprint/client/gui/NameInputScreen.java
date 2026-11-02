package net.phoenixvine.blueprint.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A small box to type a name into; the name goes to the callback and the screen returns to where it came from. */
public final class NameInputScreen extends Screen {

    private final Screen parent;
    private final String label;
    private final String initial;
    private final int maxLength;
    private final Consumer<String> onDone;
    private EditBox box;

    public NameInputScreen(Screen parent, String label, String initial, int maxLength, Consumer<String> onDone) {
        super(Component.literal(label));
        this.parent = parent;
        this.label = label;
        this.initial = initial;
        this.maxLength = maxLength;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;
        box = new EditBox(font, x, y, 200, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("OK"), b -> confirm()).bounds(x, y + 26, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 102, y + 26, 98, 20)
                .build());
    }

    private void confirm() {
        String text = box.getValue().trim();
        minecraft.setScreen(parent);
        if (!text.isEmpty()) onDone.accept(text);
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
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, label, width / 2 - 100, height / 2 - 34, 0xFFFFFF, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
