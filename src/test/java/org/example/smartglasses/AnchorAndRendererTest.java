package org.example.smartglasses;

import org.example.smartglasses.client.ClientHudState;
import org.example.smartglasses.client.SmartGlassesHudRenderer;
import org.example.smartglasses.hud.Anchor;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.network.SyncHudPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnchorAndRendererTest {

    @AfterEach
    void cleanup() {
        ClientHudState.clear();
    }

    @Test
    void anchorParsingHandlesCaseAndInvalidValues() {
        assertEquals(Anchor.TOP_RIGHT, Anchor.fromName("top_right"));
        assertEquals(Anchor.BOTTOM_CENTER, Anchor.fromName("  BOTTOM_CENTER  "));
        assertEquals(Anchor.TOP_LEFT, Anchor.fromName("nonexistent"));
        assertEquals(Anchor.TOP_LEFT, Anchor.fromName(null));
    }

    @Test
    void normalizeColorAddsOpaqueAlphaWhenMissing() {
        assertEquals(0xFF00FF00, SmartGlassesHudRenderer.normalizeColor(0x00FF00));
        assertEquals(0x80FF0000, SmartGlassesHudRenderer.normalizeColor(0x80FF0000));
        assertEquals(0xFF000000, SmartGlassesHudRenderer.normalizeColor(0x000000));
    }

    @Test
    void clientHudStateAppliesDiffsCorrectly() {
        HudWidget r1 = HudWidget.rect("r1", Anchor.TOP_LEFT, 10, 10, 100, 40, 0xFF112233);
        HudWidget r2 = HudWidget.rect("r2", Anchor.TOP_LEFT, 20, 20, 100, 40, 0xFF445566);

        ClientHudState.apply(new SyncHudPayload(true, List.of(), List.of(r1, r2)));
        assertEquals(2, ClientHudState.snapshot().size());

        ClientHudState.apply(new SyncHudPayload(false, List.of("r1"), List.of()));
        assertEquals(1, ClientHudState.snapshot().size());
        assertEquals("r2", ClientHudState.snapshot().iterator().next().id());
    }

    @Test
    void hitTestPrefersTopmostClickableWidget() {
        HudWidget bg = HudWidget.rect("bg", Anchor.TOP_LEFT, 0, 0, 500, 500, 0x80000000).withClickable(false);
        HudWidget btn1 = HudWidget.rect("btn1", Anchor.TOP_LEFT, 100, 100, 200, 100, 0xFF00FF00).withClickable(true);
        HudWidget btn2 = HudWidget.rect("btn2", Anchor.TOP_LEFT, 150, 120, 200, 100, 0xFF0000FF).withClickable(true);

        ClientHudState.apply(new SyncHudPayload(true, List.of(), List.of(bg, btn1, btn2)));

        // At 1920x1080, uniformScale is 1.0
        HudWidget hit1 = SmartGlassesHudRenderer.hitTest(110, 110, 1920, 1080, null);
        assertNotNull(hit1);
        assertEquals("btn1", hit1.id());

        // Overlapping area should return btn2 (topmost)
        HudWidget hit2 = SmartGlassesHudRenderer.hitTest(180, 150, 1920, 1080, null);
        assertNotNull(hit2);
        assertEquals("btn2", hit2.id());

        // Non-clickable bg should not hit
        assertNull(SmartGlassesHudRenderer.hitTest(10, 10, 1920, 1080, null));
    }
}
