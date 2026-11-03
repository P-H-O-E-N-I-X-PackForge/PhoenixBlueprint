package net.phoenixvine.blueprint.common.export;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.client.ShapeExportClient;

import java.util.function.Supplier;

/** The server's answer to "copy": the KubeJS text, which the client puts on the clipboard. */
public record S2CShapeExportPacket(String text, String fileName) {

    public static void encode(S2CShapeExportPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text(), KubeJsExport.MAX_CHARS);
        buf.writeUtf(packet.fileName(), 96);
    }

    public static S2CShapeExportPacket decode(FriendlyByteBuf buf) {
        String text = buf.readUtf(KubeJsExport.MAX_CHARS);
        String fileName = buf.readUtf(96);
        return new S2CShapeExportPacket(text, fileName);
    }

    public static void handle(S2CShapeExportPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Safely execute on the client side only
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ShapeExportClient.setClipboard(packet.text(), packet.fileName())
            );
        });
        ctx.setPacketHandled(true);
    }
}