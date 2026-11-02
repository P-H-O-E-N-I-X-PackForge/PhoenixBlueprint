package net.phoenixvine.blueprint.common;

import net.minecraft.network.chat.Component;

/**
 * The shapes the wand can draw. A shape is described by up to four whole-number sizes ({@code a} to {@code d}); what each one
 * means depends on the shape, so each kind names its own and gives their defaults and limits. A shape lies in the XZ plane
 * with its axis along Y; rotating it is done separately.
 */
public enum ShapeKind {

    RING("Ring", true, p("Radius", 8, 2, 64), p("Tube", 1, 1, 12), null, null),
    DISC("Disc", false, p("Outer radius", 8, 1, 64), p("Inner radius", 0, 0, 63), p("Height", 1, 1, 64), null),
    HALF_DISC("Half disc", false, p("Outer radius", 8, 1, 64), p("Inner radius", 0, 0, 63), p("Height", 1, 1, 64), null),
    SPHERE("Sphere", false, p("Radius", 8, 1, 64), null, null, null),
    ELLIPSOID("Ellipsoid", false, p("X radius", 8, 1, 64), p("Y radius", 5, 1, 64), p("Z radius", 8, 1, 64), null),
    CYLINDER("Cylinder", false, p("Radius", 6, 1, 64), p("Length", 13, 1, 128), null, null),
    CONE("Cone", false, p("Base radius", 6, 1, 64), p("Height", 13, 1, 128), null, null),
    FRUSTUM("Frustum", false, p("Bottom radius", 8, 0, 64), p("Top radius", 4, 0, 64), p("Height", 13, 1, 128), null),
    HYPERBOLOID("Hyperboloid", false, p("Waist radius", 6, 1, 64), p("End radius", 10, 1, 64), p("Height", 20, 1, 128),
            null),
    PYRAMID("Pyramid", false, p("Half-size", 6, 1, 64), p("Height", 9, 1, 128), null, null),
    PRISM("Prism", false, p("Radius", 6, 1, 64), p("Sides", 6, 3, 16), p("Height", 9, 1, 128), null),
    DOME("Dome", false, p("Radius", 8, 1, 64), null, null, null),
    CAPSULE("Capsule", false, p("Radius", 4, 1, 32), p("Length", 13, 1, 128), null, null),
    BOX("Box", false, p("X half-size", 4, 0, 64), p("Y half-size", 4, 0, 64), p("Z half-size", 4, 0, 64), null),
    BOX_FRAME("Box frame", false, p("X half-size", 4, 0, 64), p("Y half-size", 4, 0, 64), p("Z half-size", 4, 0, 64),
            p("Edge", 1, 1, 8)),
    WEDGE("Wedge", false, p("Half-width", 4, 0, 64), p("Height", 6, 1, 128), p("Length", 8, 1, 128), null),
    STAIRS("Stairs", false, p("Half-width", 3, 0, 64), p("Steps", 6, 2, 32), p("Step height", 1, 1, 8),
            p("Step depth", 2, 1, 8)),
    ZIGGURAT("Ziggurat", false, p("Base half-size", 8, 1, 64), p("Steps", 4, 2, 32), p("Step height", 2, 1, 8),
            p("Inset", 2, 1, 8)),
    GABLE_ROOF("Gable roof", false, p("Half-width", 6, 1, 64), p("Height", 5, 1, 64), p("Length", 12, 1, 128), null),
    HIP_ROOF("Hip roof", false, p("Half-width", 6, 1, 64), p("Height", 5, 1, 64), p("Length", 12, 1, 128), null),
    PARABOLOID("Paraboloid", false, p("Radius", 8, 1, 64), p("Depth", 8, 1, 128), null, null),
    OCTAHEDRON("Octahedron", false, p("Radius", 6, 1, 64), p("Height", 12, 1, 128), null, null),
    STAR("Star", false, p("Length", 10, 2, 64), p("Base radius", 3, 1, 16), null, null),
    TWIST_TOWER("Twist tower", false, p("Radius", 6, 1, 64), p("Sides", 4, 3, 16), p("Height", 20, 1, 128),
            p("Twist", 90, 0, 720)),
    NOISE_BLOB("Noise blob", false, p("Radius", 8, 1, 64), p("Bumps", 3, 0, 16), p("Scale", 6, 1, 32),
            p("Seed", 1, 0, 999)),
    HELIX("Helix", true, p("Radius", 5, 1, 64), p("Tube", 1, 1, 12), p("Length", 16, 1, 128), p("Turns", 3, 1, 24)),
    SPIRAL("Spiral", true, p("Radius", 10, 2, 64), p("Tube", 1, 1, 12), p("Turns", 3, 1, 24), null),
    KNOT("Torus knot", true, p("Size", 8, 3, 64), p("Tube", 1, 1, 12), p("Twists", 3, 1, 11), null),
    WAVE_RING("Wave ring", true, p("Radius", 8, 2, 64), p("Tube", 1, 1, 12), p("Amplitude", 3, 0, 32),
            p("Waves", 6, 1, 32)),
    STAR_RING("Star ring", true, p("Radius", 10, 2, 64), p("Tube", 1, 1, 12), p("Points", 5, 3, 16),
            p("Star (0 or 1)", 1, 0, 1)),
    LEMNISCATE("Infinity", true, p("Size", 10, 2, 64), p("Tube", 1, 1, 12), null, null),
    ARCH("Arch", true, p("Radius", 8, 2, 64), p("Tube", 1, 1, 12), p("Depth", 1, 1, 64), null),
    SEMICIRCLE("Semicircle", true, p("Radius", 10, 2, 64), p("Tube", 1, 1, 12), p("Sweep", 180, 30, 360), null),
    POLYGON_RING("Polygon ring", true, p("Radius", 10, 2, 64), p("Tube", 1, 1, 12), p("Sides", 8, 3, 16), null),
    LINE("Line", true, p("Length", 16, 1, 128), p("Tube", 1, 1, 12), p("Bend", 0, -32, 32), null),
    ELLIPSE_RING("Ellipse ring", true, p("X radius", 12, 2, 64), p("Tube", 1, 1, 12), p("Z radius", 8, 2, 64), null),
    ROUNDED_RECT_RING("Round rect", true, p("X half-size", 12, 2, 64), p("Tube", 1, 1, 12), p("Z half-size", 8, 2, 64),
            p("Corner", 3, 0, 32)),
    GEAR_RING("Gear ring", true, p("Radius", 10, 3, 64), p("Tube", 1, 1, 12), p("Teeth", 12, 3, 48),
            p("Tooth depth", 2, 1, 12)),
    GOTHIC_ARCH("Gothic arch", true, p("Half-width", 8, 2, 64), p("Tube", 1, 1, 12), p("Sharpness", 100, 0, 200),
            p("Depth", 1, 1, 64)),
    DOUBLE_HELIX("Double helix", true, p("Radius", 5, 1, 64), p("Tube", 1, 1, 12), p("Length", 16, 1, 128),
            p("Turns", 3, 1, 24));

    /** One size of a shape: its name, default, and limits. */
    public record Param(String label, int def, int min, int max) {}

    private static Param p(String label, int def, int min, int max) {
        return new Param(label, def, min, max);
    }

    private final String displayName;
    private final boolean curve;
    private final Param[] params;

    ShapeKind(String displayName, boolean curve, Param a, Param b, Param c, Param d) {
        this.displayName = displayName;
        this.curve = curve;
        this.params = new Param[] { a, b, c, d };
    }

    public String displayName() {
        return displayName;
    }

    public Component title() {
        return Component.literal(displayName);
    }

    /** A curve shape is a tube of the given thickness drawn along a line; the others are solids. */
    public boolean isCurve() {
        return curve;
    }

    /** The size {@code index} (0 to 3), or null if this shape does not use it. */
    public Param param(int index) {
        return params[index];
    }

    public ShapeKind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ShapeKind prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
