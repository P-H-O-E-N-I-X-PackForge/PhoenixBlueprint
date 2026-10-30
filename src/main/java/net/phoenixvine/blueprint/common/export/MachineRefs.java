package net.phoenixvine.blueprint.common.export;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The recipe modifiers and recipe types a machine can be given, found without a list kept by hand: recipe types are in the
 * recipe type registry; both are also looked up as static fields of classes called *RecipeModifiers* and *RecipeTypes* in every
 * mod, so exported Java can name them the way the existing code does ({@code GTRecipeModifiers.OC_PERFECT},
 * {@code PhoenixRecipeTypes.X}). A modifier is picked by an id made of its mod and field name ({@code gtceu:oc_perfect}).
 */
public final class MachineRefs {

    private static Map<String, KubeJsExport.BlockRef> modifiers;
    private static Map<String, KubeJsExport.BlockRef> types;

    private MachineRefs() {}

    /** Ids of every recipe modifier found, for a picker. */
    public static synchronized List<ResourceLocation> modifierIds() {
        ensure();
        List<ResourceLocation> out = new ArrayList<>();
        for (String id : modifiers.keySet()) out.add(ResourceLocation.parse(id));
        return out;
    }

    /** Ids of every registered GregTech recipe type, for a picker. */
    public static List<ResourceLocation> recipeTypeIds() {
        List<ResourceLocation> out = new ArrayList<>();
        for (var entry : BuiltInRegistries.RECIPE_TYPE.entrySet()) {
            if (entry.getValue() instanceof GTRecipeType) out.add(entry.getKey().location());
        }
        return out;
    }

    public static synchronized @Nullable KubeJsExport.BlockRef modifier(String id) {
        ensure();
        return modifiers.get(id);
    }

    public static synchronized @Nullable KubeJsExport.BlockRef recipeType(String id) {
        ensure();
        return types.get(id);
    }

    private static void ensure() {
        if (modifiers != null) return;
        modifiers = new LinkedHashMap<>();
        types = new LinkedHashMap<>();
        Set<IModFileInfo> seen = new HashSet<>();
        for (var mod : ModList.get().getMods()) {
            IModFileInfo info = mod.getOwningFile();
            if (info == null || !seen.add(info) || info.getFile().getScanResult() == null) continue;
            String modId = mod.getModId();
            for (ModFileScanData.ClassData data : info.getFile().getScanResult().getClasses()) {
                String name = data.clazz().getClassName();
                String simple = name.substring(name.lastIndexOf('.') + 1);
                if (simple.contains("$") || name.contains(".mixin.") || name.contains(".client.")) continue;
                boolean wantsModifiers = simple.contains("RecipeModifiers");
                boolean wantsTypes = simple.contains("RecipeTypes");
                if (!wantsModifiers && !wantsTypes) continue;
                try {
                    Class<?> holder = Class.forName(name, false, MachineRefs.class.getClassLoader());
                    for (Field field : holder.getDeclaredFields()) {
                        int mods = field.getModifiers();
                        if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)) continue;
                        Object raw = field.get(null);
                        Object value = raw instanceof Supplier<?> supplier ? supplier.get() : raw;
                        KubeJsExport.BlockRef ref = new KubeJsExport.BlockRef(holder.getSimpleName() + "." + field.getName(),
                                holder.getName());
                        if (wantsModifiers && value instanceof RecipeModifier) {
                            String id = modId + ":" + field.getName().toLowerCase(Locale.ROOT);
                            if (modifiers.containsKey(id)) {
                                id = modId + ":" + holder.getSimpleName().toLowerCase(Locale.ROOT) + "_" +
                                        field.getName().toLowerCase(Locale.ROOT);
                            }
                            modifiers.putIfAbsent(id, ref);
                        } else if (wantsTypes && value instanceof GTRecipeType type) {
                            types.putIfAbsent(type.getId().toString(), ref);
                        }
                    }
                } catch (Throwable ignored) {
                    // a class that cannot be loaded here, or a field that is not filled yet
                }
            }
        }
    }
}
