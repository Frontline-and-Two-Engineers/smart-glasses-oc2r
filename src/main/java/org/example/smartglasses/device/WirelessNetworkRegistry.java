package org.example.smartglasses.device;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.example.smartglasses.Config;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of active Wireless Network Cards and Wireless Repeaters.
 */
public final class WirelessNetworkRegistry {
    private static final Map<UUID, WirelessEndpoint> ENDPOINTS = new ConcurrentHashMap<>();
    private static final Set<WirelessRepeater> REPEATERS = ConcurrentHashMap.newKeySet();
    private static final int MAX_REPEATER_HOPS = 4;

    public interface WirelessEndpoint {
        UUID getAddress();
        @Nullable ResourceKey<Level> getDimension();
        @Nullable Vec3 getPosition();
        boolean isPortOpen(int port);
        void receiveWirelessPacket(UUID from, int port, double distance, String payload);
    }

    public interface WirelessRepeater {
        UUID getRepeaterId();
        @Nullable ResourceKey<Level> getDimension();
        @Nullable Vec3 getPosition();
        double getRelayRange();
        boolean tryConsumeRelayEnergy();
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

    public static void registerRepeater(WirelessRepeater repeater) {
        if (repeater != null) {
            REPEATERS.add(repeater);
        }
    }

    public static void unregisterRepeater(WirelessRepeater repeater) {
        if (repeater != null) {
            REPEATERS.remove(repeater);
        }
    }

    public static void clear() {
        ENDPOINTS.clear();
        REPEATERS.clear();
    }

    public static int broadcast(WirelessEndpoint sender, int port, double range, String payload) {
        ResourceKey<Level> dim = sender.getDimension();
        Vec3 pos = sender.getPosition();
        if (dim == null || pos == null) return 0;

        Set<UUID> deliveredEndpoints = new HashSet<>();
        deliveredEndpoints.add(sender.getAddress());
        Set<UUID> visitedRepeaters = new HashSet<>();

        return propagateBroadcast(sender.getAddress(), dim, pos, range, port, payload, MAX_REPEATER_HOPS, deliveredEndpoints, visitedRepeaters);
    }

    private static int propagateBroadcast(
            UUID originalSender,
            ResourceKey<Level> dim,
            Vec3 origin,
            double range,
            int port,
            String payload,
            int ttl,
            Set<UUID> deliveredEndpoints,
            Set<UUID> visitedRepeaters
    ) {
        double maxDistSq = range * range;
        int delivered = 0;

        for (WirelessEndpoint target : ENDPOINTS.values()) {
            if (deliveredEndpoints.contains(target.getAddress())) continue;
            if (!dim.equals(target.getDimension())) continue;
            if (!target.isPortOpen(port)) continue;

            Vec3 tPos = target.getPosition();
            if (tPos == null) continue;
            double distSq = origin.distanceToSqr(tPos);
            if (distSq <= maxDistSq) {
                deliveredEndpoints.add(target.getAddress());
                target.receiveWirelessPacket(originalSender, port, Math.sqrt(distSq), payload);
                delivered++;
            }
        }

        if (ttl > 0) {
            for (WirelessRepeater repeater : REPEATERS) {
                if (visitedRepeaters.contains(repeater.getRepeaterId())) continue;
                if (!dim.equals(repeater.getDimension())) continue;
                Vec3 rPos = repeater.getPosition();
                if (rPos == null) continue;
                if (origin.distanceToSqr(rPos) <= maxDistSq && repeater.tryConsumeRelayEnergy()) {
                    visitedRepeaters.add(repeater.getRepeaterId());
                    double relayRange = repeater.getRelayRange() > 0 ? repeater.getRelayRange() : Config.repeaterRange;
                    delivered += propagateBroadcast(originalSender, dim, rPos, relayRange, port, payload, ttl - 1, deliveredEndpoints, visitedRepeaters);
                }
            }
        }

        return delivered;
    }

    public static boolean send(WirelessEndpoint sender, UUID targetAddress, int port, double range, String payload) {
        WirelessEndpoint target = ENDPOINTS.get(targetAddress);
        return deliverToTarget(sender, target, port, range, payload);
    }

    public static boolean send(WirelessEndpoint sender, String addressOrPrefix, int port, double range, String payload) {
        if (addressOrPrefix == null) return false;
        String clean = addressOrPrefix.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 4) {
            throw new IllegalArgumentException("Address or prefix must be at least 4 characters");
        }

        WirelessEndpoint target = null;
        if (clean.length() == 36) {
            try {
                target = ENDPOINTS.get(UUID.fromString(clean));
            } catch (IllegalArgumentException ignored) {
                // Fall through to prefix search
            }
        }

        if (target == null) {
            List<WirelessEndpoint> matches = new ArrayList<>();
            for (WirelessEndpoint ep : ENDPOINTS.values()) {
                if (ep == sender) continue;
                if (ep.getAddress().toString().toLowerCase(Locale.ROOT).startsWith(clean)) {
                    matches.add(ep);
                }
            }
            if (matches.isEmpty()) return false;
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Ambiguous wireless address prefix: " + clean);
            }
            target = matches.get(0);
        }

        return deliverToTarget(sender, target, port, range, payload);
    }

    private static boolean deliverToTarget(WirelessEndpoint sender, @Nullable WirelessEndpoint target, int port, double range, String payload) {
        if (target == null || target == sender) return false;

        ResourceKey<Level> dim = sender.getDimension();
        Vec3 pos = sender.getPosition();
        Vec3 tPos = target.getPosition();
        if (dim == null || pos == null || tPos == null) return false;
        if (!dim.equals(target.getDimension())) return false;
        if (!target.isPortOpen(port)) return false;

        double distSq = pos.distanceToSqr(tPos);
        if (distSq <= range * range) {
            target.receiveWirelessPacket(sender.getAddress(), port, Math.sqrt(distSq), payload);
            return true;
        }

        // Try relaying via powered Wireless Repeaters
        return relayDirect(sender.getAddress(), target, dim, pos, range, port, payload, MAX_REPEATER_HOPS, new HashSet<>());
    }

    private static boolean relayDirect(
            UUID originalSender,
            WirelessEndpoint target,
            ResourceKey<Level> dim,
            Vec3 origin,
            double range,
            int port,
            String payload,
            int ttl,
            Set<UUID> visitedRepeaters
    ) {
        if (ttl <= 0) return false;
        double maxDistSq = range * range;
        Vec3 tPos = target.getPosition();
        if (tPos == null) return false;

        for (WirelessRepeater repeater : REPEATERS) {
            if (visitedRepeaters.contains(repeater.getRepeaterId())) continue;
            if (!dim.equals(repeater.getDimension())) continue;
            Vec3 rPos = repeater.getPosition();
            if (rPos == null) continue;
            if (origin.distanceToSqr(rPos) <= maxDistSq && repeater.tryConsumeRelayEnergy()) {
                visitedRepeaters.add(repeater.getRepeaterId());
                double relayRange = repeater.getRelayRange() > 0 ? repeater.getRelayRange() : Config.repeaterRange;
                double dSq = rPos.distanceToSqr(tPos);
                if (dSq <= relayRange * relayRange) {
                    target.receiveWirelessPacket(originalSender, port, Math.sqrt(dSq), payload);
                    return true;
                }
                if (relayDirect(originalSender, target, dim, rPos, relayRange, port, payload, ttl - 1, visitedRepeaters)) {
                    return true;
                }
            }
        }
        return false;
    }
}
