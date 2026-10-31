package net.phoenixvine.blueprint.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Works out, for every block of a built shape, which way a directional block should turn there (see {@link BlockOrienter}).
 * <p>
 * Each cell is matched to the part of the plan it is nearest to, and read against that part's own geometry:
 * <ul>
 * <li>a solid: the surface normal is the gradient of the shape's distance function, turned by the part's rotation; a
 * cylinder, cone, pyramid, capsule or disc lays logs along its axis, any other solid along the normal;</li>
 * <li>a curve (ring, helix, knot...): logs lie along the curve's direction; the normal points out of the tube, or, on a thin
 * tube whose blocks sit on its centre line, away from the shape's centre.</li>
 * </ul>
 * Cells are in whatever coordinates the caller built them in (relative to the anchor, or in the world); the anchor and the
 * pin of a part say where that part's own origin is in those coordinates.
 */
public final class ShapeOrientation {

    /** A surface counts as facing down (stairs and slabs go in the top half) when its normal points this far below level. */
    private static final double TOP_BELOW = -0.35;
    /** A cell this far from a curve's centre line is on the tube's surface, so the direction from the line is the normal. */
    private static final double ON_SURFACE = 0.75;

    private ShapeOrientation() {}

    /**
     * The packed orientation (see {@link BlockOrienter#pack}) of each cell; a cell no part could be matched to is left out.
     *
     * @param include  which parts to look at (a part pinned in another dimension is skipped whatever this says)
     * @param anchor   where the free parts sit, in the cells' coordinates
     * @param shiftY   how far the free parts were moved up to sit on their base ({@link ShapeGenerator.Result#shiftY})
     * @param dimension the dimension the cells are in, to tell which pinned parts count
     */
    public static Long2ByteMap compute(ShapePlan plan, Predicate<ShapeSpec> include, BlockPos anchor, int shiftY,
                                       String dimension, LongSet cells) {
        Long2ByteOpenHashMap out = new Long2ByteOpenHashMap(cells.size());
        out.defaultReturnValue(BlockOrienter.NONE);

        List<Frame> frames = new ArrayList<>();
        for (ShapeSpec spec : plan.instances()) {
            if (!include.test(spec)) continue;
            if (spec.locked && !spec.pinDim.equals(dimension)) continue;
            frames.add(new Frame(spec, anchor, shiftY));
        }
        if (frames.isEmpty()) return out;

        LongIterator it = cells.iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            double x = BlockPos.getX(cell);
            double y = BlockPos.getY(cell);
            double z = BlockPos.getZ(cell);
            Frame best = null;
            double bestScore = NO_MATCH;
            for (Frame frame : frames) {
                double score = frame.score(x, y, z);
                if (score < bestScore) {
                    bestScore = score;
                    best = frame;
                }
            }
            if (best != null) out.put(cell, best.orient(x, y, z));
        }
        return out;
    }

    private static final double NO_MATCH = 1e8;

    /** The direction of a vector, snapped to one of the six; the up-down axis wins a tie, then east-west. */
    private static Direction nearest(double x, double y, double z) {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        if (ay >= ax && ay >= az) return y < 0 ? Direction.DOWN : Direction.UP;
        if (ax >= az) return x < 0 ? Direction.WEST : Direction.EAST;
        return z < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static boolean closed(ShapeKind kind) {
        return switch (kind) {
            case RING, KNOT, WAVE_RING, STAR_RING, LEMNISCATE, POLYGON_RING -> true;
            default -> false;
        };
    }

    /** One part of the plan placed in the cells' coordinates, ready to be asked about a cell. */
    private static final class Frame {

        final ShapeSpec spec;
        /** Where the part's origin is in the cells' coordinates. */
        final double ox, oy, oz;
        final Quaterniond rotation;
        final Quaterniond inverse;
        final boolean curve;
        final boolean axisAlongShape;

        // a curve: its points (in the part's frame, turned, plus the part's offset) and the direction of the line at each
        double[] px, py, pz, tx, ty, tz;
        double radius;
        double bucket;
        Long2ObjectOpenHashMap<IntArrayList> grid;

        // what the last call to score found, for orient to use
        private int nearestPoint = -1;
        private double nearestDistance;

        Frame(ShapeSpec spec, BlockPos anchor, int shiftY) {
            this.spec = spec;
            if (spec.locked) {
                ox = spec.pinX;
                oy = spec.pinY;
                oz = spec.pinZ;
            } else {
                ox = anchor.getX();
                oy = anchor.getY() + shiftY;
                oz = anchor.getZ();
            }
            rotation = new Quaterniond().rotationYXZ(Math.toRadians(spec.yaw), Math.toRadians(spec.pitch),
                    Math.toRadians(spec.roll));
            inverse = new Quaterniond(rotation).conjugate();
            curve = spec.kind.isCurve();
            axisAlongShape = switch (spec.kind) {
                case CYLINDER, CONE, FRUSTUM, HYPERBOLOID, PYRAMID, PRISM, CAPSULE, DISC, HALF_DISC -> true;
                default -> false;
            };
            if (curve) buildCurve();
        }

        private void buildCurve() {
            List<Vector3d> points = ShapeGenerator.curvePoints(spec);
            int n = points.size();
            px = new double[n];
            py = new double[n];
            pz = new double[n];
            tx = new double[n];
            ty = new double[n];
            tz = new double[n];
            Vector3d v = new Vector3d();
            for (int i = 0; i < n; i++) {
                v.set(points.get(i));
                rotation.transform(v);
                px[i] = v.x + spec.dx;
                py[i] = v.y + spec.dy;
                pz[i] = v.z + spec.dz;
            }
            boolean loop = closed(spec.kind);
            for (int i = 0; i < n; i++) {
                int before = loop ? (i - 1 + n) % n : Math.max(0, i - 1);
                int after = loop ? (i + 1) % n : Math.min(n - 1, i + 1);
                double dx = px[after] - px[before];
                double dy = py[after] - py[before];
                double dz = pz[after] - pz[before];
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1e-9) {
                    tx[i] = 0;
                    ty[i] = 1;
                    tz[i] = 0;
                } else {
                    tx[i] = dx / length;
                    ty[i] = dy / length;
                    tz[i] = dz / length;
                }
            }

            // the same reach the shape was drawn with, so every block it drew has a point within it
            radius = spec.size[1] / 2.0 + 0.2;
            bucket = Math.max(2.0, Math.ceil(radius) + 1.0);
            grid = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < n; i++) {
                long cellKey = key(px[i], py[i], pz[i]);
                IntArrayList bucketList = grid.get(cellKey);
                if (bucketList == null) {
                    bucketList = new IntArrayList();
                    grid.put(cellKey, bucketList);
                }
                bucketList.add(i);
            }
        }

        private long key(double x, double y, double z) {
            return BlockPos.asLong((int) Math.floor(x / bucket), (int) Math.floor(y / bucket),
                    (int) Math.floor(z / bucket));
        }

        /** How far the cell is from this part: 0 inside it, growing outside; {@link #NO_MATCH} when it is nowhere near. */
        double score(double x, double y, double z) {
            double lx = x - ox - spec.dx;
            double ly = y - oy - spec.dy;
            double lz = z - oz - spec.dz;
            if (!curve) {
                Vector3d p = new Vector3d(lx, ly, lz);
                inverse.transform(p);
                double sdf = ShapeGenerator.signedDistance(spec, p.x, p.y, p.z);
                if (sdf == Double.MAX_VALUE) return NO_MATCH;
                double outside = sdf > 0.5 ? sdf - 0.5 : spec.hollow && sdf <= 0.5 - spec.wall ?
                        (0.5 - spec.wall) - sdf : 0.0;
                return outside + 0.001 * Math.abs(sdf);
            }

            double cx = x - ox;
            double cy = y - oy;
            double cz = z - oz;
            int bx = (int) Math.floor(cx / bucket);
            int by = (int) Math.floor(cy / bucket);
            int bz = (int) Math.floor(cz / bucket);
            nearestPoint = -1;
            double best = Double.MAX_VALUE;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = -1; k <= 1; k++) {
                        IntArrayList list = grid.get(BlockPos.asLong(bx + i, by + j, bz + k));
                        if (list == null) continue;
                        for (int n = 0; n < list.size(); n++) {
                            int idx = list.getInt(n);
                            double dx = cx - px[idx];
                            double dy = cy - py[idx];
                            double dz = cz - pz[idx];
                            double d = dx * dx + dy * dy + dz * dz;
                            if (d < best) {
                                best = d;
                                nearestPoint = idx;
                            }
                        }
                    }
                }
            }
            if (nearestPoint < 0) return NO_MATCH;
            nearestDistance = Math.sqrt(best);
            return Math.max(0.0, nearestDistance - radius) + 0.001 * nearestDistance;
        }

        /** The packed orientation of a cell this part was found nearest to. */
        byte orient(double x, double y, double z) {
            double nx, ny, nz;
            Direction.Axis axis;
            if (!curve) {
                double lx = x - ox - spec.dx;
                double ly = y - oy - spec.dy;
                double lz = z - oz - spec.dz;
                Vector3d p = new Vector3d(lx, ly, lz);
                inverse.transform(p);
                double h = 0.5;
                Vector3d g = new Vector3d(
                        ShapeGenerator.signedDistance(spec, p.x + h, p.y, p.z) -
                                ShapeGenerator.signedDistance(spec, p.x - h, p.y, p.z),
                        ShapeGenerator.signedDistance(spec, p.x, p.y + h, p.z) -
                                ShapeGenerator.signedDistance(spec, p.x, p.y - h, p.z),
                        ShapeGenerator.signedDistance(spec, p.x, p.y, p.z + h) -
                                ShapeGenerator.signedDistance(spec, p.x, p.y, p.z - h));
                if (g.lengthSquared() < 1e-9) {
                    // the exact middle of a shape: no surface here, so point away from the centre, or up
                    g.set(p);
                    if (g.lengthSquared() < 1e-9) g.set(0, 1, 0);
                }
                rotation.transform(g);
                nx = g.x;
                ny = g.y;
                nz = g.z;
                Direction normal = nearest(nx, ny, nz);
                if (axisAlongShape) {
                    Vector3d up = rotation.transform(new Vector3d(0, 1, 0));
                    axis = nearest(up.x, up.y, up.z).getAxis();
                } else {
                    axis = normal.getAxis();
                }
                return BlockOrienter.pack(normal, axis, below(ny, nx, nz));
            }

            // a curve: score() has just found the nearest point of the line, but another cell may have been asked since
            score(x, y, z);
            int i = Math.max(0, nearestPoint);
            double cx = x - ox;
            double cy = y - oy;
            double cz = z - oz;
            double vx = cx - px[i];
            double vy = cy - py[i];
            double vz = cz - pz[i];
            if (vx * vx + vy * vy + vz * vz < ON_SURFACE * ON_SURFACE) {
                // on the centre line of a thin tube: face away from the shape's middle, across the line's direction
                double rx = px[i] - spec.dx;
                double ry = py[i] - spec.dy;
                double rz = pz[i] - spec.dz;
                double along = rx * tx[i] + ry * ty[i] + rz * tz[i];
                vx = rx - along * tx[i];
                vy = ry - along * ty[i];
                vz = rz - along * tz[i];
                if (vx * vx + vy * vy + vz * vz < 1e-6) {
                    vx = 0;
                    vy = 1;
                    vz = 0;
                }
            }
            nx = vx;
            ny = vy;
            nz = vz;
            Direction normal = nearest(nx, ny, nz);
            axis = nearest(tx[i], ty[i], tz[i]).getAxis();
            return BlockOrienter.pack(normal, axis, below(ny, nx, nz));
        }

        private static boolean below(double ny, double nx, double nz) {
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            return length > 1e-9 && ny / length < TOP_BELOW;
        }
    }
}
