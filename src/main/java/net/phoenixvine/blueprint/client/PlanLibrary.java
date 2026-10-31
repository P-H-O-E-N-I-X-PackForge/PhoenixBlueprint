package net.phoenixvine.blueprint.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.neoforged.fml.loading.FMLPaths;
import net.phoenixvine.blueprint.common.ShapePlan;
import net.phoenixvine.blueprint.common.ShapeSpec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saved shape plans, kept as text files in {@code blueprint/plans} in the game folder (one {@code .snbt} file each, so they
 * can be copied between worlds and installs, kept in a repository or edited by hand), and a one-line text form for sharing a
 * plan in chat.
 * <p>
 * Only the shapes are saved: the parts, their sizes, rotation, offsets and blocks, and the plan's switches. Pins are left out
 * (a pin is a spot in one world) and so are the export settings (machine, limits), which belong to a machine and not a shape.
 */
public final class PlanLibrary {

    private static final String EXTENSION = ".snbt";
    private static final String SHARE_PREFIX = "BPLAN1:";
    /** Most a pasted plan may expand to, so a hostile string cannot be a bomb. */
    private static final long MAX_NBT_BYTES = 256 * 1024;
    private static final int MAX_NAME = 40;

    private PlanLibrary() {}

    /** One saved plan as the list shows it. */
    public record Entry(String name, int parts, String summary) {}

    public static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("blueprint").resolve("plans");
    }

    /** A name safe for a file: letters, digits, spaces, dashes and underscores, not too long; empty if nothing is left. */
    public static String cleanName(String raw) {
        String name = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "").replaceAll("\\s+", " ").trim();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME).trim() : name;
    }

    private static Path file(String name) {
        return dir().resolve(name + EXTENSION);
    }

    public static boolean exists(String name) {
        return !name.isEmpty() && Files.isRegularFile(file(name));
    }

    /** What is saved, by name (the order of the file names, ignoring case). */
    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        Path dir = dir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(EXTENSION)).sorted(
                    Comparator.comparing(f -> f.getFileName().toString().toLowerCase(Locale.ROOT))).forEach(f -> {
                        String file = f.getFileName().toString();
                        String name = file.substring(0, file.length() - EXTENSION.length());
                        try {
                            ShapePlan plan = ShapePlan.load(TagParser.parseTag(Files.readString(f)));
                            out.add(new Entry(name, plan.parts.size(), describe(plan)));
                        } catch (IOException | CommandSyntaxException | RuntimeException e) {
                            out.add(new Entry(name, 0, "unreadable"));
                        }
                    });
        } catch (IOException e) {
            // an unreadable folder is an empty library
        }
        return out;
    }

    /** "Ring, Sphere" for a plan, with a count past the first few. */
    private static String describe(ShapePlan plan) {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (ShapeSpec part : plan.parts) {
            if (shown == 3) {
                text.append(", +").append(plan.parts.size() - 3);
                break;
            }
            if (shown > 0) text.append(", ");
            text.append(part.kind.displayName());
            shown++;
        }
        return text.toString();
    }

    /** A copy of the plan as it is kept: shapes only, nothing pinned to a place. */
    private static CompoundTag portable(ShapePlan plan) {
        ShapePlan copy = ShapePlan.load(plan.save());
        for (ShapeSpec part : copy.parts) part.locked = false;
        return copy.save();
    }

    /** Saves a plan under a name, replacing a plan of that name. */
    public static void save(String name, ShapePlan plan) throws IOException {
        Files.createDirectories(dir());
        Files.writeString(file(name), portable(plan).toString());
    }

    public static ShapePlan load(String name) throws IOException, CommandSyntaxException {
        return ShapePlan.load(TagParser.parseTag(Files.readString(file(name))));
    }

    public static boolean delete(String name) throws IOException {
        return Files.deleteIfExists(file(name));
    }

    // ------------------------------------------------------------------ sharing

    /** The plan as one line of text that can be pasted anywhere: a prefix, then the compressed tag in base 64. */
    public static String toShareString(ShapePlan plan) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(portable(plan), bytes);
        return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    /** Reads a shared line (or a plan written out as text); throws when it is neither. */
    public static ShapePlan fromShareString(String text) throws IOException, CommandSyntaxException {
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) return ShapePlan.load(TagParser.parseTag(trimmed));
        if (!trimmed.startsWith(SHARE_PREFIX)) throw new IOException("not a blueprint plan");
        byte[] bytes = Base64.getUrlDecoder().decode(trimmed.substring(SHARE_PREFIX.length())
                .getBytes(StandardCharsets.US_ASCII));
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
        return ShapePlan.load(tag);
    }
}
