package net.phoenixvine.blueprint.common.export;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.phoenixvine.blueprint.common.ShapeActions;
import net.phoenixvine.blueprint.common.BlueprintWandItem;

import com.mojang.brigadier.arguments.StringArgumentType;

/** {@code /blueprint name <name>}: names the structure being edited (a GTM 8 machine's substructure) in the held wand. */
@EventBusSubscriber
public final class BlueprintWandCommand {

    private BlueprintWandCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blueprint")
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> rename(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
    }

    private static int rename(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !ShapeActions.mayUse(player)) {
            source.sendFailure(Component.literal("Only a player with the blueprint wand can name a structure."));
            return 0;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlueprintWandItem)) continue;
            WandSelection sel = WandSelection.read(stack);
            if (sel.currentIndex() == 0) {
                source.sendFailure(Component.literal("The first structure is always called main."));
                return 0;
            }
            sel.rename(name);
            sel.write(stack);
            source.sendSuccess(() -> Component.literal("Structure named " + name + "."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Hold the blueprint wand."));
        return 0;
    }
}
