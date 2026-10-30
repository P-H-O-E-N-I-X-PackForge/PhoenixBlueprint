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
import net.phoenixvine.blueprint.client.gui.BlueprintWandPanel;
import net.phoenixvine.blueprint.common.export.ShapeExportActions;
import net.phoenixvine.blueprint.common.export.WandSelection;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;

import java.util.List;

/**
 * Builds shapes (rings in any orientation, spheres, helices, knots, and combinations of them) out of the block held in the
 * other hand, with a hologram showing where it will go. Right-click a block to build at the spot you are aiming at, sneak and
 * right-click to open the editor, left-click to undo.
 */
public class BlueprintWandItem extends Item implements IUIHolder<PlayerInventoryGuiData<?>> {

    /** Blocks added to the player's reach while the wand is in a hand, so a shape can be placed from a distance. */
    public static final double EXTRA_REACH = 120.0;

    public BlueprintWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (WandSelection.read(context.getItemInHand()).exportMode) {
            ShapeExportActions.click(serverPlayer, context.getItemInHand(), context.getClickedPos(),
                    player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            PlayerInventoryUIFactory.INSTANCE.openFromHand(serverPlayer, context.getHand());
            return InteractionResult.SUCCESS;
        }
        ShapeActions.place(serverPlayer, context.getHand(), context.getClickedPos().relative(context.getClickedFace()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean export = WandSelection.read(stack).exportMode;
        if (!player.isShiftKeyDown() && !export) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // export mode: a right click on air copies the selection, sneaking opens the editor
            if (export && !player.isShiftKeyDown()) {
                ShapeExportActions.apply(serverPlayer, stack, ShapeExportActions.COPY);
            } else {
                PlayerInventoryUIFactory.INSTANCE.openFromHand(serverPlayer, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager syncManager, UISettings settings) {
        return BlueprintWandPanel.build(data, syncManager, settings);
    }

    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData<?> data, ModularPanel<?> mainPanel) {
        return new com.gregtechceu.gtceu.api.mui.GTGuiScreen(mainPanel);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Builds rings, spheres, helices, knots and more").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Block to build with: the other hand").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click: build  |  Left-click: undo").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak + right-click: edit the shape").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Export mode: select a built multiblock and copy it as KubeJS")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Long reach while held").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Creative mode or operators only").withStyle(ChatFormatting.DARK_GRAY));
    }
}
