package org.example.smartglasses;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = SmartGlasses.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue MAX_RANGE = BUILDER
            .comment("Maximum range (in blocks) from a Glasses Bridge to connected Smart Glasses. 0 = unlimited.")
            .defineInRange("maxRange", 256, 0, 100000);

    private static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION = BUILDER
            .comment("Whether Smart Glasses remain connected across dimensions.")
            .define("allowCrossDimension", false);

    private static final ModConfigSpec.BooleanValue REQUIRE_ENERGY = BUILDER
            .comment("Whether Smart Glasses require FE energy to display HUD/AR widgets.")
            .define("requireEnergy", false);

    private static final ModConfigSpec.IntValue ENERGY_CAPACITY = BUILDER
            .comment("Maximum FE energy capacity of Smart Glasses.")
            .defineInRange("energyCapacity", 100000, 1000, 100000000);

    private static final ModConfigSpec.IntValue ENERGY_PER_TICK = BUILDER
            .comment("FE energy consumed per tick while connected to a bridge (when requireEnergy is true).")
            .defineInRange("energyPerTick", 5, 0, 10000);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int maxRange = 256;
    public static boolean allowCrossDimension = false;
    public static boolean requireEnergy = false;
    public static int energyCapacity = 100000;
    public static int energyPerTick = 5;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxRange = MAX_RANGE.get();
        allowCrossDimension = ALLOW_CROSS_DIMENSION.get();
        requireEnergy = REQUIRE_ENERGY.get();
        energyCapacity = ENERGY_CAPACITY.get();
        energyPerTick = ENERGY_PER_TICK.get();
    }
}
