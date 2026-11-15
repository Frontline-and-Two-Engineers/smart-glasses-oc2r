package org.example.smartglasses.blockentity;

import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.NamedDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.smartglasses.Config;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.block.RadarBlock;

import java.util.*;

/**
 * OC2R Radar peripheral with 3 tiers:
 * Tier 1: entities + 64 blocks
 * Tier 2: entities + HP + 128 blocks
 * Tier 3: entities + HP + player names + coordinates + 256 blocks
 */
@SuppressWarnings("unused")
public class RadarBlockEntity extends BlockEntity implements NamedDevice, DocumentedDevice {
    private static final int MAX_RESULTS = 128;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(SmartGlasses.RADAR_BE.get(), pos, state);
    }

    public int getTier() {
        if (getBlockState().getBlock() instanceof RadarBlock rb) {
            return rb.getTier();
        }
        return 1;
    }

    public double getMaxRangeValue() {
        return switch (getTier()) {
            case 2 -> 128.0;
            case 3 -> 256.0;
            default -> 64.0;
        };
    }

    @Callback(name = "getTier", synchronize = false)
    public int getTierCallback() {
        return getTier();
    }

    @Callback(name = "getMaxRange", synchronize = false)
    public double getMaxRangeCallback() {
        return getMaxRangeValue();
    }

    @Callback(name = "isRelativeCoordinates", synchronize = false)
    public boolean isRelativeCoordinates() {
        return Config.relativeCoordinates;
    }

    @Callback(name = "scan")
    public List<Map<String, Object>> scan(@Parameter("radius") double radius) {
        if (level == null || level.isClientSide) return List.of();

        int tier = getTier();
        double maxRange = getMaxRangeValue();
        double r = Double.isFinite(radius) && radius > 0 ? Math.min(radius, maxRange) : maxRange;
        double rSq = r * r;

        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB box = new AABB(worldPosition).inflate(r);
        List<Entity> found = level.getEntitiesOfClass(Entity.class, box, e -> !e.isRemoved());

        List<EntityDist> sorted = new ArrayList<>();
        for (Entity e : found) {
            double distSq = e.position().distanceToSqr(center);
            if (distSq <= rSq) {
                sorted.add(new EntityDist(e, Math.sqrt(distSq)));
            }
        }
        sorted.sort(Comparator.comparingDouble(ed -> ed.dist));

        int limit = Math.min(sorted.size(), MAX_RESULTS);
        List<Map<String, Object>> results = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Entity e = sorted.get(i).entity;
            double dist = sorted.get(i).dist;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", e.getUUID().toString());
            entry.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            entry.put("distance", dist);

            if (tier >= 2 && e instanceof LivingEntity living) {
                entry.put("hp", living.getHealth());
                entry.put("maxHp", living.getMaxHealth());
            }

            if (tier >= 3) {
                if (e instanceof Player player) {
                    entry.put("isPlayer", true);
                    entry.put("name", player.getGameProfile().getName());
                } else {
                    entry.put("isPlayer", false);
                    entry.put("name", e.getName().getString());
                }
                double ox = Config.relativeCoordinates ? e.getX() - worldPosition.getX() : e.getX();
                double oy = Config.relativeCoordinates ? e.getY() - worldPosition.getY() : e.getY();
                double oz = Config.relativeCoordinates ? e.getZ() - worldPosition.getZ() : e.getZ();
                entry.put("x", ox);
                entry.put("y", oy);
                entry.put("z", oz);
            }

            results.add(entry);
        }
        return results;
    }

    private record EntityDist(Entity entity, double dist) {
    }

    @Override
    public Collection<String> getDeviceTypeNames() {
        return List.of("radar");
    }

    @Override
    public void getDeviceDocumentation(DeviceVisitor visitor) {
        visitor.visitCallback("getTier")
                .description("Returns the radar tier (1, 2, or 3).");
        visitor.visitCallback("getMaxRange")
                .description("Returns the maximum scan radius in blocks (64, 128, or 256).");
        visitor.visitCallback("scan")
                .description("Scans entities within radius (clamped to tier max). Tier 1: uuid/type/distance; Tier 2: +hp/maxHp; Tier 3: +name/isPlayer/x/y/z.");
        visitor.visitCallback("isRelativeCoordinates")
                .description("Returns true if entity coordinates are relative to the radar block.");
    }
}
