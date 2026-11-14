package org.example.smartglasses;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.example.smartglasses.hud.Anchor;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.network.GlassesGazePayload;
import org.example.smartglasses.network.GlassesHudClickPayload;
import org.example.smartglasses.network.SyncHudPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HudWidgetAndPayloadTest {

    @Test
    void widgetCodecRoundTripsAllTypes() {
        List<HudWidget> widgets = List.of(
                HudWidget.rect("r1", Anchor.TOP_LEFT, 10, 20, 100, 50, 0x80FF0000).withClickable(true),
                HudWidget.text("t1", Anchor.TOP_RIGHT, -120, 15, "Hello AR", 0xFF00E5FF, 1.5f),
                HudWidget.progressBar("p1", Anchor.BOTTOM_CENTER, -100, -40, 200, 20, 42, 100, 0xFF00FF00, 0xFF222222),
                HudWidget.itemIcon("i1", Anchor.CENTER_LEFT, 12, -8, "minecraft:diamond", 1.25f),
                HudWidget.graph("g1", Anchor.BOTTOM_RIGHT, -220, -80, 200, 60, List.of(1.0f, 5.5f, 3.2f, 9.0f), 0xFF55FF55, 0x66000000),
                HudWidget.worldMarker("w1", 10.5f, 64.0f, -20.25f, "Base", 0xFF00FFFF),
                HudWidget.worldLine("l1", 0, 64, 0, 10, 70, 10, 0xFFFFAA00),
                HudWidget.entityMarker("e1", UUID.randomUUID().toString(), "Robot-1", 0xFFFF3355)
        );

        for (HudWidget original : widgets) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            HudWidget.STREAM_CODEC.encode(buf, original);
            HudWidget decoded = HudWidget.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded, "Failed round-trip for " + original.type());
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void syncHudPayloadRoundTrip() {
        HudWidget w = HudWidget.text("status", Anchor.TOP_LEFT, 20, 20, "ONLINE", 0xFF00FF00, 1.0f);
        SyncHudPayload payload = new SyncHudPayload(false, List.of("old_1", "old_2"), List.of(w));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncHudPayload.STREAM_CODEC.encode(buf, payload);
        SyncHudPayload decoded = SyncHudPayload.STREAM_CODEC.decode(buf);

        assertFalse(decoded.clearAll());
        assertEquals(List.of("old_1", "old_2"), decoded.removedIds());
        assertEquals(List.of(w), decoded.upserts());
    }

    @Test
    void gazeAndClickPayloadsRoundTrip() {
        GlassesGazePayload gaze = new GlassesGazePayload(
                GlassesGazePayload.TargetType.BLOCK,
                new BlockPos(12, 65, -34),
                Direction.NORTH,
                -1,
                12.5, 65.2, -34.0
        );
        FriendlyByteBuf buf1 = new FriendlyByteBuf(Unpooled.buffer());
        GlassesGazePayload.STREAM_CODEC.encode(buf1, gaze);
        assertEquals(gaze, GlassesGazePayload.STREAM_CODEC.decode(buf1));

        GlassesHudClickPayload click = new GlassesHudClickPayload("btn_lights", 0);
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        GlassesHudClickPayload.STREAM_CODEC.encode(buf2, click);
        assertEquals(click, GlassesHudClickPayload.STREAM_CODEC.decode(buf2));
    }
}
