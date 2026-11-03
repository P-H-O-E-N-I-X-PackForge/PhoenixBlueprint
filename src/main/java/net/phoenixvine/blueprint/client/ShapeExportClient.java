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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.network.BlueprintNetwork;
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
@Mod.EventBusSubscriber(modid = Blueprint.MOD_ID, value = Dist.CLIENT)
public final class ShapeExportClient {

    private static final String CATEGORY = "key.categories.phoenix_blueprint";

    public static final KeyMapping MODE_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
    public static final KeyMapping CONTROLLER_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_controller",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    public static final KeyMapping COPY_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_copy",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);

    /** Locks what is selected in place: the current part in build mode (shift: every part), the import in export mode. */
    public static final KeyMapping LOCK_KEY = new KeyMapping("key.phoenix_blueprint.blueprint_wand_lock",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);

    private static final int MAX_CLIPBOARD = 32767;

    private ShapeExportClient() {}

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            renderHud(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 1.20.1 uses Phase.END instead of TickEvent.ClientTickEvent.Post
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        boolean holding = mc.player != null && wand(mc.player) != null;
        if (holding) updateDerivedCasing(mc);

        while (MODE_KEY.consumeClick()) {
            if (holding) {
                BlueprintNetwork.CHANNEL.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.TOGGLE_MODE));
            }
        }
        while (CONTROLLER_KEY.consumeClick()) {
            if (holding) {
                BlueprintNetwork.CHANNEL.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.TOGGLE_CONTROLLER));
            }
        }
        while (LOCK_KEY.consumeClick()) {
            if (holding) lockSelected(mc);
        }
        while (COPY_KEY.consumeClick()) {
            if (holding) {
                BlueprintNetwork.CHANNEL.sendToServer(new C2SBlueprintWandActionPacket(ShapeExportActions.COPY));
            }
        }
    }

    /** Whether Phantasia is installed, so the selection can be previewed in its 3D viewer. */
    public static boolean phantasiaAvailable() {
        return ModList.get().isLoaded("phantasia");
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
        WandEditor.machine(net.phoenixvine.blueprint.common.export.MachineSettings.DERIVED_CASING, sprite.toString());
    }

    private static void say(LocalPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    /**
     * The lock key. Export mode: locks the imported pattern where it is shown (or lets it follow the aim again). Build mode:
     * pins the current part at the aimed spot, or every part with shift held (pressing it on a pinned part unpins it).
     */
    private static void lockSelected(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack stack = wand(player);
        if (stack == null) return;
        if (WandSelection.read(stack).exportMode) {
            if (!ImportHologram.active()) {
                say(player, "Nothing imported to lock: IMPORT FROM CLIPBOARD first");
            } else if (ImportHologram.toggleLock() || ImportHologram.locked()) {
                say(player, ImportHologram.locked() ? "Import locked in place" : "Import follows the aim");
            } else {
                say(player, "Aim the wand at a block first");
            }
            return;
        }
        var plan = net.phoenixvine.blueprint.common.ShapePlan.read(stack);
        boolean all = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        boolean pinned = all ? plan.parts.stream().allMatch(part -> part.locked) : plan.current().locked;
        BlockPos at = ShapeHologram.anchor();
        if (pinned) {
            WandEditor.unpin(all);
            say(player, all ? "Every part follows the aim" : "Part " + (plan.selected + 1) + " follows the aim");
        } else if (at != null) {
            WandEditor.pin(all, at, ShapeHologram.dimension());
            say(player, all ? "Every part pinned here" : "Part " + (plan.selected + 1) + " pinned here");
        } else {
            say(player, "Aim the wand at a block first");
        }
    }

    public static @Nullable ItemStack wand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BlueprintWandItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof BlueprintWandItem) return player.getOffhandItem();
        return null;
    }

    // ------------------------------------------------------------------ clipboard

    /** Copies the text; one too long for the clipboard is written to packet.txt and its path is copied instead. */
    public static void setClipboard(String text, String fileName) {
        Minecraft mc = Minecraft.getInstance();
        String saved = save(text, fileName);
        if (saved != null && mc.screen instanceof net.phoenixvine.blueprint.client.gui.BlueprintScreen editor) {
            editor.note("Saved to " + saved);
        }
        if (text.length() <= MAX_CLIPBOARD) {
            mc.keyboardHandler.setClipboard(text);
            return;
        }
        try {
            Path path = Path.of("packet.txt");
            Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            mc.keyboardHandler.setClipboard("Data saved to: " + path.toAbsolutePath());
            net.minecraft.client.gui.components.toasts.SystemToast.add(
                    mc.getToasts(),
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("Blueprint Wand"),
                    Component.literal("The selection was too big for the clipboard; its file path was copied instead")
            );
        } catch (Exception e) {
            mc.keyboardHandler.setClipboard(text.substring(0, MAX_CLIPBOARD));
        }
    }

    private static final int KEEP_EXPORTS = 50;

    /** Where every export is also written, newest last: blueprint/exports in the game folder. */
    public static Path exportsDir() {
        return FMLPaths.GAMEDIR.get().resolve("blueprint").resolve("exports");
    }

    /** Writes an export to the exports folder (keeping the newest few) and returns its path from the game folder, or null. */
    private static @Nullable String save(String text, String fileName) {
        try {
            Path dir = exportsDir();
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve(stamp + "-" + fileName.replaceAll("[^A-Za-z0-9_.-]", "_"));
            Files.writeString(file, text);
            try (var list = Files.list(dir)) {
                var old = list.sorted(java.util.Comparator.comparing(Path::getFileName).reversed())
                        .skip(KEEP_EXPORTS).toList();
                for (Path p : old) Files.deleteIfExists(p);
            }
            return "blueprint/exports/" + file.getFileName();
        } catch (Exception e) {
            return null;
        }
    }

    /** Opens the exports folder in the system's file browser. */
    public static void openExportsFolder() {
        try {
            Files.createDirectories(exportsDir());
            net.minecraft.Util.getPlatform().openFile(exportsDir().toFile());
        } catch (Exception ignored) {
            // no file browser to open
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

        // which way the pattern is read from each controller: slices red, rows (up) green, characters blue
        if (mc.level != null) {
            for (var s : all) {
                if (s.controller == null) continue;
                int[] f = net.phoenixvine.blueprint.common.export.ControllerFrame.frame(mc.level, s.controller);
                Vec3 c = Vec3.atCenterOf(s.controller);
                bar(pose, c, f[0], f[1], f[2], 1f, 0.25f, 0.25f);
                bar(pose, c, f[3], f[4], f[5], 0.25f, 1f, 0.25f);
                bar(pose, c, f[6], f[7], f[8], 0.35f, 0.55f, 1f);
            }
        }
    }

    /** A line from the middle of a block two and a half blocks along a direction. */
    private static void bar(PoseStack pose, Vec3 from, int dx, int dy, int dz, float r, float g, float b) {
        Vec3 to = from.add(dx * 2.5, dy * 2.5, dz * 2.5);
        AABB box = new AABB(Math.min(from.x, to.x) - 0.04, Math.min(from.y, to.y) - 0.04, Math.min(from.z, to.z) - 0.04,
                Math.max(from.x, to.x) + 0.04, Math.max(from.y, to.y) + 0.04, Math.max(from.z, to.z) + 0.04);
        drawBox(pose, box, r, g, b, 0.35f, true);
        drawBox(pose, box, r, g, b, 1f, false);
    }

    /** A box as it is seen normally and, fainter, through the blocks in front of it. */
    private static void box(PoseStack pose, BlockPos min, BlockPos maxExclusive, float r, float g, float b) {
        draw(pose, min, maxExclusive, r, g, b, 0.3f, true);
        draw(pose, min, maxExclusive, r, g, b, 1.0f, false);
    }

    private static void draw(PoseStack pose, BlockPos min, BlockPos max, float r, float g, float b, float a,
                             boolean throughWalls) {
        drawBox(pose, new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()), r, g, b, a,
                throughWalls);
    }

    private static void drawBox(PoseStack pose, AABB world, float r, float g, float b, float a, boolean throughWalls) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        AABB box = world.move(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        if (throughWalls) RenderSystem.disableDepthTest();
        VertexConsumer buffer = source.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, buffer, box, r, g, b, a);
        source.endBatch(RenderType.lines());
        if (throughWalls) RenderSystem.enableDepthTest();
    }
}