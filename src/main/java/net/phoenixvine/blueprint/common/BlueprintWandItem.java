package net.phoenixvine.blueprint.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.phoenixvine.blueprint.client.ClientHooks;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import java.util.List;

/**
 * Builds shapes (rings in any orientation, spheres, helices, knots, and combinations of them) out of the block held in the
 * other hand, with a hologram showing where it will go, and exports finished multiblocks as GregTech KubeJS or Java.
 * Right-click a block to build at the spot you are aiming at, sneak and right-click to open the editor, left-click to undo.
 * The editor is a screen opened on the client; its changes reach the server as packets ({@link WandEdits}).
 */
public class BlueprintWandItem extends Item {

    /** Blocks added to the player's reach while the wand is in a hand, so a shape can be placed from a distance. */
    public static final double EXTRA_REACH = 120.0;

    public BlueprintWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean export = WandSelection.read(context.getItemInHand()).exportMode;

        if (context.getLevel().isClientSide) {
            // build mode: sneaking opens the editor (export mode uses it to set the first corner)
            if (!export && player.isShiftKeyDown()) ClientHooks.openEditor(context.getHand());
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (export) {
            ShapeExportActions.click(serverPlayer, context.getItemInHand(), context.getClickedPos(),
                    player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) return InteractionResult.SUCCESS;
        ShapeActions.place(serverPlayer, context.getHand(), context.getClickedPos().relative(context.getClickedFace()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean export = WandSelection.read(stack).exportMode;
        if (!player.isShiftKeyDown() && !export) return InteractionResultHolder.pass(stack);

        if (level.isClientSide) {
            if (player.isShiftKeyDown()) ClientHooks.openEditor(hand);
        } else if (player instanceof ServerPlayer serverPlayer && export && !player.isShiftKeyDown()) {
            // export mode: a right click on air copies the selection, sneaking opens the editor
            ShapeExportActions.apply(serverPlayer, stack, ShapeExportActions.COPY);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Builds rings, spheres, helices, knots and more").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Block to build with: the other hand").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click: build  |  Left-click: undo").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak + right-click: edit the shape").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Export mode: select a built multiblock and copy it as KubeJS or Java")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Long reach while held").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Creative mode or operators only").withStyle(ChatFormatting.DARK_GRAY));
    }
}
