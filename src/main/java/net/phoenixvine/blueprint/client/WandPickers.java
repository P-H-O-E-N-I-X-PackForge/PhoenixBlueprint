package net.phoenixvine.blueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.blueprint.client.gui.RegistryPickerScreen;
import net.phoenixvine.blueprint.common.export.MachineRefs;
import net.phoenixvine.blueprint.common.export.MachineSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The lists the machine page picks from: every block, GregTech recipe type, recipe modifier and block texture. A pick goes to
 * the wand in a packet, which opens the editor again.
 */
public final class WandPickers {

    private WandPickers() {}

    public static void pickBlock(Screen parent, int field, String label) {
        open(parent, label, blockIds(), field);
    }

    /** Picks the block the selected part is built from. */
    public static void pickPartBlock(Screen parent) {
        Minecraft.getInstance().setScreen(new RegistryPickerScreen(parent, "Block for this part", blockIds(),
                id -> WandEditor.partBlock(id.toString())));
    }

    public static void pickTexture(Screen parent, int field, String label) {
        Minecraft.getInstance().setScreen(new WandTextureBrowser(parent, textureIds(),
                id -> WandEditor.machine(field, id.toString())));
    }

    public static void pickRecipeType(Screen parent) {
        open(parent, "Add a recipe type", MachineRefs.recipeTypeIds(), MachineSettings.ADD_TYPE);
    }

    public static void pickRecipeModifier(Screen parent) {
        open(parent, "Add a recipe modifier", MachineRefs.modifierIds(), MachineSettings.ADD_MODIFIER);
    }

    public static void typeName(Screen parent, int field, String label, String initial) {
        Minecraft.getInstance().setScreen(new WandTextScreen(parent, field, label, initial));
    }

    private static void open(Screen parent, String label, Collection<ResourceLocation> ids, int field) {
        Minecraft.getInstance().setScreen(new RegistryPickerScreen(parent, label, ids,
                id -> WandEditor.machine(field, id.toString())));
    }

    private static List<ResourceLocation> blockIds() {
        List<ResourceLocation> out = new ArrayList<>(BuiltInRegistries.BLOCK.keySet());
        out.remove(ResourceLocation.withDefaultNamespace("air"));
        return out;
    }

    /** The block textures of every loaded resource pack, as the ids a model uses ({@code gtceu:block/casings/...}). */
    private static List<ResourceLocation> textureIds() {
        List<ResourceLocation> out = new ArrayList<>();
        Minecraft.getInstance().getResourceManager().listResources("textures/block", id -> id.getPath().endsWith(".png"))
                .keySet().forEach(id -> {
                    String path = id.getPath();
                    out.add(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                            path.substring("textures/".length(), path.length() - ".png".length())));
                });
        return out;
    }
}
