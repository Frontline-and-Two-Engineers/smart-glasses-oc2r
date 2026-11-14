package org.example.smartglasses.client;

import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.network.SyncHudPayload;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side cache of active 2D HUD and 3D AR widgets.
 */
public final class ClientHudState {
    private static final Map<String, HudWidget> WIDGETS = new LinkedHashMap<>();

    private ClientHudState() {
    }

    public static synchronized void apply(SyncHudPayload payload) {
        if (payload.clearAll()) {
            WIDGETS.clear();
        }
        for (String removedId : payload.removedIds()) {
            WIDGETS.remove(removedId);
        }
        for (HudWidget widget : payload.upserts()) {
            WIDGETS.put(widget.id(), widget);
        }
    }

    public static synchronized void clear() {
        WIDGETS.clear();
    }

    public static synchronized Collection<HudWidget> snapshot() {
        return java.util.List.copyOf(WIDGETS.values());
    }
}
