package org.example.smartglasses.device;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hardened HTTPS fetcher restricted strictly to Pastebin and GitHub raw content.
 * Blocks SSRF (private/loopback IPs), redirects, non-HTTPS protocols, and oversized payloads.
 */
public final class SafeCodeFetcher {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "pastebin.com",
            "raw.githubusercontent.com",
            "gist.githubusercontent.com"
    );

    private static final Pattern PASTEBIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,16}$");
    private static final Pattern GITHUB_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private SafeCodeFetcher() {
    }

    public static URI resolvePastebinUri(String idOrUrl) {
        if (idOrUrl == null || idOrUrl.isBlank()) {
            throw new IllegalArgumentException("Pastebin ID or URL cannot be empty");
        }
        String trimmed = idOrUrl.trim();
        String id;
        if (trimmed.startsWith("https://pastebin.com/raw/")) {
            id = trimmed.substring("https://pastebin.com/raw/".length());
        } else if (trimmed.startsWith("https://pastebin.com/")) {
            id = trimmed.substring("https://pastebin.com/".length());
        } else {
            id = trimmed;
        }
        if (!PASTEBIN_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid Pastebin ID: " + id);
        }
        return URI.create("https://pastebin.com/raw/" + id);
    }

    public static URI resolveGitHubUri(String user, String repo, String branch, String path) {
        validateSegment(user, "user");
        validateSegment(repo, "repo");
        validateSegment(branch, "branch");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }
        String cleanPath = path.trim();
        while (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.isEmpty() || cleanPath.contains("..") || cleanPath.contains("?") || cleanPath.contains("#") || cleanPath.contains("\\")) {
            throw new IllegalArgumentException("Invalid GitHub file path");
        }
        return URI.create("https://raw.githubusercontent.com/" + user.trim() + "/" + repo.trim() + "/" + branch.trim() + "/" + cleanPath);
    }

    public static URI validateAllowedUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only HTTPS URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL userinfo is not allowed");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Only standard HTTPS port 443 is allowed");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Missing host");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(lowerHost)) {
            throw new IllegalArgumentException("Host not allowed: " + lowerHost + " (allowed: pastebin.com, raw.githubusercontent.com, gist.githubusercontent.com)");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || path.contains("..")) {
            throw new IllegalArgumentException("Invalid URL path");
        }
        if (lowerHost.equals("pastebin.com")) {
            if (!path.startsWith("/raw/")) {
                throw new IllegalArgumentException("Pastebin URLs must use /raw/<id>");
            }
            String id = path.substring("/raw/".length());
            if (!PASTEBIN_ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid Pastebin ID in URL");
            }
        }
        return uri;
    }

    public static void verifyPublicAddress(String host) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IOException("Host did not resolve: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                throw new SecurityException("Blocked non-public IP resolution for host: " + host);
            }
        }
    }

    public static String fetch(URI uri, int maxBytes) throws IOException, InterruptedException {
        URI validated = validateAllowedUri(uri.toString());
        verifyPublicAddress(validated.getHost());

        HttpRequest request = HttpRequest.newBuilder(validated)
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "SmartGlasses-OC2R/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP error " + status);
            }
            byte[] buf = new byte[Math.min(maxBytes + 1, 8192)];
            byte[] out = new byte[maxBytes];
            int total = 0;
            int read;
            while ((read = in.read(buf, 0, Math.min(buf.length, maxBytes - total + 1))) != -1) {
                if (total + read > maxBytes) {
                    throw new IOException("Response exceeds maximum allowed size (" + maxBytes + " bytes)");
                }
                System.arraycopy(buf, 0, out, total, read);
                total += read;
            }
            return new String(out, 0, total, StandardCharsets.UTF_8);
        }
    }

    private static void validateSegment(String value, String name) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..") || !GITHUB_SEGMENT_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid GitHub " + name);
        }
    }
}
