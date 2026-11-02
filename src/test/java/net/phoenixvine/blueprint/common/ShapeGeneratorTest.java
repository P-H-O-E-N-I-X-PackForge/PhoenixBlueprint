package net.phoenixvine.blueprint.common;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeGeneratorTest {

    private static ShapePlan plan(ShapeKind kind, int... sizes) {
        ShapePlan plan = new ShapePlan();
        ShapeSpec spec = plan.current();
        spec.setKind(kind);
        for (int i = 0; i < sizes.length; i++) spec.setSize(i, sizes[i]);
        return plan;
    }

    private static LongSet cells(ShapePlan plan) {
        return ShapeGenerator.generate(plan).cells();
    }

    private static boolean has(LongSet set, int x, int y, int z) {
        return set.contains(BlockPos.asLong(x, y, z));
    }

    /** Whether every block can be reached from the first through face, edge or corner neighbours. */
    private static boolean connected(LongSet set) {
        if (set.isEmpty()) return true;
        LongSet seen = new LongOpenHashSet();
        Deque<Long> queue = new ArrayDeque<>();
        long start = set.iterator().nextLong();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            long cell = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long next = BlockPos.asLong(BlockPos.getX(cell) + dx, BlockPos.getY(cell) + dy,
                                BlockPos.getZ(cell) + dz);
                        if (set.contains(next) && seen.add(next)) queue.add(next);
                    }
                }
            }
        }
        return seen.size() == set.size();
    }

    @Test
    void solidSphereIsSymmetricAndFilled() {
        LongSet sphere = cells(plan(ShapeKind.SPHERE, 5));
        assertTrue(has(sphere, 0, 0, 0));
        assertTrue(has(sphere, 5, 0, 0));
        assertFalse(has(sphere, 6, 0, 0));
        LongIterator it = sphere.iterator();
        while (it.hasNext()) {
            long c = it.nextLong();
            assertTrue(has(sphere, -BlockPos.getX(c), -BlockPos.getY(c), -BlockPos.getZ(c)), "symmetric about the centre");
        }
        // a sphere of radius 5 holds about 4/3 * pi * 5.5^3 blocks
        assertTrue(sphere.size() > 500 && sphere.size() < 800, "size " + sphere.size());
    }

    @Test
    void hollowSphereIsOnlyTheShell() {
        ShapePlan hollow = plan(ShapeKind.SPHERE, 6);
        hollow.current().hollow = true;
        hollow.connected = false;
        LongSet shell = cells(hollow);
        assertFalse(has(shell, 0, 0, 0));
        assertFalse(has(shell, 3, 0, 0));
        assertTrue(has(shell, 6, 0, 0));
        assertTrue(shell.size() < cells(plan(ShapeKind.SPHERE, 6)).size() / 2);
    }

    @Test
    void ringLiesFlatAndIsOneConnectedLoop() {
        LongSet ring = cells(plan(ShapeKind.RING, 10, 1));
        LongIterator it = ring.iterator();
        while (it.hasNext()) assertEquals(0, BlockPos.getY(it.nextLong()), "a flat ring has one layer");
        assertTrue(has(ring, 10, 0, 0));
        assertTrue(has(ring, 0, 0, -10));
        assertFalse(has(ring, 0, 0, 0), "the middle is open");
        assertTrue(connected(ring));
    }

    @Test
    void turnedRingHasNoHolesAtAnyAngle() {
        for (int yaw : new int[] { 0, 15, 30, 45, 60, 90 }) {
            for (int pitch : new int[] { 0, 15, 30, 45, 60, 90, 135 }) {
                ShapePlan plan = plan(ShapeKind.RING, 12, 1);
                plan.current().yaw = yaw;
                plan.current().pitch = pitch;
                plan.current().roll = 20;
                assertTrue(connected(cells(plan)), "ring at yaw " + yaw + " pitch " + pitch + " has a gap");
            }
        }
    }

    @Test
    void ringStandingUpIsInAVerticalPlane() {
        ShapePlan plan = plan(ShapeKind.RING, 8, 1);
        plan.current().pitch = 90;
        LongSet ring = cells(plan);
        LongIterator it = ring.iterator();
        while (it.hasNext()) assertEquals(0, BlockPos.getZ(it.nextLong()), "turned a quarter, the ring is in the XY plane");
        assertTrue(has(ring, 0, 8, 0));
        assertTrue(has(ring, 0, -8, 0));
    }

    @Test
    void severalRingsCombine() {
        ShapePlan gyro = plan(ShapeKind.RING, 8, 1);
        gyro.addPart();
        gyro.current().pitch = 90;
        gyro.addPart();
        gyro.current().pitch = 0;
        gyro.current().roll = 90;
        LongSet all = cells(gyro);
        assertTrue(has(all, 8, 0, 0));
        assertTrue(has(all, 0, 8, 0));
        assertTrue(has(all, 0, 0, 8));
        assertTrue(connected(all));
    }

    @Test
    void sittingOnTheBaseRaisesTheShape() {
        ShapePlan plan = plan(ShapeKind.SPHERE, 4);
        plan.sitOnBase = true;
        LongSet sphere = cells(plan);
        int minY = Integer.MAX_VALUE;
        LongIterator it = sphere.iterator();
        while (it.hasNext()) minY = Math.min(minY, BlockPos.getY(it.nextLong()));
        assertEquals(0, minY);
    }

    @Test
    void everyShapeMakesSomethingAndStaysInBounds() {
        for (ShapeKind kind : ShapeKind.values()) {
            ShapePlan plan = new ShapePlan();
            plan.current().setKind(kind);
            ShapeGenerator.Result result = ShapeGenerator.generate(plan);
            assertFalse(result.cells().isEmpty(), kind + " made nothing");
            assertFalse(result.truncated(), kind + " is too big at its defaults");
            assertTrue(connected(result.cells()) ||
                    kind == ShapeKind.PYRAMID || kind == ShapeKind.OCTAHEDRON ||
                    kind == ShapeKind.STAR || kind == ShapeKind.NOISE_BLOB ||
                    kind == ShapeKind.GEAR_RING || kind == ShapeKind.STAR_RING ||
                    kind == ShapeKind.KNOT || kind == ShapeKind.WAVE_RING ||
                    kind == ShapeKind.BOX_FRAME || kind == ShapeKind.ARCH ||
                    kind == ShapeKind.SEMICIRCLE || kind == ShapeKind.POLYGON_RING ||
                    kind == ShapeKind.LINE || kind == ShapeKind.ELLIPSE_RING ||
                    kind == ShapeKind.ROUNDED_RECT_RING || kind == ShapeKind.DOUBLE_HELIX ||
                    kind == ShapeKind.HELIX || kind == ShapeKind.SPIRAL ||
                    kind == ShapeKind.LEMNISCATE, kind + " came out in pieces");
        }
    }

    @Test
    void anEnormousShapeIsCutShortAndFlagged() {
        ShapePlan plan = plan(ShapeKind.SPHERE, 64);
        ShapeGenerator.Result result = ShapeGenerator.generate(plan);
        assertTrue(result.truncated());
    }

    @Test
    void aPlanSurvivesBeingSavedAndLoaded() {
        ShapePlan plan = plan(ShapeKind.HELIX, 6, 2, 20, 4);
        plan.current().yaw = 45;
        plan.current().hollow = true;
        plan.addPart();
        plan.current().setKind(ShapeKind.KNOT);
        ShapePlan loaded = ShapePlan.load(plan.save());
        assertEquals(plan, loaded);
        assertEquals(2, loaded.parts.size());
        assertEquals(ShapeKind.HELIX, loaded.parts.get(0).kind);
    }

    /** Whether every block can be reached from the first through blocks that share a face. */
    private static boolean faceConnected(LongSet set) {
        if (set.isEmpty()) return true;
        LongSet seen = new LongOpenHashSet();
        Deque<Long> queue = new ArrayDeque<>();
        long start = set.iterator().nextLong();
        queue.add(start);
        seen.add(start);
        int[][] steps = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!queue.isEmpty()) {
            long cell = queue.poll();
            for (int[] d : steps) {
                long next = BlockPos.asLong(BlockPos.getX(cell) + d[0], BlockPos.getY(cell) + d[1],
                        BlockPos.getZ(cell) + d[2]);
                if (set.contains(next) && seen.add(next)) queue.add(next);
            }
        }
        return seen.size() == set.size();
    }

    @Test
    void thinRingsJoinFaceToFaceUnlessAskedNotTo() {
        for (int pitch : new int[] { 0, 30, 45, 90 }) {
            ShapePlan joined = plan(ShapeKind.RING, 9, 1);
            joined.current().pitch = pitch;
            joined.current().yaw = 20;
            assertTrue(faceConnected(cells(joined)), "ring at pitch " + pitch + " has diagonal gaps");
        }

        ShapePlan loose = plan(ShapeKind.RING, 9, 1);
        loose.current().pitch = 45;
        loose.current().yaw = 20;
        loose.connected = false;
        assertFalse(faceConnected(cells(loose)), "a tilted one-block ring touches at corners without the option");
        assertTrue(cells(loose).size() < cells(plan(ShapeKind.RING, 9, 1)).size() * 2);
    }

    @Test
    void everyShapeIsFaceConnectedByDefault() {
        for (ShapeKind kind : ShapeKind.values()) {
            if (kind == ShapeKind.PYRAMID || kind == ShapeKind.OCTAHEDRON || kind == ShapeKind.STAR ||
                    kind == ShapeKind.NOISE_BLOB || kind.isCurve() || kind == ShapeKind.SPHERE ||
                    kind == ShapeKind.ELLIPSOID || kind == ShapeKind.CYLINDER || kind == ShapeKind.CONE ||
                    kind == ShapeKind.FRUSTUM || kind == ShapeKind.HYPERBOLOID || kind == ShapeKind.DOME ||
                    kind == ShapeKind.CAPSULE || kind == ShapeKind.PARABOLOID) {
                continue;
            }
            ShapePlan plan = new ShapePlan();
            plan.current().setKind(kind);
            assertTrue(faceConnected(ShapeGenerator.generate(plan).cells()), kind + " has diagonal gaps");
        }
    }

    @Test
    void pinnedPartsStayPutWhileFreeOnesFollowTheAnchor() {
        ShapePlan plan = plan(ShapeKind.RING, 9, 1);
        plan.addPart();
        plan.selected = 1;
        plan.current().setKind(ShapeKind.SPHERE);
        ShapeSpec ring = plan.parts.get(0);
        ring.locked = true;
        ring.pinDim = "minecraft:overworld";
        ring.pinX = 100;
        ring.pinY = 64;
        ring.pinZ = -20;

        LongSet a = ShapeGenerator.world(plan, new BlockPos(0, 70, 0), "minecraft:overworld").cells();
        LongSet b = ShapeGenerator.world(plan, new BlockPos(30, 70, 5), "minecraft:overworld").cells();
        LongSet pinned = ShapeGenerator.pinnedParts(plan, "minecraft:overworld").cells();
        assertFalse(pinned.isEmpty());
        assertTrue(a.containsAll(pinned));
        assertTrue(b.containsAll(pinned));
        assertFalse(a.equals(b), "the free sphere follows the anchor");
    }

    @Test
    void partsPinnedInAnotherDimensionAreLeftOut() {
        ShapePlan plan = plan(ShapeKind.RING, 9, 1);
        ShapeSpec ring = plan.current();
        ring.locked = true;
        ring.pinDim = "minecraft:the_nether";
        ShapeGenerator.WorldResult result = ShapeGenerator.world(plan, BlockPos.ZERO, "minecraft:overworld");
        assertEquals(1, result.skippedParts());
        assertTrue(result.cells().isEmpty());
    }

    @Test
    void pinnedPartsSurviveSaving() {
        ShapePlan plan = plan(ShapeKind.RING, 9, 1);
        ShapeSpec ring = plan.current();
        ring.locked = true;
        ring.pinDim = "minecraft:overworld";
        ring.pinX = 5;
        ring.pinY = 6;
        ring.pinZ = 7;
        ShapePlan back = ShapePlan.load(plan.save());
        assertEquals(plan, back);
        assertTrue(back.current().locked);
        assertEquals(6, back.current().pinY);
    }
}