package net.phoenixvine.blueprint.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.phoenixvine.blueprint.Blueprint;
import net.phoenixvine.blueprint.common.C2SShapeUndoPacket;
import net.phoenixvine.blueprint.common.C2SWandEditPacket;
import net.phoenixvine.blueprint.common.C2SWandPlanPacket;
import net.phoenixvine.blueprint.common.export.*;

public class BlueprintNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Blueprint.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void registerPackets() {
        int id = 0;

        // Register your packets for server-bound actions
        // (Make sure your packet classes implement encode/decode methods for FriendlyByteBuf)
        CHANNEL.messageBuilder(C2SShapeUndoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SShapeUndoPacket::encode)
                .decoder(C2SShapeUndoPacket::decode)
                .consumerNetworkThread(C2SShapeUndoPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SBlueprintWandActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBlueprintWandActionPacket::encode)
                .decoder(C2SBlueprintWandActionPacket::decode)
                .consumerNetworkThread(C2SBlueprintWandActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SWandEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWandEditPacket::encode)
                .decoder(C2SWandEditPacket::decode)
                .consumerNetworkThread(C2SWandEditPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SWandPlanPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWandPlanPacket::encode)
                .decoder(C2SWandPlanPacket::decode)
                .consumerNetworkThread(C2SWandPlanPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SImportPlacePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SImportPlacePacket::encode)
                .decoder(C2SImportPlacePacket::decode)
                .consumerNetworkThread(C2SImportPlacePacket::handle)
                .add();

        // Register client-bound packet
        CHANNEL.messageBuilder(S2CShapeExportPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CShapeExportPacket::encode)
                .decoder(S2CShapeExportPacket::decode)
                .consumerNetworkThread(S2CShapeExportPacket::handle)
                .add();
    }
}