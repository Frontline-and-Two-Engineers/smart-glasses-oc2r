package org.example.smartglasses.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.example.smartglasses.SmartGlasses;

/**
 * Client -> Server packet sent when the player triggers the Smart Glasses gaze/scan keybind.
 */
public record GlassesGazePayload(
        TargetType targetType,
        BlockPos blockPos,
        Direction face,
        int entityId,
        double hitX,
        double hitY,
        double hitZ
) implements CustomPacketPayload {

    public enum TargetType {
        MISS,
        BLOCK,
        ENTITY
    }

    public static final Type<GlassesGazePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SmartGlasses.MODID, "glasses_gaze")
    );

    public static final StreamCodec<FriendlyByteBuf, GlassesGazePayload> STREAM_CODEC = StreamCodec.of(
            GlassesGazePayload::encode,
            GlassesGazePayload::decode
    );

    private static void encode(FriendlyByteBuf buf, GlassesGazePayload payload) {
        buf.writeEnum(payload.targetType);
        buf.writeBlockPos(payload.blockPos);
        buf.writeEnum(payload.face);
        buf.writeInt(payload.entityId);
        buf.writeDouble(payload.hitX);
        buf.writeDouble(payload.hitY);
        buf.writeDouble(payload.hitZ);
    }

    private static GlassesGazePayload decode(FriendlyByteBuf buf) {
        TargetType targetType = buf.readEnum(TargetType.class);
        BlockPos blockPos = buf.readBlockPos();
        Direction face = buf.readEnum(Direction.class);
        int entityId = buf.readInt();
        double hitX = buf.readDouble();
        double hitY = buf.readDouble();
        double hitZ = buf.readDouble();
        return new GlassesGazePayload(targetType, blockPos, face, entityId, hitX, hitY, hitZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
