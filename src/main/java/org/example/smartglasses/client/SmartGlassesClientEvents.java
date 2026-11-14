package org.example.smartglasses.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.item.SmartGlassesItem;
import org.example.smartglasses.network.GlassesGazePayload;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side event handlers for Smart Glasses HUD, 3D AR rendering, and keybinds.
 */
public final class SmartGlassesClientEvents {
    private static final double GAZE_RANGE = 64.0;

    public static final KeyMapping GAZE_KEY = new KeyMapping(
            "key.smartglasses.gaze",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.smartglasses"
    );

    public static final KeyMapping CURSOR_KEY = new KeyMapping(
            "key.smartglasses.cursor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.smartglasses"
    );

    private SmartGlassesClientEvents() {
    }

    @EventBusSubscriber(modid = SmartGlasses.MODID, value = Dist.CLIENT)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(
                    VanillaGuiLayers.CROSSHAIR,
                    ResourceLocation.fromNamespaceAndPath(SmartGlasses.MODID, "hud"),
                    SmartGlassesHudRenderer::render
            );
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(GAZE_KEY);
            event.register(CURSOR_KEY);
        }
    }

    @EventBusSubscriber(modid = SmartGlasses.MODID, value = Dist.CLIENT)
    public static final class GameBusEvents {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            SmartGlassesWorldRenderer.onRenderLevelStage(event);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            boolean wearing = !SmartGlassesItem.getEquippedGlasses(mc.player).isEmpty();
            while (GAZE_KEY.consumeClick()) {
                if (wearing) {
                    sendGazeRaycast(mc);
                }
            }
            while (CURSOR_KEY.consumeClick()) {
                if (wearing && mc.screen == null) {
                    mc.setScreen(new SmartGlassesCursorScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientHudState.clear();
        }
    }

    private static void sendGazeRaycast(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;

        Vec3 eyePos = camera.getEyePosition(1.0f);
        Vec3 lookVec = camera.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.scale(GAZE_RANGE));

        HitResult blockHit = camera.pick(GAZE_RANGE, 1.0f, false);
        double maxDistSq = blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation().distanceToSqr(eyePos)
                : GAZE_RANGE * GAZE_RANGE;

        AABB searchBox = camera.getBoundingBox().expandTowards(lookVec.scale(GAZE_RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                camera,
                eyePos,
                endPos,
                searchBox,
                e -> !e.isSpectator() && e.isPickable(),
                maxDistSq
        );

        if (entityHit != null) {
            Vec3 loc = entityHit.getLocation();
            PacketDistributor.sendToServer(new GlassesGazePayload(
                    GlassesGazePayload.TargetType.ENTITY,
                    BlockPos.ZERO,
                    Direction.UP,
                    entityHit.getEntity().getId(),
                    loc.x, loc.y, loc.z
            ));
        } else if (blockHit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = bhr.getLocation();
            PacketDistributor.sendToServer(new GlassesGazePayload(
                    GlassesGazePayload.TargetType.BLOCK,
                    bhr.getBlockPos(),
                    bhr.getDirection(),
                    -1,
                    loc.x, loc.y, loc.z
            ));
        } else {
            PacketDistributor.sendToServer(new GlassesGazePayload(
                    GlassesGazePayload.TargetType.MISS,
                    BlockPos.ZERO,
                    Direction.UP,
                    -1,
                    endPos.x, endPos.y, endPos.z
            ));
        }
    }
}
