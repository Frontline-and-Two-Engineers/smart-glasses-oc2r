package org.example.smartglasses.device;

import com.google.gson.JsonObject;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.LifecycleAwareDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.api.bus.device.rpc.IEventSink;
import li.cil.oc2.api.bus.device.rpc.RPCEventSource;
import li.cil.oc2.common.bus.device.rpc.item.AbstractItemRPCDevice;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.example.smartglasses.Config;
import org.example.smartglasses.item.WirelessCardItem;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * OC2R Wireless Network Card device supporting port-based broadcast and direct messaging.
 */
@SuppressWarnings("unused")
public class WirelessCardItemDevice extends AbstractItemRPCDevice
        implements DocumentedDevice, LifecycleAwareDevice, RPCEventSource, WirelessNetworkRegistry.WirelessEndpoint {

    private static final int MAX_OPEN_PORTS = 16;
    private static final int MAX_QUEUED_MESSAGES = 256;
    private static final int MAX_PAYLOAD_CHARS = 16384;

    private final ItemDeviceQuery query;
    private final UUID address;
    private final Set<Integer> openPorts = ConcurrentHashMap.newKeySet();
    private final Queue<Map<String, Object>> inbox = new ConcurrentLinkedQueue<>();
    private final Map<IEventSink, UUID> subscribers = new ConcurrentHashMap<>();
    private double strength;

    public WirelessCardItemDevice(ItemStack identity, ItemDeviceQuery query) {
        super(identity, "wireless_card");
        this.query = query;
        this.address = WirelessCardItem.getOrCreateAddress(identity);
        this.strength = Math.min(256.0, Config.wirelessMaxRange);
    }

    @Override
    public void onDeviceMounted() {
        WirelessNetworkRegistry.register(this);
    }

    @Override
    public void onDeviceUnmounted() {
        WirelessNetworkRegistry.unregister(this);
    }

    @Override
    public void onDeviceDisposed() {
        WirelessNetworkRegistry.unregister(this);
        inbox.clear();
        openPorts.clear();
    }

    @Override
    public UUID getAddress() {
        return address;
    }

    @Nullable
    @Override
    public ResourceKey<Level> getDimension() {
        Optional<BlockEntity> be = query.getContainerBlockEntity();
        if (be.isPresent() && be.get().getLevel() != null) {
            return be.get().getLevel().dimension();
        }
        Optional<Entity> ent = query.getContainerEntity();
        return ent.map(e -> e.level().dimension()).orElse(null);
    }

    @Nullable
    @Override
    public Vec3 getPosition() {
        Optional<BlockEntity> be = query.getContainerBlockEntity();
        if (be.isPresent()) {
            return Vec3.atCenterOf(be.get().getBlockPos());
        }
        return query.getContainerEntity().map(Entity::position).orElse(null);
    }

    @Override
    public boolean isPortOpen(int port) {
        return openPorts.contains(port);
    }

    @Override
    public void receiveWirelessPacket(UUID from, int port, double distance, String payload) {
        while (inbox.size() >= MAX_QUEUED_MESSAGES) {
            inbox.poll();
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("from", from.toString());
        msg.put("port", port);
        msg.put("distance", distance);
        msg.put("payload", payload);
        inbox.offer(msg);

        if (!subscribers.isEmpty()) {
            JsonObject json = new JsonObject();
            json.addProperty("event", "wireless_message");
            json.addProperty("from", from.toString());
            json.addProperty("port", port);
            json.addProperty("distance", distance);
            json.addProperty("payload", payload);
            for (Map.Entry<IEventSink, UUID> entry : subscribers.entrySet()) {
                entry.getKey().postEvent(entry.getValue(), json);
            }
        }
    }

    // =========================================================================
    // OC2R RPC Callbacks
    // =========================================================================

    @Callback(name = "getAddress", synchronize = false)
    public String getAddressCallback() {
        return address.toString();
    }

    @Callback(name = "open", synchronize = false)
    public boolean open(@Parameter("port") int port) {
        validatePort(port);
        if (openPorts.contains(port)) return true;
        if (openPorts.size() >= MAX_OPEN_PORTS) {
            throw new IllegalStateException("Too many open ports (max " + MAX_OPEN_PORTS + ")");
        }
        return openPorts.add(port);
    }

    @Callback(name = "close", synchronize = false)
    public boolean close(@Parameter("port") int port) {
        return openPorts.remove(port);
    }

    @Callback(name = "closeAll", synchronize = false)
    public void closeAll() {
        openPorts.clear();
    }

    @Callback(name = "isOpen", synchronize = false)
    public boolean isOpen(@Parameter("port") int port) {
        return openPorts.contains(port);
    }

    @Callback(name = "getOpenPorts", synchronize = false)
    public List<Integer> getOpenPorts() {
        List<Integer> ports = new ArrayList<>(openPorts);
        Collections.sort(ports);
        return ports;
    }

    @Callback(name = "setStrength", synchronize = false)
    public double setStrength(@Parameter("range") double range) {
        if (!Double.isFinite(range) || range < 0) {
            throw new IllegalArgumentException("Range must be >= 0");
        }
        this.strength = Math.min(range, Config.wirelessMaxRange);
        return this.strength;
    }

    @Callback(name = "getStrength", synchronize = false)
    public double getStrength() {
        return strength;
    }

    @Callback(name = "broadcast")
    public int broadcast(@Parameter("port") int port, @Parameter("payload") String payload) {
        validatePort(port);
        String clean = validatePayload(payload);
        return WirelessNetworkRegistry.broadcast(this, port, strength, clean);
    }

    @Callback(name = "send")
    public boolean send(
            @Parameter("targetAddress") String targetAddress,
            @Parameter("port") int port,
            @Parameter("payload") String payload
    ) {
        validatePort(port);
        String clean = validatePayload(payload);
        UUID target = UUID.fromString(targetAddress.trim());
        return WirelessNetworkRegistry.send(this, target, port, strength, clean);
    }

    @Nullable
    @Callback(name = "pollMessage", synchronize = false)
    public Map<String, Object> pollMessage() {
        return inbox.poll();
    }

    @Callback(name = "getMessages", synchronize = false)
    public List<Map<String, Object>> getMessages() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> msg;
        while ((msg = inbox.poll()) != null) {
            out.add(msg);
        }
        return out;
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }

    private static String validatePayload(String payload) {
        if (payload == null) return "";
        if (payload.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("Payload exceeds " + MAX_PAYLOAD_CHARS + " characters");
        }
        return payload;
    }

    @Override
    public void subscribe(IEventSink sink, UUID id) {
        subscribers.put(sink, id);
    }

    @Override
    public void unsubscribe(IEventSink sink) {
        subscribers.remove(sink);
    }

    @Override
    public void getDeviceDocumentation(DeviceVisitor visitor) {
        visitor.visitCallback("getAddress").description("Returns this wireless card's unique UUID address.");
        visitor.visitCallback("open").description("Opens a port (1-65535) to receive wireless packets.");
        visitor.visitCallback("close").description("Closes a port.");
        visitor.visitCallback("setStrength").description("Sets transmission radius in blocks (clamped to server max).");
        visitor.visitCallback("broadcast").description("Broadcasts a string payload to all cards within range listening on the port.");
        visitor.visitCallback("send").description("Sends a string payload directly to a target card address within range.");
        visitor.visitCallback("pollMessage").description("Returns and removes the next queued wireless message, or null.");
        visitor.visitCallback("getMessages").description("Returns and clears all queued wireless messages.");
    }
}
