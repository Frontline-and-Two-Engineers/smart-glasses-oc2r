package org.example.smartglasses.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.example.smartglasses.Config;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.device.WirelessNetworkRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * BlockEntity for the FE-powered Wireless Repeater.
 */
public class WirelessRepeaterBlockEntity extends BlockEntity implements WirelessNetworkRegistry.WirelessRepeater {
    private static final String TAG_ENERGY = "energy";

    private final UUID repeaterId = UUID.randomUUID();
    private final RepeaterEnergyStorage energyStorage = new RepeaterEnergyStorage();

    public WirelessRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(SmartGlasses.WIRELESS_REPEATER_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            WirelessNetworkRegistry.registerRepeater(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            WirelessNetworkRegistry.unregisterRepeater(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ENERGY)) {
            energyStorage.setEnergy(tag.getInt(TAG_ENERGY));
        }
    }

    @Override
    public UUID getRepeaterId() {
        return repeaterId;
    }

    @Nullable
    @Override
    public ResourceKey<Level> getDimension() {
        return level != null ? level.dimension() : null;
    }

    @Nullable
    @Override
    public Vec3 getPosition() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public double getRelayRange() {
        return Config.repeaterRange;
    }

    @Override
    public boolean tryConsumeRelayEnergy() {
        int cost = Config.repeaterEnergyPerPacket;
        if (cost <= 0) return true;
        if (energyStorage.getEnergyStored() < cost) return false;
        energyStorage.extractInternal(cost);
        setChanged();
        return true;
    }

    private static final class RepeaterEnergyStorage extends EnergyStorage {
        RepeaterEnergyStorage() {
            super(Config.repeaterEnergyCapacity, Math.max(1000, Config.repeaterEnergyCapacity / 20), 0);
        }

        void setEnergy(int value) {
            this.energy = Math.clamp(value, 0, this.capacity);
        }

        void extractInternal(int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }
    }
}
