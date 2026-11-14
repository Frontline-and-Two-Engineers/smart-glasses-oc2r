package org.example.smartglasses.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.network.GlassesHudClickPayload;

/**
 * Transparent overlay screen allowing the player to click interactive HUD widgets.
 */
public class SmartGlassesCursorScreen extends Screen {

    public SmartGlassesCursorScreen() {
        super(Component.translatable("screen.smartglasses.cursor"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Keep world and HUD completely clear without darkening
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        HudWidget hovered = SmartGlassesHudRenderer.hitTest(mouseX, mouseY, width, height, font);
        if (hovered != null) {
            SmartGlassesHudRenderer.Bounds b = SmartGlassesHudRenderer.getWidgetBounds(hovered, width, height, font);
            if (b != null) {
                graphics.renderOutline(b.x1() - 1, b.y1() - 1, (b.x2() - b.x1()) + 2, (b.y2() - b.y1()) + 2, 0xFF00E5FF);
            }
            graphics.renderTooltip(font, Component.literal(hovered.id()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        HudWidget clicked = SmartGlassesHudRenderer.hitTest(mouseX, mouseY, width, height, font);
        if (clicked != null) {
            PacketDistributor.sendToServer(new GlassesHudClickPayload(clicked.id(), button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SmartGlassesClientEvents.CURSOR_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
