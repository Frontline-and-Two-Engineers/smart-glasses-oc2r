package org.example.smartglasses.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.item.SmartGlassesItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Renders 2D Smart Glasses HUD widgets on the player's screen.
 */
public final class SmartGlassesHudRenderer {
    private static final float VIRTUAL_WIDTH = 1920.0f;
    private static final float VIRTUAL_HEIGHT = 1080.0f;

    private SmartGlassesHudRenderer() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (SmartGlassesItem.getEquippedGlasses(mc.player).isEmpty()) {
            return;
        }

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float uniformScale = Math.min(screenW / VIRTUAL_WIDTH, screenH / VIRTUAL_HEIGHT);
        if (uniformScale <= 0.0f) return;

        Font font = mc.font;

        for (HudWidget w : ClientHudState.snapshot()) {
            if (isWorldType(w.type())) continue;

            float baseX = w.anchor().factorX * screenW + w.x() * uniformScale;
            float baseY = w.anchor().factorY * screenH + w.y() * uniformScale;

            switch (w.type()) {
                case RECT -> {
                    int x1 = Math.round(baseX);
                    int y1 = Math.round(baseY);
                    int x2 = Math.round(baseX + w.width() * uniformScale);
                    int y2 = Math.round(baseY + w.height() * uniformScale);
                    graphics.fill(x1, y1, x2, y2, normalizeColor(w.color()));
                }
                case PROGRESS_BAR -> {
                    int x1 = Math.round(baseX);
                    int y1 = Math.round(baseY);
                    int x2 = Math.round(baseX + w.width() * uniformScale);
                    int y2 = Math.round(baseY + w.height() * uniformScale);
                    graphics.fill(x1, y1, x2, y2, normalizeColor(w.bgColor()));

                    float ratio = w.max() > 0 ? Math.clamp(w.value() / w.max(), 0.0f, 1.0f) : 0.0f;
                    int fillX2 = Math.round(baseX + w.width() * uniformScale * ratio);
                    if (fillX2 > x1) {
                        graphics.fill(x1, y1, fillX2, y2, normalizeColor(w.color()));
                    }
                }
                case TEXT -> {
                    float textScale = Math.max(0.25f, w.scale()) * uniformScale * 2.0f;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(baseX, baseY, 0.0f);
                    pose.scale(textScale, textScale, 1.0f);
                    graphics.drawString(font, w.text(), 0, 0, normalizeColor(w.color()), true);
                    pose.popPose();
                }
                case ITEM_ICON -> {
                    ResourceLocation itemLoc = ResourceLocation.tryParse(w.text());
                    if (itemLoc != null) {
                        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(itemLoc);
                        if (itemOpt.isPresent()) {
                            float iconScale = Math.max(0.25f, w.scale()) * uniformScale * 2.0f;
                            var pose = graphics.pose();
                            pose.pushPose();
                            pose.translate(baseX, baseY, 0.0f);
                            pose.scale(iconScale, iconScale, 1.0f);
                            graphics.renderItem(new ItemStack(itemOpt.get()), 0, 0);
                            pose.popPose();
                        }
                    }
                }
                case GRAPH -> {
                    int x1 = Math.round(baseX);
                    int y1 = Math.round(baseY);
                    int wPx = Math.max(1, Math.round(w.width() * uniformScale));
                    int hPx = Math.max(1, Math.round(w.height() * uniformScale));
                    graphics.fill(x1, y1, x1 + wPx, y1 + hPx, normalizeColor(w.bgColor()));

                    List<Float> pts = w.points();
                    if (pts != null && !pts.isEmpty()) {
                        float minV = Float.MAX_VALUE;
                        float maxV = -Float.MAX_VALUE;
                        for (float v : pts) {
                            if (v < minV) minV = v;
                            if (v > maxV) maxV = v;
                        }
                        float range = maxV - minV;
                        if (range < 1e-5f) range = 1.0f;

                        int fg = normalizeColor(w.color());
                        int n = pts.size();
                        int barW = Math.max(1, wPx / Math.max(1, n));
                        for (int i = 0; i < n; i++) {
                            float norm = (pts.get(i) - minV) / range;
                            int bx1 = x1 + (i * wPx) / n;
                            int bx2 = Math.min(x1 + wPx, Math.max(bx1 + 1, bx1 + barW));
                            int barH = Math.max(1, Math.round(norm * (hPx - 2)));
                            int by1 = y1 + hPx - barH;
                            graphics.fill(bx1, by1, bx2, y1 + hPx, fg);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    @Nullable
    public static Bounds getWidgetBounds(HudWidget w, int screenW, int screenH, Font font) {
        if (isWorldType(w.type())) return null;
        float uniformScale = Math.min(screenW / VIRTUAL_WIDTH, screenH / VIRTUAL_HEIGHT);
        if (uniformScale <= 0.0f) return null;

        float baseX = w.anchor().factorX * screenW + w.x() * uniformScale;
        float baseY = w.anchor().factorY * screenH + w.y() * uniformScale;

        float wPx;
        float hPx;
        switch (w.type()) {
            case TEXT -> {
                float textScale = Math.max(0.25f, w.scale()) * uniformScale * 2.0f;
                wPx = font.width(w.text()) * textScale;
                hPx = font.lineHeight * textScale;
            }
            case ITEM_ICON -> {
                float iconScale = Math.max(0.25f, w.scale()) * uniformScale * 2.0f;
                wPx = 16.0f * iconScale;
                hPx = 16.0f * iconScale;
            }
            default -> {
                wPx = w.width() * uniformScale;
                hPx = w.height() * uniformScale;
            }
        }
        return new Bounds(Math.round(baseX), Math.round(baseY), Math.round(baseX + wPx), Math.round(baseY + hPx));
    }

    @Nullable
    public static HudWidget hitTest(double mouseX, double mouseY, int screenW, int screenH, Font font) {
        List<HudWidget> widgets = List.copyOf(ClientHudState.snapshot());
        // Iterate in reverse so topmost rendered widget wins
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidget w = widgets.get(i);
            if (!w.clickable()) continue;
            Bounds b = getWidgetBounds(w, screenW, screenH, font);
            if (b != null && mouseX >= b.x1 && mouseX <= b.x2 && mouseY >= b.y1 && mouseY <= b.y2) {
                return w;
            }
        }
        return null;
    }

    public record Bounds(int x1, int y1, int x2, int y2) {
    }

    private static boolean isWorldType(HudWidget.Type type) {
        return type == HudWidget.Type.WORLD_MARKER
                || type == HudWidget.Type.WORLD_LINE
                || type == HudWidget.Type.ENTITY_MARKER;
    }

    /**
     * Ensures colors without an explicit alpha byte (e.g. 0x00FF00) render opaque instead of invisible.
     */
    public static int normalizeColor(int argb) {
        if ((argb & 0xFF000000) == 0) {
            return argb | 0xFF000000;
        }
        return argb;
    }
}
