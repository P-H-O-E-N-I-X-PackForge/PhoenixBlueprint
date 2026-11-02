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

    /**
     * @param owners which part (its index in the plan) each cell came from; empty unless some part has a block of its own.
     *               A block added to join two others takes the part of a neighbour.
     * @param shiftY how far everything was moved up to sit on its base (0 when the plan does not sit on its base)
     */
    public record Result(LongSet cells, boolean truncated, it.unimi.dsi.fastutil.longs.Long2IntMap owners, int shiftY) {

        public Result(LongSet cells, boolean truncated) {
            this(cells, truncated, new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(), 0);
        }

        public Result(LongSet cells, boolean truncated, it.unimi.dsi.fastutil.longs.Long2IntMap owners) {
            this(cells, truncated, owners, 0);
        }
    }

    private ShapeGenerator() {}

    /** Cells of blocks in the world, from a plan whose parts may be pinned. */
    public record WorldResult(LongSet cells, boolean truncated, int skippedParts,
                              it.unimi.dsi.fastutil.longs.Long2IntMap owners,
                              it.unimi.dsi.fastutil.longs.Long2ByteMap orientation) {

        public WorldResult(LongSet cells, boolean truncated, int skippedParts) {
            this(cells, truncated, skippedParts, new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap());
        }

        public WorldResult(LongSet cells, boolean truncated, int skippedParts,
                           it.unimi.dsi.fastutil.longs.Long2IntMap owners) {
            this(cells, truncated, skippedParts, owners, noOrientation());
        }
    }

    /** An orientation map with nothing in it: every block is placed as it is. */
    public static it.unimi.dsi.fastutil.longs.Long2ByteMap noOrientation() {
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap none = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
        none.defaultReturnValue(BlockOrienter.NONE);
        return none;
    }

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
        return pinnedParts(plan, dimension, plan.orient != ShapePlan.ORIENT_OFF);
    }

    private static WorldResult pinnedParts(ShapePlan plan, String dimension, boolean withOrientation) {
        LongOpenHashSet out = new LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap owners = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        owners.defaultReturnValue(-1);
        boolean truncated = false;
        int skipped = 0;
        boolean own = plan.anyBlock();
        // a part repeated or mirrored is several instances, each its own shape
        List<ShapeSpec> instances = plan.instances();
        List<String> blockIds = plan.blockIds();
        for (int i = 0; i < instances.size(); i++) {
            ShapeSpec spec = instances.get(i);
            if (!spec.locked) continue;
            if (!spec.pinDim.equals(dimension)) {
                skipped++;
                continue;
            }
            Result part = relativeOf(plan, List.of(spec), false, false);
            truncated |= part.truncated();
            addTranslated(out, part.cells(), spec.pinX, spec.pinY, spec.pinZ);
            if (own) {
                LongIterator cells = part.cells().iterator();
                while (cells.hasNext()) {
                    long cell = cells.nextLong();
                    owners.put(BlockPos.asLong(BlockPos.getX(cell) + spec.pinX, BlockPos.getY(cell) + spec.pinY,
                            BlockPos.getZ(cell) + spec.pinZ), blockIds.indexOf(spec.block));
                }
            }
            if (out.size() > MAX_CELLS) truncated = true;
        }
        if (plan.connected && !truncated) connectDiagonals(out);
        if (own) inheritOwners(out, owners);
        it.unimi.dsi.fastutil.longs.Long2ByteMap orientation = withOrientation && !truncated ?
                ShapeOrientation.compute(plan, s -> s.locked, BlockPos.ZERO, 0, dimension, out) : noOrientation();
        return new WorldResult(out, truncated, skipped, owners, orientation);
    }

    /** The whole plan in the world: the free parts at the anchor, the pinned ones where they are pinned. */
    public static WorldResult world(ShapePlan plan, BlockPos anchor, String dimension) {
        LongOpenHashSet out = new LongOpenHashSet();
        Result free = relative(plan, s -> !s.locked, plan.sitOnBase, false);
        addTranslated(out, free.cells(), anchor.getX(), anchor.getY(), anchor.getZ());
        boolean truncated = free.truncated();

        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap owners = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        owners.defaultReturnValue(-1);
        for (var e : free.owners().long2IntEntrySet()) {
            long cell = e.getLongKey();
            owners.put(BlockPos.asLong(BlockPos.getX(cell) + anchor.getX(), BlockPos.getY(cell) + anchor.getY(),
                    BlockPos.getZ(cell) + anchor.getZ()), e.getIntValue());
        }

        WorldResult pinned = pinnedParts(plan, dimension, false);
        // the pinned parts were joined among themselves; join everything once more below
        out.addAll(pinned.cells());
        owners.putAll(pinned.owners());
        truncated |= pinned.truncated() || out.size() > MAX_CELLS;
        if (plan.connected && !truncated) connectDiagonals(out);
        if (plan.anyBlock()) inheritOwners(out, owners);
        it.unimi.dsi.fastutil.longs.Long2ByteMap orientation = plan.orient != ShapePlan.ORIENT_OFF && !truncated ?
                ShapeOrientation.compute(plan, s -> true, anchor, free.shiftY(), dimension, out) : noOrientation();
        return new WorldResult(out, truncated, pinned.skippedParts(), owners, orientation);
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
        List<ShapeSpec> specs = new ArrayList<>();
        for (ShapeSpec spec : plan.instances()) {
            if (include.test(spec)) specs.add(spec);
        }
        return relativeOf(plan, specs, sitOnBase, connect);
    }

    private static Result relativeOf(ShapePlan plan, List<ShapeSpec> specs, boolean sitOnBase, boolean connect) {
        LongOpenHashSet out = new LongOpenHashSet();
        boolean truncated = false;
        for (ShapeSpec spec : specs) {
            addSpec(out, spec);
            if (out.size() > MAX_CELLS) {
                truncated = true;
                break;
            }
        }

        if (connect && !truncated) connectDiagonals(out);

        // which part each block came from, when the parts are built from different blocks
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap owners = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        owners.defaultReturnValue(-1);
        if (plan.anyBlock() && !truncated) {
            List<String> blockIds = plan.blockIds();
            for (ShapeSpec spec : specs) {
                LongOpenHashSet own = new LongOpenHashSet();
                addSpec(own, spec);
                int owner = blockIds.indexOf(spec.block);
                LongIterator cells = own.iterator();
                while (cells.hasNext()) owners.put(cells.nextLong(), owner);
            }
            inheritOwners(out, owners);
        }

        int shiftY = 0;
        if (sitOnBase && !out.isEmpty()) {
            int minY = Integer.MAX_VALUE;
            LongIterator it = out.iterator();
            while (it.hasNext()) minY = Math.min(minY, BlockPos.getY(it.nextLong()));
            shiftY = -minY;
            LongOpenHashSet shifted = new LongOpenHashSet(out.size());
            it = out.iterator();
            while (it.hasNext()) {
                long cell = it.nextLong();
                shifted.add(BlockPos.asLong(BlockPos.getX(cell), BlockPos.getY(cell) - minY, BlockPos.getZ(cell)));
            }
            out = shifted;
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap moved = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            moved.defaultReturnValue(-1);
            for (var e : owners.long2IntEntrySet()) {
                long cell = e.getLongKey();
                moved.put(BlockPos.asLong(BlockPos.getX(cell), BlockPos.getY(cell) - minY, BlockPos.getZ(cell)),
                        e.getIntValue());
            }
            owners = moved;
        }
        return new Result(out, truncated, owners, shiftY);
    }

    /**
     * Joins blocks that only touch along an edge or at a corner by adding one between them, so a thin ring is a chain of
     * blocks that share faces (the way things connect in Minecraft) instead of a line of corners.
     */
    /** Cells with no owner (the ones added to join others) take the owner of a block they touch. */
    private static void inheritOwners(LongSet cells, it.unimi.dsi.fastutil.longs.Long2IntMap owners) {
        if (owners.isEmpty()) return;
        int[][] faces = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int pass = 0; pass < 3; pass++) {
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap found = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            LongIterator it = cells.iterator();
            while (it.hasNext()) {
                long cell = it.nextLong();
                if (owners.containsKey(cell)) continue;
                for (int[] f : faces) {
                    long next = BlockPos.asLong(BlockPos.getX(cell) + f[0], BlockPos.getY(cell) + f[1],
                            BlockPos.getZ(cell) + f[2]);
                    if (owners.containsKey(next)) {
                        found.put(cell, owners.get(next));
                        break;
                    }
                }
            }
            if (found.isEmpty()) return;
            owners.putAll(found);
        }
    }

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
                    if (spec.hollow) {
                        // an open shell is only the wall of the sides: the ends (or base) are left out
                        double wall = spec.open ? lateralDistance(spec, p.x, p.y, p.z) : Double.NaN;
                        if ((Double.isNaN(wall) ? sdf : wall) <= 0.5 - spec.wall) continue;
                    }
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
        int d = s.size[3];
        return switch (s.kind) {
            case SPHERE, DOME -> a;
            case ELLIPSOID, BOX, BOX_FRAME -> Math.sqrt((double) a * a + (double) b * b + (double) c * c);
            case WEDGE, GABLE_ROOF, HIP_ROOF -> Math.sqrt((double) a * a + b * b / 4.0 + c * c / 4.0);
            case STAIRS -> Math.sqrt((double) a * a + Math.pow(b * d / 2.0, 2) + Math.pow(b * c / 2.0, 2));
            case ZIGGURAT -> Math.hypot(a * 1.5, b * c / 2.0);
            case PARABOLOID, OCTAHEDRON -> Math.hypot(a, b / 2.0);
            case STAR -> a;
            case TWIST_TOWER -> Math.hypot(a, c / 2.0);
            case NOISE_BLOB -> a + b + 1.0;
            case CYLINDER, CONE, CAPSULE -> Math.hypot(a, b / 2.0) + (s.kind == ShapeKind.CAPSULE ? a : 0);
            case PYRAMID -> Math.hypot(a * 1.5, b / 2.0);
            case FRUSTUM, HYPERBOLOID -> Math.hypot(Math.max(a, b), c / 2.0);
            case PRISM -> Math.hypot(a, c / 2.0);
            case DISC, HALF_DISC -> Math.hypot(a, c / 2.0);
            default -> a;
        };
    }

    /** How far a point is from the surface of a solid shape: negative inside, positive outside (blocks, roughly). */
    static double signedDistance(ShapeSpec s, double x, double y, double z) {
        int a = s.size[0];
        int b = s.size[1];
        int c = s.size[2];
        int d = s.size[3];
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
            case FRUSTUM: {
                double half = (c - 1) / 2.0;
                double t = c > 1 ? (y + half) / (c - 1) : 0.0;
                double radius = a + (b - a) * Math.max(0.0, Math.min(1.0, t));
                return Math.max(Math.hypot(x, z) - radius, Math.abs(y) - half);
            }
            case HYPERBOLOID: {
                // a cooling tower: the radius is a at the waist and b at both ends, along a hyperbola
                double half = (c - 1) / 2.0;
                double u = half > 0 ? Math.max(-1.0, Math.min(1.0, y / half)) : 0.0;
                double radius = Math.sqrt(a * a + ((double) b * b - (double) a * a) * u * u);
                return Math.max(Math.hypot(x, z) - radius, Math.abs(y) - half);
            }
            case PRISM: {
                // a regular polygon with b sides and corner radius a, found by folding the point into its own side
                double sector = Math.PI * 2 / b;
                double angle = Math.atan2(z, x);
                if (angle < 0) angle += Math.PI * 2;
                double facing = (Math.floor(angle / sector) + 0.5) * sector;
                double apothem = a * Math.cos(Math.PI / b);
                double edge = x * Math.cos(facing) + z * Math.sin(facing) - apothem;
                return Math.max(edge, Math.abs(y) - (c - 1) / 2.0);
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
            case BOX_FRAME: {
                // how far inside each face the point is; it is on the frame where it is near two faces at once
                double ix = a - Math.abs(x);
                double iy = b - Math.abs(y);
                double iz = c - Math.abs(z);
                double nearest = Math.min(ix, Math.min(iy, iz));
                double middle = Math.max(Math.min(ix, iy), Math.min(Math.max(ix, iy), iz));
                return Math.max(-nearest, middle - (d - 1));
            }
            case WEDGE: {
                // a ramp rising along +z
                double halfH = (b - 1) / 2.0;
                double halfL = (c - 1) / 2.0;
                double slope = c > 1 ? (b - 1) / (double) (c - 1) : 0.0;
                double above = ((y + halfH) - slope * (z + halfL)) / Math.sqrt(1 + slope * slope);
                return Math.max(Math.max(Math.abs(x) - a, Math.abs(y) - halfH), Math.max(Math.abs(z) - halfL, above));
            }
            case STAIRS: {
                // d blocks deep and c high a step, b steps, rising along +z
                double halfL = (b * d - 1) / 2.0;
                double halfH = (b * c - 1) / 2.0;
                int step = (int) Math.max(0, Math.min(b - 1, Math.floor((z + halfL + 0.5) / d)));
                double top = -halfH + (step + 1) * c - 1;
                return Math.max(Math.max(Math.abs(x) - a, Math.abs(z) - halfL), Math.max(y - top, -halfH - y));
            }
            case ZIGGURAT: {
                // b square tiers, each d blocks narrower on every side than the one below
                double halfH = (b * c - 1) / 2.0;
                int level = (int) Math.max(0, Math.min(b - 1, Math.floor((y + halfH + 0.5) / c)));
                double size = a - (double) level * d;
                return Math.max(Math.max(Math.abs(x), Math.abs(z)) - size, Math.abs(y) - halfH);
            }
            case GABLE_ROOF: {
                // a ridge along z, sloping down to the eaves at x = +-a
                double halfH = (b - 1) / 2.0;
                double halfL = (c - 1) / 2.0;
                double slope = (b - 1) / (double) Math.max(1, a);
                double over = ((y + halfH) + slope * Math.abs(x) - (b - 1)) / Math.sqrt(1 + slope * slope);
                return Math.max(Math.max(Math.abs(y) - halfH, Math.abs(z) - halfL), Math.max(over, Math.abs(x) - a));
            }
            case HIP_ROOF: {
                // like the gable roof, sloping down on all four sides
                double halfH = (b - 1) / 2.0;
                double halfW = Math.max(1.0, a);
                double halfL = Math.max(1.0, (c - 1) / 2.0);
                double sx = (b - 1) / halfW;
                double sz = (b - 1) / halfL;
                double overX = ((y + halfH) + sx * Math.abs(x) - (b - 1)) / Math.sqrt(1 + sx * sx);
                double overZ = ((y + halfH) + sz * Math.abs(z) - (b - 1)) / Math.sqrt(1 + sz * sz);
                return Math.max(Math.abs(y) - halfH, Math.max(overX, overZ));
            }
            case PARABOLOID: {
                // a bowl opening upward: radius a at the top, b deep
                double half = (b - 1) / 2.0;
                double t = b > 1 ? Math.max(0.0, Math.min(1.0, (y + half) / (b - 1))) : 1.0;
                return Math.max(Math.hypot(x, z) - a * Math.sqrt(t), Math.abs(y) - half);
            }
            case OCTAHEDRON: {
                double halfH = Math.max(0.5, b / 2.0);
                double k = (Math.abs(x) + Math.abs(z)) / a + Math.abs(y) / halfH - 1.0;
                return k * Math.min(a, halfH) / 1.8;
            }
            case STAR: {
                // three spikes, one along each axis, each tapering from b blocks wide at the middle to a point a away
                double best = Double.MAX_VALUE;
                for (int axis = 0; axis < 3; axis++) {
                    double along = axis == 0 ? x : axis == 1 ? y : z;
                    double p1 = axis == 0 ? y : x;
                    double p2 = axis == 2 ? y : z;
                    double reach = b * (1.0 - Math.abs(along) / a);
                    best = Math.min(best, Math.max(Math.hypot(p1, p2) - reach, Math.abs(along) - a));
                }
                return best;
            }
            case TWIST_TOWER: {
                // a prism with b sides, turned d degrees from the bottom to the top
                double half = (c - 1) / 2.0;
                double turn = Math.toRadians(d) * (c > 1 ? (y + half) / (c - 1) : 0.0);
                double cos = Math.cos(turn);
                double sin = Math.sin(turn);
                double tx = x * cos + z * sin;
                double tz = -x * sin + z * cos;
                double sector = Math.PI * 2 / b;
                double angle = Math.atan2(tz, tx);
                if (angle < 0) angle += Math.PI * 2;
                double facing = (Math.floor(angle / sector) + 0.5) * sector;
                double edge = tx * Math.cos(facing) + tz * Math.sin(facing) - a * Math.cos(Math.PI / b);
                return Math.max(edge, Math.abs(y) - half);
            }
            case NOISE_BLOB:
                return Math.sqrt(x * x + y * y + z * z) - (a + b * noise(x / c, y / c, z / c, d));
            case HALF_DISC: {
                // a disc cut along its diameter: the half on the +z side, the cut edge on the x axis
                double ro = Math.hypot(x, z);
                return Math.max(Math.max(Math.max(ro - a, b - ro), -z), Math.abs(y) - (c - 1) / 2.0);
            }
            default:
                return Double.MAX_VALUE;
        }
    }

    /**
     * How far a point is outside the sides of a shape, leaving its ends out (the axial limits that close a pipe, a tower or a
     * dome). Used to make an open shell; NaN for a shape with no sides to speak of (a sphere, say).
     */
    static double lateralDistance(ShapeSpec s, double x, double y, double z) {
        int a = s.size[0];
        int b = s.size[1];
        int c = s.size[2];
        switch (s.kind) {
            case CYLINDER:
                return Math.hypot(x, z) - a;
            case CONE: {
                double half = (b - 1) / 2.0;
                double t = b > 1 ? (y + half) / (b - 1) : 0.0;
                return Math.hypot(x, z) - a * (1.0 - Math.max(0.0, Math.min(1.0, t)));
            }
            case PYRAMID: {
                double half = (b - 1) / 2.0;
                double t = b > 1 ? (y + half) / (b - 1) : 0.0;
                return Math.max(Math.abs(x), Math.abs(z)) - a * (1.0 - Math.max(0.0, Math.min(1.0, t)));
            }
            case FRUSTUM: {
                double half = (c - 1) / 2.0;
                double t = c > 1 ? (y + half) / (c - 1) : 0.0;
                return Math.hypot(x, z) - (a + (b - a) * Math.max(0.0, Math.min(1.0, t)));
            }
            case HYPERBOLOID: {
                double half = (c - 1) / 2.0;
                double u = half > 0 ? Math.max(-1.0, Math.min(1.0, y / half)) : 0.0;
                return Math.hypot(x, z) - Math.sqrt(a * a + ((double) b * b - (double) a * a) * u * u);
            }
            case PARABOLOID: {
                double half = (b - 1) / 2.0;
                double t = b > 1 ? Math.max(0.0, Math.min(1.0, (y + half) / (b - 1))) : 1.0;
                return Math.hypot(x, z) - a * Math.sqrt(t);
            }
            case PRISM:
            case TWIST_TOWER: {
                double tx = x;
                double tz = z;
                double sides = b;
                if (s.kind == ShapeKind.TWIST_TOWER) {
                    double half = (c - 1) / 2.0;
                    double turn = Math.toRadians(s.size[3]) * (c > 1 ? (y + half) / (c - 1) : 0.0);
                    tx = x * Math.cos(turn) + z * Math.sin(turn);
                    tz = -x * Math.sin(turn) + z * Math.cos(turn);
                }
                double sector = Math.PI * 2 / sides;
                double angle = Math.atan2(tz, tx);
                if (angle < 0) angle += Math.PI * 2;
                double facing = (Math.floor(angle / sector) + 0.5) * sector;
                return tx * Math.cos(facing) + tz * Math.sin(facing) - a * Math.cos(Math.PI / sides);
            }
            case BOX:
                return Math.max(Math.abs(x) - a, Math.abs(z) - c);
            case DOME:
                return Math.sqrt(x * x + y * y + z * z) - a;
            case ZIGGURAT: {
                double halfH = (b * c - 1) / 2.0;
                int level = (int) Math.max(0, Math.min(b - 1, Math.floor((y + halfH + 0.5) / c)));
                return Math.max(Math.abs(x), Math.abs(z)) - (a - (double) level * s.size[3]);
            }
            case WEDGE: {
                double halfH = (b - 1) / 2.0;
                double halfL = (c - 1) / 2.0;
                double slope = c > 1 ? (b - 1) / (double) (c - 1) : 0.0;
                return ((y + halfH) - slope * (z + halfL)) / Math.sqrt(1 + slope * slope);
            }
            case GABLE_ROOF: {
                double halfH = (b - 1) / 2.0;
                double slope = (b - 1) / (double) Math.max(1, a);
                return ((y + halfH) + slope * Math.abs(x) - (b - 1)) / Math.sqrt(1 + slope * slope);
            }
            case HIP_ROOF: {
                double halfH = (b - 1) / 2.0;
                double halfW = Math.max(1.0, a);
                double halfL = Math.max(1.0, (c - 1) / 2.0);
                double sx = (b - 1) / halfW;
                double sz = (b - 1) / halfL;
                return Math.max(((y + halfH) + sx * Math.abs(x) - (b - 1)) / Math.sqrt(1 + sx * sx),
                        ((y + halfH) + sz * Math.abs(z) - (b - 1)) / Math.sqrt(1 + sz * sz));
            }
            default:
                return Double.NaN;
        }
    }

    /** Smooth random values from -1 to 1, the same everywhere for one seed: two layers of value noise. */
    private static double noise(double x, double y, double z, int seed) {
        return 0.65 * valueNoise(x, y, z, seed) + 0.35 * valueNoise(x * 2.3 + 17.1, y * 2.3 + 5.7, z * 2.3 + 9.3, seed + 101);
    }

    private static double valueNoise(double x, double y, double z, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double x00 = lerp(lattice(ix, iy, iz, seed), lattice(ix + 1, iy, iz, seed), fx);
        double x10 = lerp(lattice(ix, iy + 1, iz, seed), lattice(ix + 1, iy + 1, iz, seed), fx);
        double x01 = lerp(lattice(ix, iy, iz + 1, seed), lattice(ix + 1, iy, iz + 1, seed), fx);
        double x11 = lerp(lattice(ix, iy + 1, iz + 1, seed), lattice(ix + 1, iy + 1, iz + 1, seed), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** A fixed pseudo-random value from -1 to 1 for a point of the grid. */
    private static double lattice(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 362437;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x7FFFFF - 1.0;
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
    static List<Vector3d> curvePoints(ShapeSpec s) {
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
            case ELLIPSE_RING:
                return sample(t -> new Vector3d(a * Math.cos(t * Math.PI * 2), 0, c * Math.sin(t * Math.PI * 2)), true);
            case ROUNDED_RECT_RING: {
                // a rectangle a by c (half-sizes) with its corners rounded to radius d
                double r = Math.min(d, Math.min(a, c));
                double[][] centres = { { a - r, c - r }, { -(a - r), c - r }, { -(a - r), -(c - r) }, { a - r, -(c - r) } };
                int steps = r <= 0 ? 0 : Math.max(2, (int) Math.ceil(r * Math.PI / 2 / 0.4));
                List<Vector3d> outline = new ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    if (steps == 0) {
                        outline.add(new Vector3d(centres[k][0], 0, centres[k][1]));
                        continue;
                    }
                    for (int j = 0; j <= steps; j++) {
                        double angle = Math.PI / 2 * (k + j / (double) steps);
                        outline.add(new Vector3d(centres[k][0] + r * Math.cos(angle), 0,
                                centres[k][1] + r * Math.sin(angle)));
                    }
                }
                return polyline(outline, true);
            }
            case GEAR_RING: {
                // c teeth, each d high: the top of a tooth is half the way round to the next
                List<Vector3d> outline = new ArrayList<>();
                for (int i = 0; i < c; i++) {
                    addArc(outline, a + d, Math.PI * 2 * (i + 0.25) / c, Math.PI * 2 * (i + 0.75) / c);
                    addArc(outline, a, Math.PI * 2 * (i + 0.75) / c, Math.PI * 2 * (i + 1.25) / c);
                }
                return polyline(outline, true);
            }
            case GOTHIC_ARCH: {
                // two arcs, each centred on the far side of the middle; sharpness 0 is a round arch, 100 an equilateral one
                double radius = a * (1 + c / 100.0);
                double shift = radius - a;
                double apex = Math.acos(Math.min(1.0, shift / radius));
                int steps = Math.max(4, (int) Math.ceil(radius * apex / 0.3));
                List<Vector3d> arc = new ArrayList<>();
                for (int i = 0; i <= steps; i++) {
                    double angle = apex * i / steps;
                    arc.add(new Vector3d(shift + radius * Math.cos(Math.PI - angle), radius * Math.sin(Math.PI - angle), 0));
                    arc.add(new Vector3d(-shift + radius * Math.cos(angle), radius * Math.sin(angle), 0));
                }
                if (d <= 1) return arc;
                List<Vector3d> all = new ArrayList<>(arc.size() * d);
                for (int k = 0; k < d; k++) {
                    double z = k - (d - 1) / 2.0;
                    for (Vector3d v : arc) all.add(new Vector3d(v.x, v.y, z));
                }
                return all;
            }
            case DOUBLE_HELIX: {
                List<Vector3d> all = new ArrayList<>();
                for (int strand = 0; strand < 2; strand++) {
                    double phase = strand * Math.PI;
                    all.addAll(sample(t -> new Vector3d(a * Math.cos(t * Math.PI * 2 * d + phase), c * (t - 0.5),
                            a * Math.sin(t * Math.PI * 2 * d + phase)), false));
                }
                return all;
            }
            case SEMICIRCLE: {
                // an arc of the circle, sweep degrees wide, centred on the +x side (a half circle at 180)
                double half = Math.toRadians(c) / 2.0;
                return sample(t -> {
                    double angle = (t * 2 - 1) * half;
                    return new Vector3d(a * Math.cos(angle), 0, a * Math.sin(angle));
                }, false);
            }
            case POLYGON_RING: {
                List<Vector3d> corners = new ArrayList<>();
                for (int i = 0; i < c; i++) {
                    double angle = Math.PI * 2 * i / c;
                    corners.add(new Vector3d(a * Math.cos(angle), 0, a * Math.sin(angle)));
                }
                return polyline(corners, true);
            }
            case LINE:
                // along x, sagging by c blocks in the middle (a straight line when c is 0)
                return sample(t -> new Vector3d(a * (t - 0.5), c * (1 - (2 * t - 1) * (2 * t - 1)), 0), false);
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

    /** Points along an arc of the circle of the given radius in the xz plane, about 0.4 apart. */
    private static void addArc(List<Vector3d> out, double radius, double from, double to) {
        int steps = Math.max(1, (int) Math.ceil(radius * Math.abs(to - from) / 0.4));
        for (int i = 0; i <= steps; i++) {
            double angle = from + (to - from) * i / steps;
            out.add(new Vector3d(radius * Math.cos(angle), 0, radius * Math.sin(angle)));
        }
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
