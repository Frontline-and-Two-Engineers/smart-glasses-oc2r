package org.example.smartglasses.device;

import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.common.bus.device.rpc.item.AbstractItemRPCDevice;
import net.minecraft.world.item.ItemStack;
import org.example.smartglasses.Config;

import java.net.URI;

/**
 * Safe, allowlisted Pastebin & GitHub HTTPS fetcher device for OC2R.
 */
@SuppressWarnings("unused")
public class PastebinCardItemDevice extends AbstractItemRPCDevice implements DocumentedDevice {
    private long lastRequestTimeMs = 0L;

    public PastebinCardItemDevice(ItemStack identity) {
        super(identity, "pastebin");
    }

    @Callback(name = "fetchPastebin", synchronize = false)
    public String fetchPastebin(@Parameter("idOrUrl") String idOrUrl) {
        return executeFetch(SafeCodeFetcher.resolvePastebinUri(idOrUrl));
    }

    @Callback(name = "fetchGitHub", synchronize = false)
    public String fetchGitHub(
            @Parameter("user") String user,
            @Parameter("repo") String repo,
            @Parameter("branch") String branch,
            @Parameter("path") String path
    ) {
        return executeFetch(SafeCodeFetcher.resolveGitHubUri(user, repo, branch, path));
    }

    @Callback(name = "fetchUrl", synchronize = false)
    public String fetchUrl(@Parameter("url") String url) {
        return executeFetch(SafeCodeFetcher.validateAllowedUri(url));
    }

    private synchronized String executeFetch(URI uri) {
        if (!Config.enablePastebinCard) {
            throw new IllegalStateException("Pastebin & GitHub Card is disabled in server config");
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTimeMs;
        if (elapsed < Config.pastebinCooldownMs) {
            long waitMs = Config.pastebinCooldownMs - elapsed;
            throw new IllegalStateException("Rate limited; retry in " + waitMs + "ms");
        }
        lastRequestTimeMs = now;

        try {
            return SafeCodeFetcher.fetch(uri, Config.pastebinMaxBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Fetch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void getDeviceDocumentation(DeviceVisitor visitor) {
        visitor.visitCallback("fetchPastebin")
                .description("Safely downloads raw text from pastebin.com by paste ID or URL.");
        visitor.visitCallback("fetchGitHub")
                .description("Safely downloads a raw file from raw.githubusercontent.com/<user>/<repo>/<branch>/<path>.");
        visitor.visitCallback("fetchUrl")
                .description("Safely downloads an HTTPS URL strictly from pastebin.com/raw/, raw.githubusercontent.com, or gist.githubusercontent.com.");
    }
}
