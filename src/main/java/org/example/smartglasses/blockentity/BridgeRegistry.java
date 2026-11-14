package org.example.smartglasses.blockentity;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side lookup of active GlassesBridgeBlockEntities by their Bridge UUID.
 */
public final class BridgeRegistry {
    private static final Map<UUID, GlassesBridgeBlockEntity> BRIDGES = new ConcurrentHashMap<>();

    private BridgeRegistry() {
    }

    public static void register(UUID id, GlassesBridgeBlockEntity bridge) {
        if (id != null && bridge != null) {
            BRIDGES.put(id, bridge);
        }
    }

    public static void unregister(UUID id, GlassesBridgeBlockEntity bridge) {
        if (id != null) {
            BRIDGES.remove(id, bridge);
        }
    }

    @Nullable
    public static GlassesBridgeBlockEntity get(UUID id) {
        if (id == null) return null;
        GlassesBridgeBlockEntity be = BRIDGES.get(id);
        if (be != null && be.isRemoved()) {
            BRIDGES.remove(id, be);
            return null;
        }
        return be;
    }

    public static void clear() {
        BRIDGES.clear();
    }
}
