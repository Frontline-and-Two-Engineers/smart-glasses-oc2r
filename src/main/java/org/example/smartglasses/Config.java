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

    private static final ModConfigSpec.BooleanValue RELATIVE_COORDINATES = BUILDER
            .comment("When true, all coordinates exposed by Glasses Bridge and Radar are relative to the device block instead of absolute world coordinates.")
            .define("relativeCoordinates", true);

    private static final ModConfigSpec.BooleanValue REQUIRE_ENERGY = BUILDER
            .comment("Whether Smart Glasses require FE energy to display HUD/AR widgets.")
            .define("requireEnergy", false);

    private static final ModConfigSpec.IntValue ENERGY_CAPACITY = BUILDER
            .comment("Maximum FE energy capacity of Smart Glasses.")
            .defineInRange("energyCapacity", 100000, 1000, 100000000);

    private static final ModConfigSpec.IntValue ENERGY_PER_TICK = BUILDER
            .comment("FE energy consumed per tick while connected to a bridge (when requireEnergy is true).")
            .defineInRange("energyPerTick", 5, 0, 10000);

    private static final ModConfigSpec.IntValue WIRELESS_MAX_RANGE = BUILDER
            .comment("Maximum range (in blocks) for Wireless Network Cards.")
            .defineInRange("wirelessMaxRange", 512, 16, 100000);

    private static final ModConfigSpec.IntValue REPEATER_RANGE = BUILDER
            .comment("Relay range (in blocks) for Wireless Repeaters.")
            .defineInRange("repeaterRange", 512, 16, 100000);

    private static final ModConfigSpec.IntValue REPEATER_ENERGY_CAPACITY = BUILDER
            .comment("FE energy capacity of a Wireless Repeater.")
            .defineInRange("repeaterEnergyCapacity", 50000, 1000, 100000000);

    private static final ModConfigSpec.IntValue REPEATER_ENERGY_PER_PACKET = BUILDER
            .comment("FE energy consumed by a Wireless Repeater per relayed packet.")
            .defineInRange("repeaterEnergyPerPacket", 25, 0, 100000);

    private static final ModConfigSpec.BooleanValue ENABLE_PASTEBIN_CARD = BUILDER
            .comment("Whether the Pastebin & GitHub Card is allowed to make outbound HTTPS requests.")
            .define("enablePastebinCard", true);

    private static final ModConfigSpec.IntValue PASTEBIN_MAX_BYTES = BUILDER
            .comment("Maximum response size (in bytes) for Pastebin & GitHub Card downloads.")
            .defineInRange("pastebinMaxBytes", 262144, 1024, 2097152);

    private static final ModConfigSpec.IntValue PASTEBIN_COOLDOWN_MS = BUILDER
            .comment("Minimum cooldown (in milliseconds) between requests on a Pastebin & GitHub Card.")
            .defineInRange("pastebinCooldownMs", 2000, 200, 60000);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int maxRange = 256;
    public static boolean allowCrossDimension = false;
    public static boolean relativeCoordinates = true;
    public static boolean requireEnergy = false;
    public static int energyCapacity = 100000;
    public static int energyPerTick = 5;

    public static int wirelessMaxRange = 512;
    public static int repeaterRange = 512;
    public static int repeaterEnergyCapacity = 50000;
    public static int repeaterEnergyPerPacket = 25;
    public static boolean enablePastebinCard = true;
    public static int pastebinMaxBytes = 262144;
    public static int pastebinCooldownMs = 2000;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxRange = MAX_RANGE.get();
        allowCrossDimension = ALLOW_CROSS_DIMENSION.get();
        relativeCoordinates = RELATIVE_COORDINATES.get();
        requireEnergy = REQUIRE_ENERGY.get();
        energyCapacity = ENERGY_CAPACITY.get();
        energyPerTick = ENERGY_PER_TICK.get();
        wirelessMaxRange = WIRELESS_MAX_RANGE.get();
        repeaterRange = REPEATER_RANGE.get();
        repeaterEnergyCapacity = REPEATER_ENERGY_CAPACITY.get();
        repeaterEnergyPerPacket = REPEATER_ENERGY_PER_PACKET.get();
        enablePastebinCard = ENABLE_PASTEBIN_CARD.get();
        pastebinMaxBytes = PASTEBIN_MAX_BYTES.get();
        pastebinCooldownMs = PASTEBIN_COOLDOWN_MS.get();
    }
}
