package org.example.smartglasses;

import org.example.smartglasses.device.SafeCodeFetcher;
import org.example.smartglasses.device.WirelessNetworkRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CardsSecurityAndWirelessTest {

    @AfterEach
    void cleanup() {
        WirelessNetworkRegistry.clear();
    }

    @Test
    void safeCodeFetcherAllowsValidPastebinAndGithub() {
        URI p1 = SafeCodeFetcher.resolvePastebinUri("AbCd1234");
        assertEquals("https://pastebin.com/raw/AbCd1234", p1.toString());

        URI p2 = SafeCodeFetcher.resolvePastebinUri("https://pastebin.com/AbCd1234");
        assertEquals("https://pastebin.com/raw/AbCd1234", p2.toString());

        URI g1 = SafeCodeFetcher.resolveGitHubUri("Frontline-and-Two-Engineers", "smart-glasses-oc2r", "main", "docs/README.md");
        assertEquals("https://raw.githubusercontent.com/Frontline-and-Two-Engineers/smart-glasses-oc2r/main/docs/README.md", g1.toString());

        URI g2 = SafeCodeFetcher.validateAllowedUri("https://gist.githubusercontent.com/user/12345/raw/test.lua");
        assertEquals("gist.githubusercontent.com", g2.getHost());
    }

    @Test
    void safeCodeFetcherBlocksSsrfAndInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("http://pastebin.com/raw/AbCd1234"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("https://127.0.0.1/secret"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("https://localhost:8080/admin"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("https://169.254.169.254/latest/meta-data/"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("https://evil.com/payload.lua"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.validateAllowedUri("https://pastebin.com/AbCd1234"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.resolveGitHubUri("user", "repo", "main", "../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> SafeCodeFetcher.resolvePastebinUri("../etc/passwd"));
    }

    @Test
    void wirelessRegistryDeliversWithinRangeAndOpenPortsOnly() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();

        List<String> receivedB = new ArrayList<>();
        List<String> receivedC = new ArrayList<>();

        WirelessNetworkRegistry.WirelessEndpoint a = fakeEndpoint(aId, Level.OVERWORLD, new Vec3(0, 64, 0), Set.of(10), (f, p, d, m) -> {});
        WirelessNetworkRegistry.WirelessEndpoint b = fakeEndpoint(bId, Level.OVERWORLD, new Vec3(50, 64, 0), Set.of(10), (f, p, d, m) -> receivedB.add(m));
        WirelessNetworkRegistry.WirelessEndpoint c = fakeEndpoint(cId, Level.OVERWORLD, new Vec3(500, 64, 0), Set.of(10), (f, p, d, m) -> receivedC.add(m));

        WirelessNetworkRegistry.register(a);
        WirelessNetworkRegistry.register(b);
        WirelessNetworkRegistry.register(c);

        int count = WirelessNetworkRegistry.broadcast(a, 10, 100.0, "hello");
        assertEquals(1, count);
        assertEquals(List.of("hello"), receivedB);
        assertTrue(receivedC.isEmpty());

        assertFalse(WirelessNetworkRegistry.send(a, bId, 99, 100.0, "closed-port"));
        assertTrue(WirelessNetworkRegistry.send(a, bId, 10, 100.0, "direct"));
        assertEquals(List.of("hello", "direct"), receivedB);
    }

    private interface Receiver {
        void onMsg(UUID from, int port, double dist, String msg);
    }

    private static WirelessNetworkRegistry.WirelessEndpoint fakeEndpoint(
            UUID id, ResourceKey<Level> dim, Vec3 pos, Set<Integer> ports, Receiver rx
    ) {
        return new WirelessNetworkRegistry.WirelessEndpoint() {
            @Override public UUID getAddress() { return id; }
            @Override public ResourceKey<Level> getDimension() { return dim; }
            @Override public Vec3 getPosition() { return pos; }
            @Override public boolean isPortOpen(int port) { return ports.contains(port); }
            @Override public void receiveWirelessPacket(UUID from, int port, double distance, String payload) {
                rx.onMsg(from, port, distance, payload);
            }
        };
    }
}
