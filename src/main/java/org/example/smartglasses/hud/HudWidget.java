package org.example.smartglasses.hud;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable representation of a 2D HUD or 3D World-Space AR widget.
 */
public record HudWidget(
        String id,
        Type type,
        Anchor anchor,
        float x,
        float y,
        float z,
        float x2,
        float y2,
        float z2,
        float width,
        float height,
        int color,
        int bgColor,
        float value,
        float max,
        float scale,
        String text,
        String targetUuid,
        List<Float> points,
        boolean clickable
) {
    public enum Type {
        RECT,
        TEXT,
        PROGRESS_BAR,
        ITEM_ICON,
        GRAPH,
        WORLD_MARKER,
        WORLD_LINE,
        ENTITY_MARKER
    }

    public static final StreamCodec<FriendlyByteBuf, HudWidget> STREAM_CODEC = StreamCodec.of(
            HudWidget::encode,
            HudWidget::decode
    );

    private static void encode(FriendlyByteBuf buf, HudWidget w) {
        buf.writeUtf(w.id, 128);
        buf.writeEnum(w.type);
        buf.writeEnum(w.anchor);
        buf.writeFloat(w.x);
        buf.writeFloat(w.y);
        buf.writeFloat(w.z);
        buf.writeFloat(w.x2);
        buf.writeFloat(w.y2);
        buf.writeFloat(w.z2);
        buf.writeFloat(w.width);
        buf.writeFloat(w.height);
        buf.writeInt(w.color);
        buf.writeInt(w.bgColor);
        buf.writeFloat(w.value);
        buf.writeFloat(w.max);
        buf.writeFloat(w.scale);
        buf.writeUtf(w.text != null ? w.text : "", 1024);
        buf.writeUtf(w.targetUuid != null ? w.targetUuid : "", 64);
        List<Float> pts = w.points != null ? w.points : List.of();
        buf.writeVarInt(pts.size());
        for (float p : pts) {
            buf.writeFloat(p);
        }
        buf.writeBoolean(w.clickable);
    }

    private static HudWidget decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(128);
        Type type = buf.readEnum(Type.class);
        Anchor anchor = buf.readEnum(Anchor.class);
        float x = buf.readFloat();
        float y = buf.readFloat();
        float z = buf.readFloat();
        float x2 = buf.readFloat();
        float y2 = buf.readFloat();
        float z2 = buf.readFloat();
        float width = buf.readFloat();
        float height = buf.readFloat();
        int color = buf.readInt();
        int bgColor = buf.readInt();
        float value = buf.readFloat();
        float max = buf.readFloat();
        float scale = buf.readFloat();
        String text = buf.readUtf(1024);
        String targetUuid = buf.readUtf(64);
        int ptCount = buf.readVarInt();
        List<Float> pts = new ArrayList<>(ptCount);
        for (int i = 0; i < ptCount; i++) {
            pts.add(buf.readFloat());
        }
        boolean clickable = buf.readBoolean();
        return new HudWidget(id, type, anchor, x, y, z, x2, y2, z2, width, height, color, bgColor, value, max, scale, text, targetUuid, List.copyOf(pts), clickable);
    }

    public static HudWidget rect(String id, Anchor anchor, float x, float y, float width, float height, int color) {
        return new HudWidget(id, Type.RECT, anchor, x, y, 0, 0, 0, 0, width, height, color, 0, 0, 0, 1.0f, "", "", List.of(), false);
    }

    public static HudWidget text(String id, Anchor anchor, float x, float y, String text, int color, float scale) {
        return new HudWidget(id, Type.TEXT, anchor, x, y, 0, 0, 0, 0, 0, 0, color, 0, 0, 0, scale, text, "", List.of(), false);
    }

    public static HudWidget progressBar(String id, Anchor anchor, float x, float y, float width, float height, float value, float max, int fgColor, int bgColor) {
        return new HudWidget(id, Type.PROGRESS_BAR, anchor, x, y, 0, 0, 0, 0, width, height, fgColor, bgColor, value, max, 1.0f, "", "", List.of(), false);
    }

    public static HudWidget itemIcon(String id, Anchor anchor, float x, float y, String itemId, float scale) {
        return new HudWidget(id, Type.ITEM_ICON, anchor, x, y, 0, 0, 0, 0, 16, 16, 0xFFFFFFFF, 0, 0, 0, scale, itemId, "", List.of(), false);
    }

    public static HudWidget graph(String id, Anchor anchor, float x, float y, float width, float height, List<Float> points, int color, int bgColor) {
        return new HudWidget(id, Type.GRAPH, anchor, x, y, 0, 0, 0, 0, width, height, color, bgColor, 0, 0, 1.0f, "", "", List.copyOf(points), false);
    }

    public static HudWidget worldMarker(String id, float x, float y, float z, String label, int color) {
        return new HudWidget(id, Type.WORLD_MARKER, Anchor.TOP_LEFT, x, y, z, 0, 0, 0, 0, 0, color, 0, 0, 0, 1.0f, label, "", List.of(), false);
    }

    public static HudWidget worldLine(String id, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        return new HudWidget(id, Type.WORLD_LINE, Anchor.TOP_LEFT, x1, y1, z1, x2, y2, z2, 0, 0, color, 0, 0, 0, 1.0f, "", "", List.of(), false);
    }

    public static HudWidget entityMarker(String id, String entityUuid, String label, int color) {
        return new HudWidget(id, Type.ENTITY_MARKER, Anchor.TOP_LEFT, 0, 0, 0, 0, 0, 0, 0, 0, color, 0, 0, 0, 1.0f, label, entityUuid, List.of(), false);
    }

    public HudWidget withAnchor(Anchor newAnchor) {
        return new HudWidget(id, type, newAnchor, x, y, z, x2, y2, z2, width, height, color, bgColor, value, max, scale, text, targetUuid, points, clickable);
    }

    public HudWidget withClickable(boolean isClickable) {
        return new HudWidget(id, type, anchor, x, y, z, x2, y2, z2, width, height, color, bgColor, value, max, scale, text, targetUuid, points, isClickable);
    }
}
