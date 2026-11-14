package org.example.smartglasses;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.object.ObjectDevice;
import li.cil.oc2.api.bus.device.provider.ItemDeviceProvider;
import li.cil.oc2.common.bus.device.provider.Providers;
import li.cil.oc2.common.capabilities.Capabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.smartglasses.block.GlassesBridgeBlock;
import org.example.smartglasses.blockentity.BridgeRegistry;
import org.example.smartglasses.blockentity.GlassesBridgeBlockEntity;
import org.example.smartglasses.client.ClientHudState;
import org.example.smartglasses.device.PastebinCardItemDeviceProvider;
import org.example.smartglasses.device.WirelessCardItemDeviceProvider;
import org.example.smartglasses.device.WirelessNetworkRegistry;
import org.example.smartglasses.item.PastebinCardItem;
import org.example.smartglasses.item.SmartGlassesItem;
import org.example.smartglasses.item.WirelessCardItem;
import org.example.smartglasses.network.GlassesGazePayload;
import org.example.smartglasses.network.GlassesHudClickPayload;
import org.example.smartglasses.network.SyncHudPayload;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

@Mod(SmartGlasses.MODID)
public class SmartGlasses {
    public static final String MODID = "smartglasses";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHAT_COMMAND_PREFIX = "$$";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<ItemDeviceProvider> ITEM_DEVICE_PROVIDERS =
            DeferredRegister.create(Providers.itemDeviceProviderRegistry(), MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BRIDGE_ID =
            DATA_COMPONENTS.registerComponentType("bridge_id", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> WIRELESS_ADDRESS =
            DATA_COMPONENTS.registerComponentType("wireless_address", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredBlock<GlassesBridgeBlock> GLASSES_BRIDGE_BLOCK =
            BLOCKS.register("glasses_bridge", () -> new GlassesBridgeBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredItem<BlockItem> GLASSES_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem("glasses_bridge", GLASSES_BRIDGE_BLOCK);

    public static final DeferredItem<SmartGlassesItem> SMART_GLASSES_ITEM =
            ITEMS.register("smart_glasses", () -> new SmartGlassesItem(
                    new Item.Properties().stacksTo(1)
            ));

    public static final DeferredItem<WirelessCardItem> WIRELESS_CARD_ITEM =
            ITEMS.register("wireless_card", () -> new WirelessCardItem(
                    new Item.Properties().stacksTo(1)
            ));

    public static final DeferredItem<PastebinCardItem> PASTEBIN_CARD_ITEM =
            ITEMS.register("pastebin_card", () -> new PastebinCardItem(
                    new Item.Properties().stacksTo(1)
            ));

    public static final DeferredHolder<ItemDeviceProvider, WirelessCardItemDeviceProvider> WIRELESS_CARD_PROVIDER =
            ITEM_DEVICE_PROVIDERS.register("wireless_card", WirelessCardItemDeviceProvider::new);

    public static final DeferredHolder<ItemDeviceProvider, PastebinCardItemDeviceProvider> PASTEBIN_CARD_PROVIDER =
            ITEM_DEVICE_PROVIDERS.register("pastebin_card", PastebinCardItemDeviceProvider::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GlassesBridgeBlockEntity>> GLASSES_BRIDGE_BE =
            BLOCK_ENTITIES.register("glasses_bridge", () -> BlockEntityType.Builder.of(
                    GlassesBridgeBlockEntity::new,
                    GLASSES_BRIDGE_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMART_GLASSES_TAB =
            CREATIVE_MODE_TABS.register("smartglasses", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.smartglasses"))
                    .icon(() -> new ItemStack(SMART_GLASSES_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SMART_GLASSES_ITEM.get());
                        output.accept(GLASSES_BRIDGE_ITEM.get());
                        output.accept(WIRELESS_CARD_ITEM.get());
                        output.accept(PASTEBIN_CARD_ITEM.get());
                    })
                    .build());

    public SmartGlasses(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ITEM_DEVICE_PROVIDERS.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("Smart Glasses OC2R initialized");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        BlockCapability<Device, Direction> oc2rDeviceCap = BlockCapability.createSided(
                Capabilities.Device.BLOCK.getId(),
                Device.class
        );
        event.registerBlockEntity(
                oc2rDeviceCap,
                GLASSES_BRIDGE_BE.get(),
                (be, side) -> new ObjectDevice(be, List.of("glasses_bridge"))
        );
        event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> new SmartGlassesItem.EnergyStorage(stack),
                SMART_GLASSES_ITEM.get()
        );
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SyncHudPayload.TYPE,
                SyncHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHudState.apply(payload))
        );
        registrar.playToServer(
                GlassesGazePayload.TYPE,
                GlassesGazePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        UUID bridgeId = SmartGlassesItem.getBridgeId(SmartGlassesItem.getEquippedGlasses(player));
                        GlassesBridgeBlockEntity bridge = BridgeRegistry.get(bridgeId);
                        if (bridge != null) {
                            bridge.handleGaze(player, payload);
                        }
                    }
                })
        );
        registrar.playToServer(
                GlassesHudClickPayload.TYPE,
                GlassesHudClickPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        UUID bridgeId = SmartGlassesItem.getBridgeId(SmartGlassesItem.getEquippedGlasses(player));
                        GlassesBridgeBlockEntity bridge = BridgeRegistry.get(bridgeId);
                        if (bridge != null) {
                            bridge.handleHudClick(player, payload);
                        }
                    }
                })
        );
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String raw = event.getRawText();
        if (!raw.startsWith(CHAT_COMMAND_PREFIX)) return;

        ServerPlayer player = event.getPlayer();
        UUID bridgeId = SmartGlassesItem.getBridgeId(SmartGlassesItem.getEquippedGlasses(player));
        if (bridgeId == null) return;

        GlassesBridgeBlockEntity bridge = BridgeRegistry.get(bridgeId);
        if (bridge != null) {
            event.setCanceled(true);
            String command = raw.substring(CHAT_COMMAND_PREFIX.length()).trim();
            bridge.handleChatCommand(player, command);
            player.sendSystemMessage(
                    Component.literal("[Glasses] > " + command).withStyle(ChatFormatting.DARK_AQUA)
            );
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BridgeRegistry.clear();
        WirelessNetworkRegistry.clear();
    }
}
