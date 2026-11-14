package org.example.smartglasses.device;

import li.cil.oc2.api.bus.device.ItemDevice;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.common.bus.device.provider.util.AbstractItemDeviceProvider;
import org.example.smartglasses.item.PastebinCardItem;

import java.util.Optional;

public final class PastebinCardItemDeviceProvider extends AbstractItemDeviceProvider {
    public PastebinCardItemDeviceProvider() {
        super(PastebinCardItem.class);
    }

    @Override
    protected Optional<ItemDevice> getItemDevice(ItemDeviceQuery query) {
        return Optional.of(new PastebinCardItemDevice(query.getItemStack()));
    }

    @Override
    protected int getItemDeviceEnergyConsumption(ItemDeviceQuery query) {
        return 2;
    }
}
