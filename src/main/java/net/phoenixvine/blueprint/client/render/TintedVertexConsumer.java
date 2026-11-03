package net.phoenixvine.blueprint.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class TintedVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;

    private double offsetX, offsetY, offsetZ;

    private float r = 1f, g = 1f, b = 1f, a = 1f;

    public TintedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    public void setAlpha(float alpha) {
        this.a = alpha;
    }

    public void setTint(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void resetTint() {
        r = 1f;
        g = 1f;
        b = 1f;
        a = 1f;
    }

    public void addOffset(double ox, double oy, double oz) {
        offsetX += ox;
        offsetY += oy;
        offsetZ += oz;
    }

    public void clearOffset() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x + offsetX, y + offsetY, z + offsetZ);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}