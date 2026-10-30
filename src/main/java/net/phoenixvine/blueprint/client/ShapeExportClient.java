package net.phoenixvine.blueprint.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.BlueprintWandItem;
import net.phoenixvine.blueprint.common.export.C2SBlueprintWandActionPacket;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The client side of the wand's export mode: the selection boxes in the world, the info above the hotbar, the three keys
 * and putting the copied KubeJS on the clipboard.
 */
@EventBusSubscriber(modid = Blueprint.MOD_ID, value = Dist.CLIENT)
public final class ShapeExportClient {

    private static final String CATEGORY = "key.categories.phoenix_blueprint";

    public static final KeyMapping MODE_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
    public static final KeyMapping CONTROLLER_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_controller",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    public static final KeyMapping COPY_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_copy",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);

    private static final int MAX_CLIPBOARD = 32767;

    private ShapeExportClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MODE_KEY);
        event.register(CONTROLLER_KEY);
        event.register(COPY_KEY);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Blueprint.id("blueprint_wand_export"), (graphics, deltaTracker) -> renderHud(graphics));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null && wand(mc.player) != null;
        if (holding) updateDerivedCasing(mc);
        while (MODE_KEY.consumeClick()) {
            if (holding) PacketDistributor.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.TOGGLE_MODE));
        }
        while (CONTROLLER_KEY.consumeClick()) {
            if (holding) {
                PacketDistributor.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.TOGGLE_CONTROLLER));
            }
        }
        while (COPY_KEY.consumeClick()) {
            if (holding) PacketDistributor.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.COPY));
        }
    }

    /** Whether Phantasia is installed, so the selection can be previewed in its 3D viewer. */
    public static boolean phantasiaAvailable() {
        return net.neoforged.fml.ModList.get().isLoaded("phantasia");
    }

    /** Opens the structure in Phantasia's viewer as if it were a registered multiblock. */
    public static void openPreview(net.phoenixvine.blueprint.common.export.WandSelection.Structure structure) {
        if (phantasiaAvailable()) WandPhantasiaPreview.open(structure);
    }

    private static String lastDerivedFor = "";

    /**
     * With no casing texture picked, the machine model falls back to the texture of the appearance block, which itself falls
     * back to the block marked as the controller. Only the client knows block models, so it works the texture out and stores it
     * on the wand (without reopening the editor).
     */
    private static void updateDerivedCasing(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack stack = wand(player);
        if (stack == null || mc.level == null) return;
        WandSelection sel = WandSelection.read(stack);
        if (!sel.exportMode) return;
        net.phoenixvine.blueprint.common.export.MachineSettings settings =
                net.phoenixvine.blueprint.common.export.MachineSettings.read(stack);
        if (!settings.casingTexture.isEmpty()) return;

        net.minecraft.world.level.block.state.BlockState state = null;
        if (!settings.appearanceBlock.isEmpty()) {
            var id = ResourceLocation.tryParse(settings.appearanceBlock);
            if (id != null) state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        } else {
            for (var s : sel.structures()) {
                if (s.controller != null) {
                    state = mc.level.getBlockState(s.controller);
                    break;
                }
            }
        }
        if (state == null || state.isAir()) return;

        String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "|" +
                settings.derivedCasingTexture;
        if (key.equals(lastDerivedFor)) return;
        lastDerivedFor = key;
        ResourceLocation sprite = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state).contents().name();
        if (sprite.getPath().equals("missingno") || sprite.toString().equals(settings.derivedCasingTexture)) return;
        PacketDistributor.sendToServer(new net.phoenixvine.blueprint.common.export.C2SShapeMachinePacket(
                net.phoenixvine.blueprint.common.export.MachineSettings.DERIVED_CASING, sprite.toString()));
    }

    public static @Nullable ItemStack wand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BlueprintWandItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof BlueprintWandItem) return player.getOffhandItem();
        return null;
    }

    // ------------------------------------------------------------------ clipboard

    /** Copies the text; one too long for the clipboard is written to packet.txt and its path is copied instead. */
    public static void setClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (text.length() <= MAX_CLIPBOARD) {
            mc.keyboardHandler.setClipboard(text);
            return;
        }
        try {
            Path path = Path.of("packet.txt");
            Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            mc.keyboardHandler.setClipboard("Data saved to: " + path.toAbsolutePath());
            mc.getToasts().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Blueprint Wand"),
                    Component.literal("The selection was too big for the clipboard; its file path was copied instead")));
        } catch (Exception e) {
            mc.keyboardHandler.setClipboard(text.substring(0, MAX_CLIPBOARD));
        }
    }

    // ------------------------------------------------------------------ the info above the hotbar

    private static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = wand(player);
        if (stack == null) return;
        WandSelection sel = WandSelection.read(stack);
        if (!sel.exportMode) return;

        // on the left edge, clear of the action bar where the messages ("Copied blocks.") are shown
        int x = 8;
        int y = g.guiHeight() / 2 - 30;
        g.drawString(mc.font, "[" + sel.name() + "] " + (sel.controllerMode ?
                "marking the CONTROLLER (sneak + click unmarks)" : "marking corners (sneak = corner 1)"), x, y,
                0xFFAA00, true);
        y += 12;
        g.drawString(mc.font, "Corner 1: " + text(sel.corner1), x, y, 0xFFFFFF, true);
        g.drawString(mc.font, "Corner 2: " + text(sel.corner2), x, y + 10, 0xFFFFFF, true);
        g.drawString(mc.font, "Controller: " + (sel.controller == null ? "- (optional)" : text(sel.controller)), x,
                y + 20, 0xFFAA55, true);
        if (sel.hasBothCorners()) {
            BlockPos low = sel.low();
            BlockPos high = sel.high();
            int sx = high.getX() - low.getX() + 1, sy = high.getY() - low.getY() + 1, sz = high.getZ() - low.getZ() + 1;
            g.drawString(mc.font, "Size: " + sx + "x" + sy + "x" + sz + "   Blocks: " + (long) sx * sy * sz +
                    "   Copy: right-click air or " + COPY_KEY.getTranslatedKeyMessage().getString(), x, y + 32,
                    0xFFFFFF, true);
        }
    }

    private static String text(@Nullable BlockPos pos) {
        return pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // ------------------------------------------------------------------ the selection in the world

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = wand(player);
        if (stack == null) return;
        WandSelection sel = WandSelection.read(stack);
        if (!sel.exportMode) return;

        PoseStack pose = event.getPoseStack();
        // the other structures of the machine, fainter and in blue
        int own = sel.currentIndex();
        var all = sel.structures();
        for (int i = 0; i < all.size(); i++) {
            var s = all.get(i);
            if (i == own || s.corner1 == null || s.corner2 == null) continue;
            BlockPos lo = new BlockPos(Math.min(s.corner1.getX(), s.corner2.getX()),
                    Math.min(s.corner1.getY(), s.corner2.getY()), Math.min(s.corner1.getZ(), s.corner2.getZ()));
            BlockPos hi = new BlockPos(Math.max(s.corner1.getX(), s.corner2.getX()),
                    Math.max(s.corner1.getY(), s.corner2.getY()), Math.max(s.corner1.getZ(), s.corner2.getZ()));
            box(pose, lo, hi.offset(1, 1, 1), 0.3f, 0.6f, 1f);
            if (s.controller != null) box(pose, s.controller, s.controller.offset(1, 1, 1), 1f, 0.5f, 0f);
        }
        if (sel.hasBothCorners()) {
            box(pose, sel.low(), sel.high().offset(1, 1, 1), 0f, 1f, 0f);
        } else if (sel.corner1 != null) {
            box(pose, sel.corner1, sel.corner1.offset(1, 1, 1), 0f, 1f, 0f);
        } else if (sel.corner2 != null) {
            box(pose, sel.corner2, sel.corner2.offset(1, 1, 1), 0f, 1f, 0f);
        }
        if (sel.controller != null) box(pose, sel.controller, sel.controller.offset(1, 1, 1), 1f, 0.5f, 0f);
    }

    /** A box as it is seen normally and, fainter, through the blocks in front of it. */
    private static void box(PoseStack pose, BlockPos min, BlockPos maxExclusive, float r, float g, float b) {
        draw(pose, min, maxExclusive, r, g, b, 0.3f, true);
        draw(pose, min, maxExclusive, r, g, b, 1.0f, false);
    }

    private static void draw(PoseStack pose, BlockPos min, BlockPos max, float r, float g, float b, float a,
                             boolean throughWalls) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())
                .move(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        if (throughWalls) RenderSystem.disableDepthTest();
        VertexConsumer buffer = source.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, buffer, box, r, g, b, a);
        source.endBatch(RenderType.lines());
        if (throughWalls) RenderSystem.enableDepthTest();
    }
}
