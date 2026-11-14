package org.example.smartglasses.blockentity;

import com.google.gson.JsonObject;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.NamedDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.bus.device.rpc.IEventSink;
import li.cil.oc2.api.bus.device.rpc.RPCEventSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.smartglasses.Config;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.hud.Anchor;
import org.example.smartglasses.hud.HudWidget;
import org.example.smartglasses.item.SmartGlassesItem;
import org.example.smartglasses.network.GlassesGazePayload;
import org.example.smartglasses.network.GlassesHudClickPayload;
import org.example.smartglasses.network.SyncHudPayload;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * OC2R RPC Device managing 2D HUD and 3D AR widgets for bound Smart Glasses.
 */
@SuppressWarnings("unused")
public class GlassesBridgeBlockEntity extends BlockEntity
        implements NamedDevice, DocumentedDevice, RPCEventSource {

    private static final String TAG_BRIDGE_ID = "bridgeId";
    private static final int MAX_WIDGETS_PER_LAYER = 512;
    private static final int MAX_QUEUED_EVENTS = 256;

    private UUID bridgeId = UUID.randomUUID();
    private final Map<IEventSink, UUID> subscribers = new ConcurrentHashMap<>();
    private final Queue<Map<String, Object>> eventQueue = new ConcurrentLinkedQueue<>();

    // Shared layer + per-player layers
    private final Map<String, HudWidget> globalWidgets = new LinkedHashMap<>();
    private final Map<UUID, Map<String, HudWidget>> playerWidgets = new ConcurrentHashMap<>();

    // Pending diff state (flushed in serverTick)
    private boolean globalClearAll = false;
    private final Set<String> globalDirtyUpserts = new LinkedHashSet<>();
    private final Set<String> globalDirtyRemoves = new LinkedHashSet<>();

    private final Map<UUID, LayerDiff> playerDiffs = new ConcurrentHashMap<>();
    private final Set<UUID> connectedPlayers = new HashSet<>();

    private static final class LayerDiff {
        boolean clearAll = false;
        final Set<String> upserts = new LinkedHashSet<>();
        final Set<String> removes = new LinkedHashSet<>();
    }

    public GlassesBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(SmartGlasses.GLASSES_BRIDGE_BE.get(), pos, state);
    }

    public UUID getBridgeId() {
        return bridgeId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            BridgeRegistry.register(bridgeId, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            BridgeRegistry.unregister(bridgeId, this);
            // Clear HUD for any connected players when bridge is broken
            if (level instanceof ServerLevel serverLevel) {
                SyncHudPayload clear = new SyncHudPayload(true, List.of(), List.of());
                for (UUID playerId : connectedPlayers) {
                    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        PacketDistributor.sendToPlayer(player, clear);
                    }
                }
            }
            connectedPlayers.clear();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(TAG_BRIDGE_ID, bridgeId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_BRIDGE_ID)) {
            BridgeRegistry.unregister(bridgeId, this);
            bridgeId = tag.getUUID(TAG_BRIDGE_ID);
            if (level != null && !level.isClientSide) {
                BridgeRegistry.register(bridgeId, this);
            }
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Set<UUID> currentPlayers = new HashSet<>();
        List<ServerPlayer> activeRecipients = new ArrayList<>();

        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (isPlayerConnected(player)) {
                currentPlayers.add(player.getUUID());
                activeRecipients.add(player);
            }
        }

        // Detect newly connected players -> send full snapshot
        for (ServerPlayer player : activeRecipients) {
            UUID pid = player.getUUID();
            if (!connectedPlayers.contains(pid)) {
                connectedPlayers.add(pid);
                sendFullSnapshot(player);
                emitPlayerEvent("glasses_player_connected", player);
            }
        }

        // Detect disconnected players -> clear client HUD if online
        Iterator<UUID> it = connectedPlayers.iterator();
        while (it.hasNext()) {
            UUID pid = it.next();
            if (!currentPlayers.contains(pid)) {
                it.remove();
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(pid);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new SyncHudPayload(true, List.of(), List.of()));
                    emitPlayerEvent("glasses_player_disconnected", player);
                }
            }
        }

        // Consume energy for active recipients (if enabled in config)
        for (ServerPlayer player : activeRecipients) {
            ItemStack glasses = SmartGlassesItem.getEquippedGlasses(player);
            if (!glasses.isEmpty()) {
                SmartGlassesItem.consumeEnergy(glasses);
            }
        }

        // Flush pending diffs to active recipients
        boolean hasGlobalDiff = globalClearAll || !globalDirtyUpserts.isEmpty() || !globalDirtyRemoves.isEmpty();
        if (hasGlobalDiff || !playerDiffs.isEmpty()) {
            for (ServerPlayer player : activeRecipients) {
                UUID pid = player.getUUID();
                LayerDiff pDiff = playerDiffs.remove(pid);
                if (!hasGlobalDiff && pDiff == null) continue;

                boolean clear = globalClearAll || (pDiff != null && pDiff.clearAll);
                if (clear) {
                    sendFullSnapshot(player);
                    continue;
                }

                List<String> removes = new ArrayList<>(globalDirtyRemoves);
                List<HudWidget> upserts = new ArrayList<>();
                Map<String, HudWidget> pMap = playerWidgets.get(pid);

                for (String id : globalDirtyUpserts) {
                    // Player-specific widget with same ID overrides global widget
                    if (pMap != null && pMap.containsKey(id)) {
                        upserts.add(pMap.get(id));
                    } else {
                        HudWidget w = globalWidgets.get(id);
                        if (w != null) upserts.add(w);
                    }
                }

                if (pDiff != null) {
                    for (String id : pDiff.removes) {
                        if (!globalWidgets.containsKey(id)) {
                            removes.add(id);
                        } else {
                            // Revert to global widget if player override was removed
                            upserts.add(globalWidgets.get(id));
                        }
                    }
                    if (pMap != null) {
                        for (String id : pDiff.upserts) {
                            HudWidget w = pMap.get(id);
                            if (w != null) upserts.add(w);
                        }
                    }
                }

                if (!removes.isEmpty() || !upserts.isEmpty()) {
                    PacketDistributor.sendToPlayer(player, new SyncHudPayload(false, removes, upserts));
                }
            }

            globalClearAll = false;
            globalDirtyUpserts.clear();
            globalDirtyRemoves.clear();
            playerDiffs.clear();
        }
    }

    private void sendFullSnapshot(ServerPlayer player) {
        Map<String, HudWidget> merged = new LinkedHashMap<>(globalWidgets);
        Map<String, HudWidget> pMap = playerWidgets.get(player.getUUID());
        if (pMap != null) {
            merged.putAll(pMap);
        }
        PacketDistributor.sendToPlayer(player, new SyncHudPayload(true, List.of(), new ArrayList<>(merged.values())));
    }

    public boolean isPlayerConnected(ServerPlayer player) {
        ItemStack glasses = SmartGlassesItem.getEquippedGlasses(player);
        UUID bound = SmartGlassesItem.getBridgeId(glasses);
        if (!bridgeId.equals(bound)) return false;
        if (!SmartGlassesItem.hasEnoughEnergy(glasses)) return false;
        if (level == null) return false;

        boolean sameDim = player.level().dimension().equals(level.dimension());
        if (!sameDim && !Config.allowCrossDimension) return false;

        if (sameDim && Config.maxRange > 0) {
            double maxDistSq = (double) Config.maxRange * Config.maxRange;
            double dx = player.getX() - (worldPosition.getX() + 0.5);
            double dy = player.getY() - (worldPosition.getY() + 0.5);
            double dz = player.getZ() - (worldPosition.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // OC2R RPC Callbacks
    // =========================================================================

    @Callback(name = "getBridgeId", synchronize = false)
    public String getBridgeIdCallback() {
        return bridgeId.toString();
    }

    @Callback(name = "getConnectedPlayers")
    public List<Map<String, Object>> getConnectedPlayers() {
        if (!(level instanceof ServerLevel serverLevel)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (isPlayerConnected(player)) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("uuid", player.getUUID().toString());
                info.put("name", player.getGameProfile().getName());
                info.put("x", player.getX());
                info.put("y", player.getY());
                info.put("z", player.getZ());
                info.put("dimension", player.level().dimension().location().toString());
                result.add(info);
            }
        }
        return result;
    }

    @Callback(name = "clear")
    public void clear() {
        globalWidgets.clear();
        globalDirtyUpserts.clear();
        globalDirtyRemoves.clear();
        globalClearAll = true;
    }

    @Callback(name = "clearPlayer")
    public void clearPlayer(@Parameter("playerUuid") String playerUuid) {
        UUID pid = parseUuid(playerUuid);
        Map<String, HudWidget> pMap = playerWidgets.get(pid);
        if (pMap != null) {
            pMap.clear();
        }
        LayerDiff diff = playerDiffs.computeIfAbsent(pid, k -> new LayerDiff());
        diff.upserts.clear();
        diff.removes.clear();
        diff.clearAll = true;
    }

    @Callback(name = "removeWidget")
    public boolean removeWidget(@Parameter("id") String id) {
        validateId(id);
        if (globalWidgets.remove(id) != null) {
            globalDirtyUpserts.remove(id);
            globalDirtyRemoves.add(id);
            return true;
        }
        return false;
    }

    @Callback(name = "removePlayerWidget")
    public boolean removePlayerWidget(@Parameter("playerUuid") String playerUuid, @Parameter("id") String id) {
        UUID pid = parseUuid(playerUuid);
        validateId(id);
        Map<String, HudWidget> pMap = playerWidgets.get(pid);
        if (pMap != null && pMap.remove(id) != null) {
            LayerDiff diff = playerDiffs.computeIfAbsent(pid, k -> new LayerDiff());
            diff.upserts.remove(id);
            diff.removes.add(id);
            return true;
        }
        return false;
    }

    @Callback(name = "addRect")
    public void addRect(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("color") int color
    ) {
        upsertGlobal(HudWidget.rect(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, color));
    }

    @Callback(name = "addPlayerRect")
    public void addPlayerRect(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("color") int color
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.rect(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, color));
    }

    @Callback(name = "addText")
    public void addText(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("text") String text,
            @Parameter("color") int color,
            @Parameter("scale") double scale
    ) {
        upsertGlobal(HudWidget.text(id, Anchor.TOP_LEFT, (float) x, (float) y, text, color, (float) scale));
    }

    @Callback(name = "addPlayerText")
    public void addPlayerText(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("text") String text,
            @Parameter("color") int color,
            @Parameter("scale") double scale
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.text(id, Anchor.TOP_LEFT, (float) x, (float) y, text, color, (float) scale));
    }

    @Callback(name = "addProgressBar")
    public void addProgressBar(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("value") double value,
            @Parameter("max") double max,
            @Parameter("fgColor") int fgColor,
            @Parameter("bgColor") int bgColor
    ) {
        upsertGlobal(HudWidget.progressBar(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, (float) value, (float) max, fgColor, bgColor));
    }

    @Callback(name = "addPlayerProgressBar")
    public void addPlayerProgressBar(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("value") double value,
            @Parameter("max") double max,
            @Parameter("fgColor") int fgColor,
            @Parameter("bgColor") int bgColor
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.progressBar(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, (float) value, (float) max, fgColor, bgColor));
    }

    @Callback(name = "addWorldMarker")
    public void addWorldMarker(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("z") double z,
            @Parameter("label") String label,
            @Parameter("color") int color
    ) {
        upsertGlobal(HudWidget.worldMarker(id, (float) x, (float) y, (float) z, label, color));
    }

    @Callback(name = "addPlayerWorldMarker")
    public void addPlayerWorldMarker(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("z") double z,
            @Parameter("label") String label,
            @Parameter("color") int color
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.worldMarker(id, (float) x, (float) y, (float) z, label, color));
    }

    @Callback(name = "setAnchor")
    public boolean setAnchor(@Parameter("id") String id, @Parameter("anchor") String anchorName) {
        validateId(id);
        HudWidget existing = globalWidgets.get(id);
        if (existing == null) return false;
        upsertGlobal(existing.withAnchor(Anchor.fromName(anchorName)));
        return true;
    }

    @Callback(name = "setPlayerAnchor")
    public boolean setPlayerAnchor(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("anchor") String anchorName
    ) {
        UUID pid = parseUuid(playerUuid);
        validateId(id);
        Map<String, HudWidget> pMap = playerWidgets.get(pid);
        if (pMap == null) return false;
        HudWidget existing = pMap.get(id);
        if (existing == null) return false;
        upsertPlayer(pid, existing.withAnchor(Anchor.fromName(anchorName)));
        return true;
    }

    @Callback(name = "addItemIcon")
    public void addItemIcon(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("itemId") String itemId,
            @Parameter("scale") double scale
    ) {
        upsertGlobal(HudWidget.itemIcon(id, Anchor.TOP_LEFT, (float) x, (float) y, itemId, (float) scale));
    }

    @Callback(name = "addPlayerItemIcon")
    public void addPlayerItemIcon(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("itemId") String itemId,
            @Parameter("scale") double scale
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.itemIcon(id, Anchor.TOP_LEFT, (float) x, (float) y, itemId, (float) scale));
    }

    @Callback(name = "addGraph")
    public void addGraph(
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("values") List<Number> values,
            @Parameter("color") int color,
            @Parameter("bgColor") int bgColor
    ) {
        upsertGlobal(HudWidget.graph(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, toFloatList(values), color, bgColor));
    }

    @Callback(name = "addPlayerGraph")
    public void addPlayerGraph(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x") double x,
            @Parameter("y") double y,
            @Parameter("width") double width,
            @Parameter("height") double height,
            @Parameter("values") List<Number> values,
            @Parameter("color") int color,
            @Parameter("bgColor") int bgColor
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.graph(id, Anchor.TOP_LEFT, (float) x, (float) y, (float) width, (float) height, toFloatList(values), color, bgColor));
    }

    @Callback(name = "addWorldLine")
    public void addWorldLine(
            @Parameter("id") String id,
            @Parameter("x1") double x1,
            @Parameter("y1") double y1,
            @Parameter("z1") double z1,
            @Parameter("x2") double x2,
            @Parameter("y2") double y2,
            @Parameter("z2") double z2,
            @Parameter("color") int color
    ) {
        upsertGlobal(HudWidget.worldLine(id, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, color));
    }

    @Callback(name = "addPlayerWorldLine")
    public void addPlayerWorldLine(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("x1") double x1,
            @Parameter("y1") double y1,
            @Parameter("z1") double z1,
            @Parameter("x2") double x2,
            @Parameter("y2") double y2,
            @Parameter("z2") double z2,
            @Parameter("color") int color
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.worldLine(id, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, color));
    }

    @Callback(name = "addEntityMarker")
    public void addEntityMarker(
            @Parameter("id") String id,
            @Parameter("entityUuid") String entityUuid,
            @Parameter("label") String label,
            @Parameter("color") int color
    ) {
        upsertGlobal(HudWidget.entityMarker(id, entityUuid, label, color));
    }

    @Callback(name = "addPlayerEntityMarker")
    public void addPlayerEntityMarker(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("entityUuid") String entityUuid,
            @Parameter("label") String label,
            @Parameter("color") int color
    ) {
        upsertPlayer(parseUuid(playerUuid), HudWidget.entityMarker(id, entityUuid, label, color));
    }

    @Callback(name = "setClickable")
    public boolean setClickable(@Parameter("id") String id, @Parameter("clickable") boolean clickable) {
        validateId(id);
        HudWidget existing = globalWidgets.get(id);
        if (existing == null) return false;
        upsertGlobal(existing.withClickable(clickable));
        return true;
    }

    @Callback(name = "setPlayerClickable")
    public boolean setPlayerClickable(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("id") String id,
            @Parameter("clickable") boolean clickable
    ) {
        UUID pid = parseUuid(playerUuid);
        validateId(id);
        Map<String, HudWidget> pMap = playerWidgets.get(pid);
        if (pMap == null) return false;
        HudWidget existing = pMap.get(id);
        if (existing == null) return false;
        upsertPlayer(pid, existing.withClickable(clickable));
        return true;
    }

    @Nullable
    @Callback(name = "getPlayerStats")
    public Map<String, Object> getPlayerStats(@Parameter("playerUuid") String playerUuid) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        UUID pid = parseUuid(playerUuid);
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(pid);
        if (player == null || !isPlayerConnected(player)) return null;

        ItemStack glasses = SmartGlassesItem.getEquippedGlasses(player);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("uuid", player.getUUID().toString());
        stats.put("name", player.getGameProfile().getName());
        stats.put("hp", player.getHealth());
        stats.put("maxHp", player.getMaxHealth());
        stats.put("food", player.getFoodData().getFoodLevel());
        stats.put("saturation", player.getFoodData().getSaturationLevel());
        stats.put("armor", player.getArmorValue());
        stats.put("air", player.getAirSupply());
        stats.put("maxAir", player.getMaxAirSupply());
        stats.put("xpLevel", player.experienceLevel);
        stats.put("yaw", player.getYRot());
        stats.put("pitch", player.getXRot());
        stats.put("x", player.getX());
        stats.put("y", player.getY());
        stats.put("z", player.getZ());
        stats.put("dimension", player.level().dimension().location().toString());
        stats.put("biome", player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("unknown"));
        stats.put("dayTime", player.level().getDayTime() % 24000L);
        stats.put("energy", SmartGlassesItem.getEnergy(glasses));
        stats.put("maxEnergy", Config.energyCapacity);
        return stats;
    }

    private static List<Float> toFloatList(@Nullable List<Number> values) {
        if (values == null || values.isEmpty()) return List.of();
        int limit = Math.min(values.size(), 256);
        List<Float> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Number n = values.get(i);
            out.add(n != null ? n.floatValue() : 0.0f);
        }
        return out;
    }

    @Nullable
    @Callback(name = "pollEvent")
    public Map<String, Object> pollEvent() {
        return eventQueue.poll();
    }

    @Callback(name = "getEvents")
    public List<Map<String, Object>> getEvents() {
        List<Map<String, Object>> drained = new ArrayList<>();
        Map<String, Object> ev;
        while ((ev = eventQueue.poll()) != null) {
            drained.add(ev);
        }
        return drained;
    }

    @Callback(name = "sendMessage")
    public boolean sendMessage(
            @Parameter("playerUuid") String playerUuid,
            @Parameter("message") String message
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        UUID pid = parseUuid(playerUuid);
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(pid);
        if (player == null || !isPlayerConnected(player)) return false;
        player.sendSystemMessage(Component.literal("[Glasses] " + message).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Callback(name = "broadcastMessage")
    public int broadcastMessage(@Parameter("message") String message) {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        int count = 0;
        Component text = Component.literal("[Glasses] " + message).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (isPlayerConnected(player)) {
                player.sendSystemMessage(text);
                count++;
            }
        }
        return count;
    }

    private void upsertGlobal(HudWidget widget) {
        validateId(widget.id());
        if (!globalWidgets.containsKey(widget.id()) && globalWidgets.size() >= MAX_WIDGETS_PER_LAYER) {
            throw new IllegalStateException("Widget limit reached (" + MAX_WIDGETS_PER_LAYER + ")");
        }
        globalWidgets.put(widget.id(), widget);
        globalDirtyRemoves.remove(widget.id());
        globalDirtyUpserts.add(widget.id());
    }

    private void upsertPlayer(UUID pid, HudWidget widget) {
        validateId(widget.id());
        Map<String, HudWidget> pMap = playerWidgets.computeIfAbsent(pid, k -> new LinkedHashMap<>());
        if (!pMap.containsKey(widget.id()) && pMap.size() >= MAX_WIDGETS_PER_LAYER) {
            throw new IllegalStateException("Player widget limit reached (" + MAX_WIDGETS_PER_LAYER + ")");
        }
        pMap.put(widget.id(), widget);
        LayerDiff diff = playerDiffs.computeIfAbsent(pid, k -> new LayerDiff());
        diff.removes.remove(widget.id());
        diff.upserts.add(widget.id());
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank() || id.length() > 128) {
            throw new IllegalArgumentException("Widget id must be non-empty and <= 128 characters");
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return UUID.fromString(raw.trim());
    }

    // =========================================================================
    // Event Dispatching (Server -> OC2R Linux)
    // =========================================================================

    public void handleChatCommand(ServerPlayer player, String command) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event", "glasses_chat_command");
        ev.put("playerUuid", player.getUUID().toString());
        ev.put("playerName", player.getGameProfile().getName());
        ev.put("command", command);
        enqueueAndPost(ev);
    }

    public void handleGaze(ServerPlayer player, GlassesGazePayload payload) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event", "glasses_gaze");
        ev.put("playerUuid", player.getUUID().toString());
        ev.put("playerName", player.getGameProfile().getName());
        ev.put("targetType", payload.targetType().name().toLowerCase(Locale.ROOT));
        ev.put("hitX", payload.hitX());
        ev.put("hitY", payload.hitY());
        ev.put("hitZ", payload.hitZ());

        if (payload.targetType() == GlassesGazePayload.TargetType.BLOCK) {
            ev.put("blockX", payload.blockPos().getX());
            ev.put("blockY", payload.blockPos().getY());
            ev.put("blockZ", payload.blockPos().getZ());
            ev.put("face", payload.face().getName());
        } else if (payload.targetType() == GlassesGazePayload.TargetType.ENTITY && level != null) {
            Entity entity = level.getEntity(payload.entityId());
            if (entity != null) {
                ev.put("entityUuid", entity.getUUID().toString());
                ev.put("entityName", entity.getName().getString());
            }
        }
        enqueueAndPost(ev);
    }

    public void handleHudClick(ServerPlayer player, GlassesHudClickPayload payload) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event", "glasses_hud_click");
        ev.put("playerUuid", player.getUUID().toString());
        ev.put("playerName", player.getGameProfile().getName());
        ev.put("widgetId", payload.widgetId());
        ev.put("button", payload.button());
        enqueueAndPost(ev);
    }

    private void emitPlayerEvent(String eventName, ServerPlayer player) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event", eventName);
        ev.put("playerUuid", player.getUUID().toString());
        ev.put("playerName", player.getGameProfile().getName());
        enqueueAndPost(ev);
    }

    private void enqueueAndPost(Map<String, Object> ev) {
        while (eventQueue.size() >= MAX_QUEUED_EVENTS) {
            eventQueue.poll();
        }
        eventQueue.offer(ev);

        if (!subscribers.isEmpty()) {
            JsonObject msg = new JsonObject();
            for (Map.Entry<String, Object> entry : ev.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Number num) {
                    msg.addProperty(entry.getKey(), num);
                } else if (val instanceof Boolean bool) {
                    msg.addProperty(entry.getKey(), bool);
                } else if (val != null) {
                    msg.addProperty(entry.getKey(), val.toString());
                }
            }
            postEvent(msg);
        }
    }

    private void postEvent(JsonObject msg) {
        for (Map.Entry<IEventSink, UUID> entry : subscribers.entrySet()) {
            entry.getKey().postEvent(entry.getValue(), msg);
        }
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
    public Collection<String> getDeviceTypeNames() {
        return List.of("glasses_bridge");
    }

    @Override
    public void getDeviceDocumentation(DeviceVisitor visitor) {
        visitor.visitCallback("getBridgeId")
                .description("Returns the unique UUID of this Glasses Bridge.");
        visitor.visitCallback("getConnectedPlayers")
                .description("Returns a list of players currently wearing Smart Glasses bound to this bridge.");
        visitor.visitCallback("clear")
                .description("Removes all global HUD and AR widgets.");
        visitor.visitCallback("addText")
                .description("Adds or updates a 2D text widget in virtual 1920x1080 space.");
        visitor.visitCallback("addRect")
                .description("Adds or updates a 2D rectangle widget in virtual 1920x1080 space.");
        visitor.visitCallback("addProgressBar")
                .description("Adds or updates a 2D progress bar widget in virtual 1920x1080 space.");
        visitor.visitCallback("addWorldMarker")
                .description("Adds or updates a 3D AR marker at world coordinates (x, y, z).");
        visitor.visitCallback("setAnchor")
                .description("Sets the screen anchor (TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT) of a widget.");
        visitor.visitCallback("removeWidget")
                .description("Removes a global widget by id.");
        visitor.visitCallback("pollEvent")
                .description("Returns and removes the next queued event (chat command, gaze, connect/disconnect), or null if empty.");
        visitor.visitCallback("getEvents")
                .description("Returns and clears all queued events.");
        visitor.visitCallback("sendMessage")
                .description("Sends a private chat message from the glasses to a connected player.");
        visitor.visitCallback("broadcastMessage")
                .description("Sends a chat message from the glasses to all connected players.");
    }
}
