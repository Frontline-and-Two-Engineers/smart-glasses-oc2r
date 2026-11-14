package org.example.smartglasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.hud.HudWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet synchronizing HUD widget diffs or full clears.
 */
public record SyncHudPayload(
        boolean clearAll,
        List<String> removedIds,
        List<HudWidget> upserts
) implements CustomPacketPayload {

    public static final Type<SyncHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartGlasses.MODID, "sync_hud")
    );

    public static final StreamCodec<FriendlyByteBuf, SyncHudPayload> STREAM_CODEC = StreamCodec.of(
            SyncHudPayload::encode,
            SyncHudPayload::decode
    );

    private static void encode(FriendlyByteBuf buf, SyncHudPayload payload) {
        buf.writeBoolean(payload.clearAll);
        buf.writeVarInt(payload.removedIds.size());
        for (String id : payload.removedIds) {
            buf.writeUtf(id, 128);
        }
        buf.writeVarInt(payload.upserts.size());
        for (HudWidget widget : payload.upserts) {
            HudWidget.STREAM_CODEC.encode(buf, widget);
        }
    }

    private static SyncHudPayload decode(FriendlyByteBuf buf) {
        boolean clearAll = buf.readBoolean();
        int removeCount = buf.readVarInt();
        List<String> removedIds = new ArrayList<>(removeCount);
        for (int i = 0; i < removeCount; i++) {
            removedIds.add(buf.readUtf(128));
        }
        int upsertCount = buf.readVarInt();
        List<HudWidget> upserts = new ArrayList<>(upsertCount);
        for (int i = 0; i < upsertCount; i++) {
            upserts.add(HudWidget.STREAM_CODEC.decode(buf));
        }
        return new SyncHudPayload(clearAll, removedIds, upserts);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
