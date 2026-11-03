package net.phoenixvine.blueprint.common.export;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Custom predicates an addon defines as static methods (such as {@code PhoenixFissionPredicates.fissionModerators()}) are asked
 * what blocks they accept, by calling the method and reading the candidate blocks the predicate shows in previews. The first
 * candidate stands for the symbol in the hologram. Only methods that take nothing are called, and only on classes with
 * "Predicate" in their name.
 *
 * <p>It is all done by reflection, because the predicate classes differ between GregTech's API lines and this must work with
 * both: the newer one has {@code MultiPredicate.expand()} giving predicates whose {@code getCandidates()} are block infos; the
 * older one has {@code TraceabilityPredicate} with lists {@code common} and {@code limited} of predicates whose public
 * {@code candidates} supplier gives block infos.
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
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, PredicateEval.class.getClassLoader());
                Method m = type.getMethod(method);
                if (!Modifier.isStatic(m.getModifiers())) continue;
                found = firstBlockOf(m.invoke(null));
                if (found != null) break;
            } catch (Throwable ignored) {
                // no such method here, or it needs something that is not set up
            }
        }
        CACHE.put(key, found);
        return found;
    }

    /** The first non-air candidate block of a predicate object of either API line, or null. */
    static @Nullable BlockState firstBlockOf(@Nullable Object predicate) throws Exception {
        if (predicate == null) return null;
        Class<?> type = predicate.getClass();

        // the newer line: expand() gives predicates, each with getCandidates() of block infos
        Method expand = publicMethod(type, "expand");
        if (expand != null) {
            for (Object base : (List<?>) expand.invoke(predicate)) {
                Method candidates = publicMethod(base.getClass(), "getCandidates");
                if (candidates == null) continue;
                for (Object info : (List<?>) candidates.invoke(base)) {
                    BlockState state = stateOf(info);
                    if (state != null) return state;
                }
            }
            return null;
        }

        // the older line: the predicates in the lists common and limited each have a supplier of block infos
        for (String list : new String[] { "common", "limited" }) {
            Object simples;
            try {
                simples = type.getField(list).get(predicate);
            } catch (NoSuchFieldException e) {
                continue;
            }
            for (Object simple : (List<?>) simples) {
                Object supplier = simple.getClass().getField("candidates").get(simple);
                if (!(supplier instanceof Supplier<?> candidates)) continue;
                Object infos = candidates.get();
                if (infos == null) continue;
                int count = java.lang.reflect.Array.getLength(infos);
                for (int i = 0; i < count; i++) {
                    BlockState state = stateOf(java.lang.reflect.Array.get(infos, i));
                    if (state != null) return state;
                }
            }
        }
        return null;
    }

    private static @Nullable BlockState stateOf(@Nullable Object blockInfo) throws Exception {
        if (blockInfo == null) return null;
        Method get = publicMethod(blockInfo.getClass(), "getBlockState");
        if (get == null) return null;
        Object state = get.invoke(blockInfo);
        return state instanceof BlockState s && !s.isAir() ? s : null;
    }

    private static @Nullable Method publicMethod(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | RuntimeException e) {
            return null;
        }
    }
}
