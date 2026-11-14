package org.example.smartglasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.item.SmartGlassesItem;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders 3D AR world-space markers, lines, and entity trackers when wearing Smart Glasses.
 */
public final class SmartGlassesWorldRenderer {

    private SmartGlassesWorldRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (SmartGlassesItem.getEquippedGlasses(mc.player).isEmpty()) {
            return;
        }

        var widgets = ClientHudState.snapshot();
        boolean hasWorldWidgets = false;
        boolean hasEntityMarkers = false;
        for (HudWidget w : widgets) {
            if (w.type() == HudWidget.Type.WORLD_MARKER || w.type() == HudWidget.Type.WORLD_LINE) {
                hasWorldWidgets = true;
            } else if (w.type() == HudWidget.Type.ENTITY_MARKER) {
                hasWorldWidgets = true;
                hasEntityMarkers = true;
            }
        }
        if (!hasWorldWidgets) return;

        Map<String, Entity> entityByUuid = null;
        if (hasEntityMarkers) {
            entityByUuid = new HashMap<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                entityByUuid.put(e.getUUID().toString(), e);
            }
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        for (HudWidget w : widgets) {
            int argb = SmartGlassesHudRenderer.normalizeColor(w.color());
            float a = ((argb >>> 24) & 0xFF) / 255.0f;
            float r = ((argb >>> 16) & 0xFF) / 255.0f;
            float g = ((argb >>> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;

            switch (w.type()) {
                case WORLD_MARKER -> {
                    AABB box = new AABB(
                            w.x() - 0.35, w.y() - 0.35, w.z() - 0.35,
                            w.x() + 0.35, w.y() + 0.35, w.z() + 0.35
                    );
                    renderBoxAndLabel(poseStack, bufferSource, camera, camPos, font, box,
                            w.x(), w.y() + 0.65, w.z(), w.text(), argb, r, g, b, a);
                }
                case WORLD_LINE -> {
                    poseStack.pushPose();
                    poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
                    VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
                    Matrix4f mat = poseStack.last().pose();
                    float dx = w.x2() - w.x();
                    float dy = w.y2() - w.y();
                    float dz = w.z2() - w.z();
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float nx = len > 1e-4f ? dx / len : 0.0f;
                    float ny = len > 1e-4f ? dy / len : 1.0f;
                    float nz = len > 1e-4f ? dz / len : 0.0f;
                    lines.addVertex(mat, w.x(), w.y(), w.z()).setColor(r, g, b, a).setNormal(nx, ny, nz);
                    lines.addVertex(mat, w.x2(), w.y2(), w.z2()).setColor(r, g, b, a).setNormal(nx, ny, nz);
                    poseStack.popPose();
                }
                case ENTITY_MARKER -> {
                    if (entityByUuid == null) continue;
                    Entity entity = entityByUuid.get(w.targetUuid());
                    if (entity == null) continue;
                    Vec3 pos = entity.getPosition(partialTick);
                    AABB rawBox = entity.getBoundingBox();
                    Vec3 entityPos = entity.position();
                    AABB box = rawBox.move(pos.x - entityPos.x, pos.y - entityPos.y, pos.z - entityPos.z).inflate(0.1);
                    renderBoxAndLabel(poseStack, bufferSource, camera, camPos, font, box,
                            pos.x, box.maxY + 0.35, pos.z, w.text(), argb, r, g, b, a);
                }
                default -> {
                }
            }
        }

        bufferSource.endBatch();
    }

    private static void renderBoxAndLabel(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Camera camera,
            Vec3 camPos,
            Font font,
            AABB box,
            double labelX,
            double labelY,
            double labelZ,
            String rawLabel,
            int argb,
            float r, float g, float b, float a
    ) {
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, a);
        poseStack.popPose();

        double dx = labelX - camPos.x;
        double dy = labelY - camPos.y;
        double dz = labelZ - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        String text = rawLabel.isEmpty()
                ? String.format("%.1fm", dist)
                : String.format("%s (%.1fm)", rawLabel, dist);

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(camera.rotation());
        float scale = (float) Math.max(0.025, Math.min(0.12, 0.025 * Math.max(1.0, dist * 0.15)));
        poseStack.scale(scale, -scale, scale);

        float textWidth = font.width(text);
        font.drawInBatch(
                text,
                -textWidth / 2.0f,
                0.0f,
                argb,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0x66000000,
                0xF000F0
        );
        poseStack.popPose();
    }
}
