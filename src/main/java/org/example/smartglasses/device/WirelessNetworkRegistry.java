package org.example.smartglasses.device;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of active Wireless Network Card devices for range-based packet delivery.
 */
public final class WirelessNetworkRegistry {
    private static final Map<UUID, WirelessEndpoint> ENDPOINTS = new ConcurrentHashMap<>();

    public interface WirelessEndpoint {
        UUID getAddress();
        @Nullable ResourceKey<Level> getDimension();
        @Nullable Vec3 getPosition();
        boolean isPortOpen(int port);
        void receiveWirelessPacket(UUID from, int port, double distance, String payload);
    }

    private WirelessNetworkRegistry() {
    }

    public static void register(WirelessEndpoint endpoint) {
        if (endpoint != null) {
            ENDPOINTS.put(endpoint.getAddress(), endpoint);
        }
    }

    public static void unregister(WirelessEndpoint endpoint) {
        if (endpoint != null) {
            ENDPOINTS.remove(endpoint.getAddress(), endpoint);
        }
    }

    public static void clear() {
        ENDPOINTS.clear();
    }

    public static int broadcast(WirelessEndpoint sender, int port, double range, String payload) {
        ResourceKey<Level> dim = sender.getDimension();
        Vec3 pos = sender.getPosition();
        if (dim == null || pos == null) return 0;

        double maxDistSq = range * range;
        int delivered = 0;

        for (WirelessEndpoint target : ENDPOINTS.values()) {
            if (target == sender || target.getAddress().equals(sender.getAddress())) continue;
            if (!dim.equals(target.getDimension())) continue;
            if (!target.isPortOpen(port)) continue;

            Vec3 tPos = target.getPosition();
            if (tPos == null) continue;
            double distSq = pos.distanceToSqr(tPos);
            if (distSq <= maxDistSq) {
                target.receiveWirelessPacket(sender.getAddress(), port, Math.sqrt(distSq), payload);
                delivered++;
            }
        }
        return delivered;
    }

    public static boolean send(WirelessEndpoint sender, UUID targetAddress, int port, double range, String payload) {
        WirelessEndpoint target = ENDPOINTS.get(targetAddress);
        if (target == null || target == sender) return false;

        ResourceKey<Level> dim = sender.getDimension();
        Vec3 pos = sender.getPosition();
        Vec3 tPos = target.getPosition();
        if (dim == null || pos == null || tPos == null) return false;
        if (!dim.equals(target.getDimension())) return false;
        if (!target.isPortOpen(port)) return false;

        double distSq = pos.distanceToSqr(tPos);
        if (distSq > range * range) return false;

        target.receiveWirelessPacket(sender.getAddress(), port, Math.sqrt(distSq), payload);
        return true;
    }
}
