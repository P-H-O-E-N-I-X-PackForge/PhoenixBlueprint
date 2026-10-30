package net.phoenixvine.blueprint.common;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Turns a {@link ShapePlan} into the set of blocks it covers, as positions relative to the anchor ({@link BlockPos#asLong}).
 * <p>
 * Solid shapes are tested cell by cell: each cell centre is turned back into the shape's own space and asked how far inside
 * or outside the surface it is. Curve shapes are a line sampled finely, rotated, with every cell near a sample kept. Either
 * way the rotation is applied to continuous positions, so a shape turned to any angle has no holes in it.
 */
public final class ShapeGenerator {

    /** The most blocks a plan may cover; past this the result is cut short and flagged. */
    public static final int MAX_CELLS = 60_000;

    /** Cells further than this from the centre are never tested (keeps a huge shape from freezing the game). */
    private static final int MAX_EXTENT = 110;

    public record Result(LongSet cells, boolean truncated) {}

    private ShapeGenerator() {}

    /** Cells of blocks in the world, from a plan whose parts may be pinned. */
    public record WorldResult(LongSet cells, boolean truncated, int skippedParts) {}

    /** Every part of the plan, relative to the anchor, as if none were pinned. */
    public static Result generate(ShapePlan plan) {
        return relative(plan, s -> true, plan.sitOnBase, plan.connected);
    }

    /** The parts that are not pinned (the ones that follow the aim), relative to the anchor. */
    public static Result freeParts(ShapePlan plan) {
        return relative(plan, s -> !s.locked, plan.sitOnBase, plan.connected);
    }

    /** How far up the free parts are moved when the plan sits on its base: a pin made now must include it to stay put. */
    public static int baseShift(ShapePlan plan) {
        if (!plan.sitOnBase) return 0;
        Result free = relative(plan, s -> !s.locked, false, false);
        int minY = Integer.MAX_VALUE;
        LongIterator it = free.cells().iterator();
        while (it.hasNext()) minY = Math.min(minY, BlockPos.getY(it.nextLong()));
        return free.cells().isEmpty() ? 0 : -minY;
    }

    /** The pinned parts in the world (those pinned in another dimension are skipped and counted). */
    public static WorldResult pinnedParts(ShapePlan plan, String dimension) {
        LongOpenHashSet out = new LongOpenHashSet();
        boolean truncated = false;
        int skipped = 0;
        for (ShapeSpec spec : plan.parts) {
            if (!spec.locked) continue;
            if (!spec.pinDim.equals(dimension)) {
                skipped++;
                continue;
            }
            Result part = relative(plan, s -> s == spec, false, false);
            truncated |= part.truncated();
            addTranslated(out, part.cells(), spec.pinX, spec.pinY, spec.pinZ);
            if (out.size() > MAX_CELLS) truncated = true;
        }
        if (plan.connected && !truncated) connectDiagonals(out);
        return new WorldResult(out, truncated, skipped);
    }

    /** The whole plan in the world: the free parts at the anchor, the pinned ones where they are pinned. */
    public static WorldResult world(ShapePlan plan, BlockPos anchor, String dimension) {
        LongOpenHashSet out = new LongOpenHashSet();
        Result free = relative(plan, s -> !s.locked, plan.sitOnBase, false);
        addTranslated(out, free.cells(), anchor.getX(), anchor.getY(), anchor.getZ());
        boolean truncated = free.truncated();

        WorldResult pinned = pinnedParts(plan, dimension);
        // the pinned parts were joined among themselves; join everything once more below
        out.addAll(pinned.cells());
        truncated |= pinned.truncated() || out.size() > MAX_CELLS;
        if (plan.connected && !truncated) connectDiagonals(out);
        return new WorldResult(out, truncated, pinned.skippedParts());
    }

    private static void addTranslated(LongSet out, LongSet cells, int dx, int dy, int dz) {
        LongIterator it = cells.iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            out.add(BlockPos.asLong(BlockPos.getX(cell) + dx, BlockPos.getY(cell) + dy, BlockPos.getZ(cell) + dz));
        }
    }

    private static Result relative(ShapePlan plan, java.util.function.Predicate<ShapeSpec> include, boolean sitOnBase,
                                   boolean connect) {
        LongOpenHashSet out = new LongOpenHashSet();
        boolean truncated = false;
        for (ShapeSpec spec : plan.parts) {
            if (!include.test(spec)) continue;
            addSpec(out, spec);
            if (out.size() > MAX_CELLS) {
                truncated = true;
                break;
            }
        }

        if (connect && !truncated) connectDiagonals(out);

        if (sitOnBase && !out.isEmpty()) {
            int minY = Integer.MAX_VALUE;
            LongIterator it = out.iterator();
            while (it.hasNext()) minY = Math.min(minY, BlockPos.getY(it.nextLong()));
            LongOpenHashSet shifted = new LongOpenHashSet(out.size());
            it = out.iterator();
            while (it.hasNext()) {
                long cell = it.nextLong();
                shifted.add(BlockPos.asLong(BlockPos.getX(cell), BlockPos.getY(cell) - minY, BlockPos.getZ(cell)));
            }
            out = shifted;
        }
        return new Result(out, truncated);
    }

    /**
     * Joins blocks that only touch along an edge or at a corner by adding one between them, so a thin ring is a chain of
     * blocks that share faces (the way things connect in Minecraft) instead of a line of corners.
     */
    private static void connectDiagonals(LongSet cells) {
        for (int pass = 0; pass < 2; pass++) {
            LongOpenHashSet additions = new LongOpenHashSet();
            LongIterator it = cells.iterator();
            while (it.hasNext()) {
                long cell = it.nextLong();
                int x = BlockPos.getX(cell);
                int y = BlockPos.getY(cell);
                int z = BlockPos.getZ(cell);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                            if (axes < 2 || !cells.contains(BlockPos.asLong(x + dx, y + dy, z + dz))) continue;

                            long fx = BlockPos.asLong(x + dx, y, z);
                            long fy = BlockPos.asLong(x, y + dy, z);
                            long fz = BlockPos.asLong(x, y, z + dz);
                            boolean joined = (dx != 0 && (cells.contains(fx) || additions.contains(fx))) ||
                                    (dy != 0 && (cells.contains(fy) || additions.contains(fy))) ||
                                    (dz != 0 && (cells.contains(fz) || additions.contains(fz)));
                            if (axes == 3 && !joined) {
                                // a corner: any block on the way will do, the next pass joins the rest
                                joined = cells.contains(BlockPos.asLong(x + dx, y + dy, z)) ||
                                        cells.contains(BlockPos.asLong(x + dx, y, z + dz)) ||
                                        cells.contains(BlockPos.asLong(x, y + dy, z + dz));
                            }
                            if (joined) continue;
                            additions.add(dx != 0 ? fx : dy != 0 ? fy : fz);
                        }
                    }
                }
            }
            if (additions.isEmpty()) return;
            cells.addAll(additions);
        }
    }

    private static void addSpec(LongSet out, ShapeSpec spec) {
        Quaterniond rotation = new Quaterniond().rotationYXZ(Math.toRadians(spec.yaw), Math.toRadians(spec.pitch),
                Math.toRadians(spec.roll));
        if (spec.kind.isCurve()) addCurve(out, spec, rotation);
        else addSolid(out, spec, rotation);
    }

    // ------------------------------------------------------------------ solids

    private static void addSolid(LongSet out, ShapeSpec spec, Quaterniond rotation) {
        double bound = boundRadius(spec) + 1.5;
        int extent = (int) Math.min(MAX_EXTENT, Math.ceil(bound));
        double boundSq = bound * bound;
        Quaterniond inverse = new Quaterniond(rotation).conjugate();
        Vector3d p = new Vector3d();

        for (int i = -extent; i <= extent; i++) {
            for (int j = -extent; j <= extent; j++) {
                for (int k = -extent; k <= extent; k++) {
                    if (i * i + j * j + k * k > boundSq) continue;
                    p.set(i, j, k);
                    inverse.transform(p);
                    double sdf = signedDistance(spec, p.x, p.y, p.z);
                    if (sdf > 0.5) continue;
                    if (spec.hollow && sdf <= 0.5 - spec.wall) continue;
                    out.add(BlockPos.asLong(spec.dx + i, spec.dy + j, spec.dz + k));
                    if (out.size() > MAX_CELLS) return;
                }
            }
        }
    }

    private static double boundRadius(ShapeSpec s) {
        int a = s.size[0];
        int b = s.size[1];
        int c = s.size[2];
        return switch (s.kind) {
            case SPHERE, DOME -> a;
            case ELLIPSOID, BOX -> Math.sqrt((double) a * a + (double) b * b + (double) c * c);
            case CYLINDER, CONE, CAPSULE -> Math.hypot(a, b / 2.0) + (s.kind == ShapeKind.CAPSULE ? a : 0);
            case PYRAMID -> Math.hypot(a * 1.5, b / 2.0);
            case DISC -> Math.hypot(a, c / 2.0);
            default -> a;
        };
    }

    /** How far a point is from the surface of a solid shape: negative inside, positive outside (blocks, roughly). */
    private static double signedDistance(ShapeSpec s, double x, double y, double z) {
        int a = s.size[0];
        int b = s.size[1];
        int c = s.size[2];
        switch (s.kind) {
            case SPHERE:
                return Math.sqrt(x * x + y * y + z * z) - a;
            case ELLIPSOID: {
                double k = Math.sqrt((x / a) * (x / a) + (y / b) * (y / b) + (z / c) * (z / c));
                return (k - 1.0) * Math.min(a, Math.min(b, c));
            }
            case CYLINDER:
                return Math.max(Math.hypot(x, z) - a, Math.abs(y) - (b - 1) / 2.0);
            case CONE: {
                double half = (b - 1) / 2.0;
                double t = b > 1 ? (y + half) / (b - 1) : 0.0;
                double radius = a * (1.0 - Math.max(0.0, Math.min(1.0, t)));
                return Math.max(Math.hypot(x, z) - radius, Math.abs(y) - half);
            }
            case PYRAMID: {
                double half = (b - 1) / 2.0;
                double t = b > 1 ? (y + half) / (b - 1) : 0.0;
                double side = a * (1.0 - Math.max(0.0, Math.min(1.0, t)));
                return Math.max(Math.max(Math.abs(x), Math.abs(z)) - side, Math.abs(y) - half);
            }
            case DOME:
                return Math.max(Math.sqrt(x * x + y * y + z * z) - a, -y);
            case CAPSULE: {
                double half = (b - 1) / 2.0;
                double yc = Math.max(-half, Math.min(half, y));
                return Math.sqrt(x * x + (y - yc) * (y - yc) + z * z) - a;
            }
            case BOX:
                return Math.max(Math.abs(x) - a, Math.max(Math.abs(y) - b, Math.abs(z) - c));
            case DISC: {
                double ro = Math.hypot(x, z);
                return Math.max(Math.max(ro - a, b - ro), Math.abs(y) - (c - 1) / 2.0);
            }
            default:
                return Double.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------ curves

    private static void addCurve(LongSet out, ShapeSpec spec, Quaterniond rotation) {
        double tube = spec.size[1];
        double radius = tube / 2.0 + 0.2;
        boolean hollow = spec.hollow && radius > spec.wall + 0.5;
        int reach = (int) Math.ceil(radius);

        Long2DoubleMap nearest = new Long2DoubleOpenHashMap();
        nearest.defaultReturnValue(Double.MAX_VALUE);
        Vector3d world = new Vector3d();

        for (Vector3d point : curvePoints(spec)) {
            world.set(point);
            rotation.transform(world);
            world.add(spec.dx, spec.dy, spec.dz);

            int cx = (int) Math.floor(world.x);
            int cy = (int) Math.floor(world.y);
            int cz = (int) Math.floor(world.z);
            for (int i = cx - reach; i <= cx + reach + 1; i++) {
                for (int j = cy - reach; j <= cy + reach + 1; j++) {
                    for (int k = cz - reach; k <= cz + reach + 1; k++) {
                        double d = Math.sqrt((i - world.x) * (i - world.x) + (j - world.y) * (j - world.y) +
                                (k - world.z) * (k - world.z));
                        if (d > radius) continue;
                        long key = BlockPos.asLong(i, j, k);
                        if (d < nearest.get(key)) nearest.put(key, d);
                    }
                }
            }
            if (nearest.size() > MAX_CELLS * 2) break;
        }

        for (Long2DoubleMap.Entry entry : nearest.long2DoubleEntrySet()) {
            if (hollow && entry.getDoubleValue() <= radius - spec.wall) continue;
            out.add(entry.getLongKey());
            if (out.size() > MAX_CELLS) return;
        }
    }

    /** Points along the shape's line, in the shape's own space, about a third of a block apart. */
    private static List<Vector3d> curvePoints(ShapeSpec s) {
        double a = s.size[0];
        int c = s.size[2];
        int d = s.size[3];
        switch (s.kind) {
            case RING:
                return sample(t -> new Vector3d(a * Math.cos(t * Math.PI * 2), 0, a * Math.sin(t * Math.PI * 2)), true);
            case HELIX:
                return sample(t -> new Vector3d(a * Math.cos(t * Math.PI * 2 * d), c * (t - 0.5),
                        a * Math.sin(t * Math.PI * 2 * d)), false);
            case SPIRAL:
                return sample(t -> new Vector3d(a * t * Math.cos(t * Math.PI * 2 * c), 0,
                        a * t * Math.sin(t * Math.PI * 2 * c)), false);
            case KNOT: {
                double scale = a / 3.0;
                return sample(t -> {
                    double th = t * Math.PI * 2;
                    double r = 2 + Math.cos(c * th);
                    return new Vector3d(scale * r * Math.cos(2 * th), scale * Math.sin(c * th),
                            scale * r * Math.sin(2 * th));
                }, true);
            }
            case WAVE_RING:
                return sample(t -> {
                    double th = t * Math.PI * 2;
                    return new Vector3d(a * Math.cos(th), c * Math.sin(d * th), a * Math.sin(th));
                }, true);
            case STAR_RING: {
                int corners = d == 1 ? c * 2 : c;
                List<Vector3d> vertices = new ArrayList<>();
                for (int i = 0; i < corners; i++) {
                    double angle = Math.PI * 2 * i / corners;
                    double r = d == 1 && i % 2 == 1 ? a * 0.45 : a;
                    vertices.add(new Vector3d(r * Math.cos(angle), 0, r * Math.sin(angle)));
                }
                return polyline(vertices, true);
            }
            case LEMNISCATE:
                return sample(t -> {
                    double th = t * Math.PI * 2;
                    double den = 1 + Math.sin(th) * Math.sin(th);
                    return new Vector3d(a * Math.cos(th) / den, 0, a * Math.sin(th) * Math.cos(th) / den);
                }, true);
            case ARCH: {
                List<Vector3d> arc = sample(t -> new Vector3d(a * Math.cos(t * Math.PI), a * Math.sin(t * Math.PI), 0),
                        false);
                if (c <= 1) return arc;
                List<Vector3d> all = new ArrayList<>(arc.size() * c);
                for (int k = 0; k < c; k++) {
                    double z = k - (c - 1) / 2.0;
                    for (Vector3d v : arc) all.add(new Vector3d(v.x, v.y, z));
                }
                return all;
            }
            default:
                return List.of();
        }
    }

    private static List<Vector3d> sample(DoubleFunction<Vector3d> f, boolean closed) {
        double length = 0;
        Vector3d previous = f.apply(0);
        for (int i = 1; i <= 512; i++) {
            Vector3d next = f.apply(i / 512.0);
            length += previous.distance(next);
            previous = next;
        }
        int n = Math.max(8, (int) Math.ceil(length / 0.3));
        List<Vector3d> points = new ArrayList<>(n + 1);
        int count = closed ? n : n + 1;
        for (int i = 0; i < count; i++) points.add(f.apply(i / (double) n));
        return points;
    }

    private static List<Vector3d> polyline(List<Vector3d> vertices, boolean closed) {
        List<Vector3d> points = new ArrayList<>();
        int edges = closed ? vertices.size() : vertices.size() - 1;
        for (int e = 0; e < edges; e++) {
            Vector3d from = vertices.get(e);
            Vector3d to = vertices.get((e + 1) % vertices.size());
            int steps = Math.max(1, (int) Math.ceil(from.distance(to) / 0.3));
            for (int i = 0; i < steps; i++) points.add(from.lerp(to, i / (double) steps, new Vector3d()));
        }
        return points;
    }
}
