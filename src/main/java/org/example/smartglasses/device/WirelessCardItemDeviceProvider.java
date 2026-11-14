package org.example.smartglasses.device;

import li.cil.oc2.api.bus.device.ItemDevice;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.common.bus.device.provider.util.AbstractItemDeviceProvider;
import org.example.smartglasses.item.WirelessCardItem;

import java.util.Optional;

public final class WirelessCardItemDeviceProvider extends AbstractItemDeviceProvider {
    public WirelessCardItemDeviceProvider() {
        super(WirelessCardItem.class);
    }

    @Override
    protected Optional<ItemDevice> getItemDevice(ItemDeviceQuery query) {
        return Optional.of(new WirelessCardItemDevice(query.getItemStack(), query));
    }

    @Override
    protected int getItemDeviceEnergyConsumption(ItemDeviceQuery query) {
        return 4;
    }
}
