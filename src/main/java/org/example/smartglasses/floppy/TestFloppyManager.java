package org.example.smartglasses.floppy;

import li.cil.oc2.common.blockentity.disk.DiskDriveBlockEntity;
import li.cil.oc2.common.serialization.BlobStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides preloaded OC2R floppy disks with test Lua programs in the creative tab
 * and materializes their ext2 filesystem images into OC2R's BlobStorage on drive insertion.
 */
public final class TestFloppyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFloppyManager.class);
    private static final String PRESET_TAG = "smartglasses_floppy";
    private static final ResourceLocation OC2R_FLOPPY_ID = ResourceLocation.fromNamespaceAndPath("oc2r", "floppy");

    public enum FloppyPreset {
        HUD("hud", 0x38BDF8),
        WIRELESS("wireless", 0x4ADE80),
        RADAR("radar", 0xFB923C),
        PASTEBIN("pastebin", 0xC084FC),
        ALL("all", 0xFACC15);

        private final String id;
        private final int color;

        FloppyPreset(String id, int color) {
            this.id = id;
            this.color = color;
        }

        public String id() {
            return id;
        }

        public int color() {
            return color;
        }

        public static Optional<FloppyPreset> byId(String id) {
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            String normalized = id.toLowerCase(Locale.ROOT);
            for (FloppyPreset preset : values()) {
                if (preset.id.equals(normalized)) {
                    return Optional.of(preset);
                }
            }
            return Optional.empty();
        }
    }

    private TestFloppyManager() {}

    public static ItemStack createFloppyStack(FloppyPreset preset) {
        Item floppyItem = BuiltInRegistries.ITEM.get(OC2R_FLOPPY_ID);
        if (floppyItem == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(floppyItem);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(preset.color(), true));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.smartglasses.floppy." + preset.id())
                        .withStyle(style -> style.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.smartglasses.floppy." + preset.id() + ".lore")
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withItalic(false)),
                Component.translatable("item.smartglasses.floppy.usage_hint")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .withStyle(style -> style.withItalic(false))
        )));

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PRESET_TAG, preset.id()));
        return stack;
    }

    public static byte[] buildImage(FloppyPreset preset) {
        Ext2ImageBuilder builder = new Ext2ImageBuilder();
        builder.addFile("README.txt", buildReadme(preset));
        switch (preset) {
            case HUD -> {
                builder.addFile("test_hud.lua", TEST_HUD_LUA);
                builder.addFile("test_ar.lua", TEST_AR_LUA);
            }
            case WIRELESS -> {
                builder.addFile("test_wireless_tx.lua", TEST_WIRELESS_TX_LUA);
                builder.addFile("test_wireless_rx.lua", TEST_WIRELESS_RX_LUA);
            }
            case RADAR -> builder.addFile("test_radar.lua", TEST_RADAR_LUA);
            case PASTEBIN -> {
                builder.addFile("test_pastebin.lua", TEST_PASTEBIN_LUA);
                builder.addFile("test_github.lua", TEST_GITHUB_LUA);
            }
            case ALL -> {
                builder.addFile("test_hud.lua", TEST_HUD_LUA);
                builder.addFile("test_ar.lua", TEST_AR_LUA);
                builder.addFile("test_wireless_tx.lua", TEST_WIRELESS_TX_LUA);
                builder.addFile("test_wireless_rx.lua", TEST_WIRELESS_RX_LUA);
                builder.addFile("test_radar.lua", TEST_RADAR_LUA);
                builder.addFile("test_pastebin.lua", TEST_PASTEBIN_LUA);
                builder.addFile("test_github.lua", TEST_GITHUB_LUA);
            }
        }
        return builder.build();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof DiskDriveBlockEntity)) {
            return;
        }
        Optional<FloppyPreset> presetOpt = getPreset(stack);
        if (presetOpt.isEmpty() || hasBlobHandle(stack)) {
            return;
        }
        initializeBlob(stack, presetOpt.get());
    }

    private static Optional<FloppyPreset> getPreset(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(PRESET_TAG) ? FloppyPreset.byId(tag.getString(PRESET_TAG)) : Optional.empty();
    }

    private static boolean hasBlobHandle(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("oc2r")) {
            return false;
        }
        CompoundTag oc2r = tag.getCompound("oc2r");
        if (!oc2r.contains("data")) {
            return false;
        }
        return oc2r.getCompound("data").hasUUID("blob");
    }

    private static void initializeBlob(ItemStack stack, FloppyPreset preset) {
        UUID handle = BlobStorage.allocateHandle();
        byte[] image = buildImage(preset);
        try {
            FileChannel channel = BlobStorage.getOrOpen(handle);
            ByteBuffer buffer = ByteBuffer.wrap(image);
            channel.position(0);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
            BlobStorage.close(handle);

            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                CompoundTag oc2r = tag.contains("oc2r") ? tag.getCompound("oc2r") : new CompoundTag();
                CompoundTag data = oc2r.contains("data") ? oc2r.getCompound("data") : new CompoundTag();
                data.putUUID("blob", handle);
                oc2r.put("data", data);
                tag.put("oc2r", oc2r);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to initialize preloaded floppy blob for preset {}", preset.id(), e);
            BlobStorage.delete(handle);
        }
    }

    private static String buildReadme(FloppyPreset preset) {
        return "Smart Glasses OC2R - Test Floppy (" + preset.id() + ")\n"
                + "============================================\n"
                + "Mount inside OC2R Linux:\n"
                + "  mkdir -p /mnt/floppy\n"
                + "  mount /dev/vda /mnt/floppy   (or /dev/vdb if you have a hard drive)\n"
                + "  cd /mnt/floppy && ls -la\n"
                + "\n"
                + "Run any script with:\n"
                + "  lua test_hud.lua\n"
                + "\n"
                + "Remember to unmount before ejecting:\n"
                + "  cd / && umount /mnt/floppy\n";
    }

    private static final String TEST_HUD_LUA = """
            local devices = require("devices")
            local bridge = assert(devices:find("glasses_bridge"), "glasses_bridge not found")

            print("Bridge ID: " .. tostring(bridge:getBridgeId()))
            bridge:clear()

            bridge:addRect("bg", 10, 10, 175, 62, 0x99111827)
            bridge:addText("title", 18, 16, "Smart Glasses HUD Test", 0xFF38BDF8, 1.0)
            bridge:addProgressBar("hp", 18, 32, 155, 10, 75, 100, 0xFF22C55E, 0xFF374151)
            bridge:addRect("btn", 18, 48, 85, 16, 0xFF2563EB)
            bridge:addText("btn_lbl", 26, 52, "Click Me!", 0xFFFFFFFF, 1.0)
            bridge:setClickable("btn", true)

            bridge:broadcastMessage("HUD test loaded! Press H to click, $$ in chat (Tier 2+).")
            print("Players connected: " .. tostring(#bridge:getConnectedPlayers()))
            print("Polling HUD/chat events for 15 seconds...")

            local deadline = os.time() + 15
            while os.time() < deadline do
              local ev = bridge:pollEvent()
              if ev then
                local detail = ev.widgetId or ev.command or ev.targetType or ""
                print("Event:", ev.event, ev.playerName, detail)
                if ev.event == "glasses_hud_click" and ev.widgetId == "btn" then
                  bridge:addText("title", 18, 16, "Clicked by " .. tostring(ev.playerName) .. "!", 0xFFFACC15, 1.0)
                elseif ev.event == "glasses_chat_command" then
                  bridge:broadcastMessage("Echo: " .. tostring(ev.command))
                elseif ev.event == "glasses_gaze" then
                  bridge:broadcastMessage("Gaze: " .. tostring(ev.targetType) .. " " .. tostring(ev.entityName or ""))
                end
              end
              os.execute("sleep 0.2")
            end
            print("Done.")
            """;

    private static final String TEST_AR_LUA = """
            local devices = require("devices")
            local bridge = assert(devices:find("glasses_bridge"), "glasses_bridge not found")

            local players = bridge:getConnectedPlayers()
            if #players == 0 then
              print("No players connected to this Glasses Bridge yet! Right-click bridge with Tier 3 glasses.")
              return
            end

            local p = players[1]
            local stats = bridge:getPlayerStats(p.uuid)
            if not stats then
              print("Could not read player stats")
              return
            end

            local x, y, z = math.floor(stats.x), math.floor(stats.y) + 2, math.floor(stats.z)
            bridge:addWorldMarker("ar_wp", x, y, z, "Bridge Beacon", 0xFF38BDF8)
            bridge:addWorldLine("ar_beam", x, y - 2, z, x, y + 6, z, 0xFF4ADE80)
            bridge:addEntityMarker("ar_self", p.uuid, p.name, 0xFFFACC15)

            print(string.format("Spawned AR marker & beam at (%d, %d, %d). Press G with Tier 3 glasses!", x, y, z))
            """;

    private static final String TEST_WIRELESS_TX_LUA = """
            local devices = require("devices")
            local card = assert(devices:find("wireless_card"), "wireless_card not found")

            local port = 42
            card:open(port)
            card:setStrength(128)
            print("Local address: " .. tostring(card:getAddress()))

            local delivered = card:broadcast(port, "Hello from " .. tostring(card:getAddress()):sub(1, 8))
            print("Broadcast on port " .. port .. " delivered to " .. tostring(delivered) .. " card(s).")

            if arg and arg[1] then
              local ok = card:send(arg[1], port, "Direct ping!")
              print("Direct send to " .. arg[1] .. ": " .. tostring(ok))
            else
              print("Tip: pass a target address or prefix: lua test_wireless_tx.lua <prefix>")
            end
            """;

    private static final String TEST_WIRELESS_RX_LUA = """
            local devices = require("devices")
            local card = assert(devices:find("wireless_card"), "wireless_card not found")

            local port = 42
            card:open(port)
            print("Listening on port " .. port)
            print("Card address: " .. tostring(card:getAddress()))
            print("Short prefix: " .. tostring(card:getAddress()):sub(1, 8))
            print("Waiting for messages (Ctrl+C to stop)...")

            while true do
              local msg = card:pollMessage()
              if msg then
                print(string.format("[%s -> port %s | dist=%.1f] %s",
                  tostring(msg.from):sub(1, 8),
                  tostring(msg.port),
                  tonumber(msg.distance or 0),
                  tostring(msg.payload)))
              end
              os.execute("sleep 0.2")
            end
            """;

    private static final String TEST_RADAR_LUA = """
            local devices = require("devices")
            local radar = assert(devices:find("radar"), "radar block not found")

            local tier = radar:getTier()
            local maxRange = radar:getMaxRange()
            print(string.format("Radar Tier %d (max range: %.0f blocks)", tier, maxRange))
            print("Capabilities: "
              .. (tier >= 1 and "[T1: type/dist/uuid <=64] " or "")
              .. (tier >= 2 and "[T2: hp/maxHp <=128] " or "")
              .. (tier >= 3 and "[T3: name/isPlayer/xyz <=256]" or ""))

            local hits = radar:scan(maxRange)
            print("Entities detected: " .. tostring(#hits))
            local withHp, withCoords, players = 0, 0, 0
            for i, e in ipairs(hits) do
              local label = e.name or e.type or e.uuid
              local line = string.format("%2d. %-22s dist=%5.1f", i, tostring(label), tonumber(e.distance or 0))
              if e.hp ~= nil then
                withHp = withHp + 1
                line = line .. string.format(" HP=%.1f/%.1f", e.hp, e.maxHp or 0)
              end
              if e.x ~= nil and e.y ~= nil and e.z ~= nil then
                withCoords = withCoords + 1
                line = line .. string.format(" (%.1f, %.1f, %.1f)", e.x, e.y, e.z)
              end
              if e.isPlayer then
                players = players + 1
                line = line .. " [PLAYER]"
              end
              print(line)
            end

            print(string.format("Summary: total=%d, withHP=%d, withCoords=%d, players=%d", #hits, withHp, withCoords, players))
            if tier >= 2 and #hits > 0 and withHp == 0 then
              print("WARN: Tier >= 2 expected hp/maxHp on living entities")
            end
            if tier >= 3 and #hits > 0 and withCoords == 0 then
              print("WARN: Tier 3 expected x/y/z coordinates")
            end
            """;

    private static final String TEST_PASTEBIN_LUA = """
            local devices = require("devices")
            local card = assert(devices:find("pastebin"), "pastebin card not found")

            local pasteId = (arg and arg[1]) or "https://pastebin.com/raw/2s6kR8p1"
            print("Fetching Pastebin: " .. pasteId)
            local ok, res = pcall(function() return card:fetchPastebin(pasteId) end)
            if ok then
              print("Success (" .. #res .. " bytes):")
              print(res:sub(1, 400))
            else
              print("Error: " .. tostring(res))
            end
            """;

    private static final String TEST_GITHUB_LUA = """
            local devices = require("devices")
            local card = assert(devices:find("pastebin"), "pastebin card not found")

            print("Fetching GitHub README from Frontline-and-Two-Engineers/smart-glasses-oc2r...")
            local ok, res = pcall(function()
              return card:fetchGitHub("Frontline-and-Two-Engineers", "smart-glasses-oc2r", "main", "README.md")
            end)
            if ok then
              print("Success (" .. #res .. " bytes):")
              print(res:sub(1, 500))
            else
              print("Error: " .. tostring(res))
            end
            """;
}
