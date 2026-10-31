package net.phoenixvine.blueprint.common.export;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom predicates an addon defines as static methods (such as {@code PhoenixFissionPredicates.fissionModerators()}) are asked
 * what blocks they accept, by calling the method and reading the candidate blocks the predicate shows in previews. The first
 * candidate stands for the symbol in the hologram. Only methods that take nothing are called, and only on classes with
 * "Predicate" in their name.
 */
public final class PredicateEval {

    private static Map<String, List<String>> bySimpleName;
    private static final Map<String, @Nullable BlockState> CACHE = new HashMap<>();

    private PredicateEval() {}

    private static void index() {
        if (bySimpleName != null) return;
        bySimpleName = new HashMap<>();
        Set<IModFileInfo> seen = new HashSet<>();
        for (var mod : ModList.get().getMods()) {
            IModFileInfo info = mod.getOwningFile();
            if (info == null || !seen.add(info) || info.getFile().getScanResult() == null) continue;
            for (ModFileScanData.ClassData data : info.getFile().getScanResult().getClasses()) {
                String name = data.clazz().getClassName();
                String simple = name.substring(name.lastIndexOf('.') + 1);
                if (simple.contains("Predicate") && !simple.contains("$") && !name.contains(".mixin.") &&
                        !name.contains(".client.")) {
                    bySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(name);
                }
            }
        }
    }

    /** The first non-air block the predicate {@code className.method()} accepts; {@code className} may be null (statically imported). */
    public static synchronized @Nullable BlockState firstBlock(@Nullable String className, String method) {
        String key = className + "." + method;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        BlockState found = null;
        index();
        List<String> classes = new ArrayList<>();
        if (className != null) classes.addAll(bySimpleName.getOrDefault(className, List.of()));
        else bySimpleName.values().forEach(classes::addAll);
        outer:
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, PredicateEval.class.getClassLoader());
                Method m = type.getMethod(method);
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.invoke(null) instanceof MultiPredicate predicate) {
                    for (BasePredicate base : predicate.expand()) {
                        for (BlockInfo candidate : base.getCandidates()) {
                            if (candidate.nonAir()) {
                                found = candidate.getBlockState();
                                break outer;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // no such method here, or it needs something that is not set up
            }
        }
        CACHE.put(key, found);
        return found;
    }
}
