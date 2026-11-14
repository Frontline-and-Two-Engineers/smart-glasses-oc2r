package org.example.smartglasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.example.smartglasses.SmartGlasses;

/**
 * Client -> Server packet sent when a player clicks a clickable HUD widget in cursor mode.
 */
public record GlassesHudClickPayload(
        String widgetId,
        int button
) implements CustomPacketPayload {

    public static final Type<GlassesHudClickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartGlasses.MODID, "glasses_hud_click")
    );

    public static final StreamCodec<FriendlyByteBuf, GlassesHudClickPayload> STREAM_CODEC = StreamCodec.of(
            GlassesHudClickPayload::encode,
            GlassesHudClickPayload::decode
    );

    private static void encode(FriendlyByteBuf buf, GlassesHudClickPayload payload) {
        buf.writeUtf(payload.widgetId, 128);
        buf.writeVarInt(payload.button);
    }

    private static GlassesHudClickPayload decode(FriendlyByteBuf buf) {
        return new GlassesHudClickPayload(buf.readUtf(128), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
