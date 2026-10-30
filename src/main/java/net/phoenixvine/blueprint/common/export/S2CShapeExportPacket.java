package net.phoenixvine.blueprint.common.export;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.client.ShapeExportClient;

/** The server's answer to "copy": the KubeJS text, which the client puts on the clipboard. */
public record S2CShapeExportPacket(String text, String fileName) implements CustomPacketPayload {

    public static final Type<S2CShapeExportPacket> TYPE = new Type<>(Blueprint.id("blueprint_wand_export"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CShapeExportPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(KubeJsExport.MAX_CHARS), S2CShapeExportPacket::text,
            ByteBufCodecs.stringUtf8(96), S2CShapeExportPacket::fileName, S2CShapeExportPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CShapeExportPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ShapeExportClient.setClipboard(packet.text(), packet.fileName()));
    }
}
