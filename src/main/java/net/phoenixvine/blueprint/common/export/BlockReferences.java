package net.phoenixvine.blueprint.common.export;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Finds the field a block is held in ({@code PhoenixBlocks.PHOENIX_HEART_CASING}, {@code GTBlocks.CASING_STEEL_SOLID},
 * {@code Blocks.GLASS}), so exported Java can name a block the way the existing multiblocks do: {@code PhoenixBlocks.X.get()}.
 *
 * Nothing is listed by hand. The block's namespace is a mod id; that mod's class scan data gives its classes, and the ones
 * called *Blocks* or *Casing(s)* are read for static fields that are a block or a supplier of one. Each mod is read the first time one of its
 * blocks is asked about, once the registries are filled, and then remembered.
 */
public final class BlockReferences {

    private static final Map<String, Map<String, KubeJsExport.BlockRef>> BY_NAMESPACE = new HashMap<>();

    private BlockReferences() {}

    public static synchronized @Nullable KubeJsExport.BlockRef lookup(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        return BY_NAMESPACE.computeIfAbsent(namespace, BlockReferences::read).get(id);
    }

    private static Map<String, KubeJsExport.BlockRef> read(String namespace) {
        Map<String, KubeJsExport.BlockRef> out = new HashMap<>();
        if (namespace.equals("minecraft")) {
            readClass(Blocks.class, out);
            return out;
        }
        var file = ModList.get().getModFileById(namespace);
        if (file == null || file.getFile().getScanResult() == null) return out;

        List<String> names = new ArrayList<>();
        for (ModFileScanData.ClassData data : file.getFile().getScanResult().getClasses()) {
            String name = data.clazz().getClassName();
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if ((simple.contains("Blocks") || simple.contains("Casing")) && !simple.contains("$") && !name.contains(".mixin.") &&
                    !name.contains(".client.")) {
                names.add(name);
            }
        }
        names.sort(null);
        for (String name : names) {
            try {
                readClass(Class.forName(name, false, BlockReferences.class.getClassLoader()), out);
            } catch (Throwable ignored) {
                // a class that cannot be loaded here (client only, missing dependency)
            }
        }
        return out;
    }

    private static void readClass(Class<?> holder, Map<String, KubeJsExport.BlockRef> out) {
        for (Field field : holder.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods) || !Supplier.class.isAssignableFrom(field.getType()) &&
                    !Block.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                // a registry entry is read with .get(); vanilla keeps the block itself in the field
                Object raw = field.get(null);
                boolean entry = raw instanceof Supplier<?>;
                Object value = entry ? ((Supplier<?>) raw).get() : raw;
                if (!(value instanceof Block block)) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                out.putIfAbsent(id, new KubeJsExport.BlockRef(holder.getSimpleName() + "." + field.getName() +
                        (entry ? ".get()" : ""), holder.getName()));
            } catch (Throwable ignored) {
                // a field that is not filled yet, or not a block
            }
        }
    }
}
