package net.phoenixvine.blueprint.common.export;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.blueprint.common.ShapeActions;

/** What the blueprint wand does on the server in export mode: marks the selection, copies it as KubeJS, changes how it is written. */
public final class ShapeExportActions {

    public static final int TOGGLE_MODE = 0;
    public static final int TOGGLE_CONTROLLER = 1;
    public static final int CLEAR = 2;
    public static final int COPY = 3;
    public static final int SELECT_BUILD = 4;
    public static final int CYCLE_CASE = 5;
    public static final int CYCLE_AIR = 6;
    public static final int TOGGLE_PARTS = 7;
    public static final int TOGGLE_STYLE = 8;
    public static final int SET_BUILD = 9;
    public static final int SET_EXPORT = 10;
    public static final int TOGGLE_LANGUAGE = 11;
    public static final int TOGGLE_VERSION = 12;
    public static final int ADD_STRUCTURE = 13;
    public static final int REMOVE_STRUCTURE = 14;
    public static final int TOGGLE_MACHINE_STYLE = 15;
    public static final int CYCLE_ROTATION = 16;
    public static final int CLEAR_TYPES = 17;
    public static final int CLEAR_MODIFIERS = 18;
    public static final int AUTO_SELECT = 19;
    /** 100 + the index of the structure to edit. */
    public static final int SELECT_STRUCTURE = 100;

    private ShapeExportActions() {}

    private static void say(@Nullable ServerPlayer player, String text, ChatFormatting colour) {
        if (player == null) return;
        player.displayClientMessage(Component.literal(text).withStyle(colour), true);
    }

    public static void apply(@Nullable ServerPlayer player, ItemStack stack, int action) {
        if (player != null && !ShapeActions.mayUse(player)) return;
        WandSelection sel = WandSelection.read(stack);
        KubeJsExport.Options o = sel.options;
        if (action >= SELECT_STRUCTURE) {
            sel.select(action - SELECT_STRUCTURE);
            sel.write(stack);
            return;
        }
        switch (action) {
            case TOGGLE_MACHINE_STYLE -> sel.options = o.withStyle(o.style() == KubeJsExport.STYLE_MACHINE ?
                    KubeJsExport.STYLE_FRAGMENT : KubeJsExport.STYLE_MACHINE);
            case CYCLE_ROTATION -> {
                MachineSettings m = MachineSettings.read(stack);
                m.rotation = (m.rotation + 1) % MachineSettings.ROTATIONS.length;
                m.write(stack);
            }
            case CLEAR_TYPES -> {
                MachineSettings m = MachineSettings.read(stack);
                m.recipeTypes.clear();
                m.write(stack);
            }
            case CLEAR_MODIFIERS -> {
                MachineSettings m = MachineSettings.read(stack);
                m.recipeModifiers.clear();
                m.write(stack);
            }
            case ADD_STRUCTURE -> {
                if (!sel.addStructure()) {
                    say(player, "A machine can have " + WandSelection.MAX_STRUCTURES + " structures at most.",
                            ChatFormatting.RED);
                } else {
                    say(player, "Substructure \"" + sel.name() + "\" added: mark its corners and controller.",
                            ChatFormatting.AQUA);
                }
            }
            case REMOVE_STRUCTURE -> {
                if (!sel.removeStructure()) say(player, "The main structure cannot be removed.", ChatFormatting.RED);
            }
            case TOGGLE_MODE -> {
                sel.exportMode = !sel.exportMode;
                say(player, sel.exportMode ? "Export mode: right-click blocks to select, right-click air to copy." :
                        "Build mode.", ChatFormatting.AQUA);
            }
            case SET_BUILD -> sel.exportMode = false;
            case SET_EXPORT -> sel.exportMode = true;
            case TOGGLE_CONTROLLER -> {
                sel.controllerMode = !sel.controllerMode;
                say(player, sel.controllerMode ? "Right-click a block to mark the controller (sneak to unmark it)." :
                        "Right-click a block to set corners again.", ChatFormatting.AQUA);
            }
            case CLEAR -> {
                sel.corner1 = null;
                sel.corner2 = null;
                sel.controller = null;
                sel.controllerMode = false;
                say(player, "Selection cleared.", ChatFormatting.GRAY);
            }
            case COPY -> {
                if (player != null) copy(player, stack, sel);
                return;
            }
            case AUTO_SELECT -> {
                if (player == null) return;
                autoSelect(player, sel);
            }
            case SELECT_BUILD -> {
                if (player == null) return;
                BlockPos[] bounds = ShapeActions.lastBuildBounds(player);
                if (bounds == null) {
                    say(player, "Nothing built yet to select.", ChatFormatting.RED);
                } else {
                    sel.corner1 = bounds[0];
                    sel.corner2 = bounds[1];
                    sel.controller = null;
                    say(player, "Selected the last build.", ChatFormatting.AQUA);
                }
            }
            case CYCLE_CASE -> sel.options = o.toggleCase();
            case CYCLE_AIR -> sel.options = o.nextAir();
            case TOGGLE_PARTS -> sel.options = o.toggleParts();
            case TOGGLE_STYLE -> sel.options = o.toggleStyle();
            case TOGGLE_LANGUAGE -> sel.options = o.toggleJava();
            case TOGGLE_VERSION -> sel.options = o.toggleVersion();
            default -> {
                return;
            }
        }
        sel.write(stack);
    }

    /** Natural terrain, which the selection from the controller does not spread through. */
    private static boolean isTerrain(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) ||
                state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER) || state.is(net.minecraft.tags.BlockTags.DIRT) ||
                state.is(net.minecraft.tags.BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL) ||
                state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS);
    }

    /** Most blocks, and farthest from the controller, the selection from the controller will look through. */
    private static final int AUTO_BLOCKS = 60_000;
    private static final int AUTO_RADIUS = 64;

    /**
     * Selects the box around everything built that touches the marked controller (blocks that are not air, joined face, edge or
     * corner to each other, not counting natural terrain), so a finished multiblock is selected in one click.
     */
    private static void autoSelect(ServerPlayer player, WandSelection sel) {
        if (sel.controller == null) {
            say(player, "Mark the controller first.", ChatFormatting.RED);
            return;
        }
        var level = player.level();
        BlockPos start = sel.controller;
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        queue.add(start);
        seen.add(start);
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        boolean cut = false;
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = at.offset(dx, dy, dz);
                        if (Math.abs(next.getX() - start.getX()) > AUTO_RADIUS ||
                                Math.abs(next.getY() - start.getY()) > AUTO_RADIUS ||
                                Math.abs(next.getZ() - start.getZ()) > AUTO_RADIUS || seen.contains(next)) {
                            continue;
                        }
                        var state = level.getBlockState(next);
                        if (state.isAir() || isTerrain(state)) continue;
                        if (seen.size() >= AUTO_BLOCKS) {
                            cut = true;
                            continue;
                        }
                        seen.add(next);
                        queue.add(next);
                        minX = Math.min(minX, next.getX());
                        minY = Math.min(minY, next.getY());
                        minZ = Math.min(minZ, next.getZ());
                        maxX = Math.max(maxX, next.getX());
                        maxY = Math.max(maxY, next.getY());
                        maxZ = Math.max(maxZ, next.getZ());
                    }
                }
            }
        }
        sel.corner1 = new BlockPos(minX, minY, minZ);
        sel.corner2 = new BlockPos(maxX, maxY, maxZ);
        say(player, cut ? "Selected the first " + AUTO_BLOCKS + " connected blocks: it may be too big." :
                "Selected " + seen.size() + " connected blocks around the controller.",
                cut ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }

    /** A right click on a block in export mode: a corner (sneak: the first), or the controller in controller mode. */
    public static void click(ServerPlayer player, ItemStack stack, BlockPos pos, boolean sneaking) {
        if (!ShapeActions.mayUse(player)) {
            say(player, "The blueprint wand needs creative mode or operator permission.", ChatFormatting.RED);
            return;
        }
        WandSelection sel = WandSelection.read(stack);
        if (sel.controllerMode) {
            if (sneaking) {
                sel.controller = null;
                say(player, "Controller unmarked.", ChatFormatting.GRAY);
            } else {
                sel.controller = pos;
                say(player, "Controller marked!", ChatFormatting.GOLD);
            }
        } else if (sneaking) {
            sel.corner1 = pos;
            say(player, "Corner 1 set!", ChatFormatting.GREEN);
        } else {
            sel.corner2 = pos;
            say(player, "Corner 2 set!", ChatFormatting.GREEN);
        }
        sel.write(stack);
    }

    /** Scans the selection and sends the KubeJS to the player's clipboard. */
    public static void copy(ServerPlayer player, ItemStack stack, WandSelection sel) {
        if (!ShapeActions.mayUse(player)) return;
        var level = player.level();

        java.util.List<WandSelection.Structure> ready = new java.util.ArrayList<>();
        for (WandSelection.Structure s : sel.structures()) {
            if (s.corner1 != null && s.corner2 != null) ready.add(s);
        }
        if (ready.isEmpty()) {
            say(player, "Set both corners first.", ChatFormatting.RED);
            return;
        }
        boolean several = sel.structureCount() > 1;
        boolean machine = sel.options.style() == KubeJsExport.STYLE_MACHINE;
        BlockPos firstController = null;
        for (WandSelection.Structure s : ready) {
            if (s.controller != null) {
                firstController = s.controller;
                break;
            }
        }
        String controllerBlock = null;
        if (firstController != null) {
            var state = level.getBlockState(firstController);
            if (!state.isAir()) {
                controllerBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            }
        }
        // no appearance block picked: the block marked as the controller; no casing texture: the appearance block's
        MachineSettings settings = machine ? MachineSettings.read(stack).effective(controllerBlock) : null;
        java.util.List<String> patterns = new java.util.ArrayList<>();

        StringBuilder text = new StringBuilder();
        boolean turned = false;
        for (WandSelection.Structure s : ready) {
            BlockPos low = new BlockPos(Math.min(s.corner1.getX(), s.corner2.getX()),
                    Math.min(s.corner1.getY(), s.corner2.getY()), Math.min(s.corner1.getZ(), s.corner2.getZ()));
            BlockPos high = new BlockPos(Math.max(s.corner1.getX(), s.corner2.getX()),
                    Math.max(s.corner1.getY(), s.corner2.getY()), Math.max(s.corner1.getZ(), s.corner2.getZ()));
            BlockPos controller = s.controller;
            if (controller != null && (controller.getX() < low.getX() || controller.getX() > high.getX() ||
                    controller.getY() < low.getY() || controller.getY() > high.getY() ||
                    controller.getZ() < low.getZ() || controller.getZ() > high.getZ())) {
                say(player, "The controller marked for \"" + s.name + "\" is outside its selection.",
                        ChatFormatting.RED);
                return;
            }

            int[] frame = controller == null ? ControllerFrame.defaultFrame() : ControllerFrame.frame(level, controller);
            KubeJsExport.Grid grid = KubeJsExport.scan(level, s.corner1, s.corner2, frame, controller);
            if (grid == null) {
                say(player, "\"" + s.name + "\" is too big (" + KubeJsExport.MAX_VOLUME + " blocks at most).",
                        ChatFormatting.RED);
                return;
            }
            String part = KubeJsExport.format(grid, sel.options, BlockReferences::lookup, s.name, several,
                    machine && !settings.appearanceBlock.isEmpty() ? settings.appearanceBlock : null);
            if (part == null) {
                say(player, "Too many different blocks in \"" + s.name + "\" (the limit is " +
                        KubeJsExport.MAX_TYPES + ").", ChatFormatting.RED);
                return;
            }
            if (machine) patterns.add(part);
            if (text.length() > 0) text.append("\n");
            text.append(part);
            turned |= controller != null;
        }
        if (machine) {
            text.setLength(0);
            text.append(KubeJsExport.machine(patterns, sel.options, new KubeJsExport.MachineContext(settings,
                    BlockReferences::lookup, MachineRefs::recipeType, MachineRefs::modifier)));
        }
        if (text.length() > KubeJsExport.MAX_CHARS) {
            say(player, "The result is too big to send to you.", ChatFormatting.RED);
            return;
        }
        String fileName = (machine ? settings.id() : "pattern") + (sel.options.java() ? ".java" : ".js");
        PacketDistributor.sendToPlayer(player, new S2CShapeExportPacket(text.toString(), fileName));
        say(player, (ready.size() > 1 ? "Copied " + ready.size() + " structures" : "Copied blocks") +
                (turned ? ", turned to face the controller." : "."), ChatFormatting.AQUA);
    }
}
