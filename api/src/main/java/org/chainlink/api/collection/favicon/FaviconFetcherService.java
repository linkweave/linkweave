package org.chainlink.api.collection.favicon;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.chainlink.infrastructure.stereotypes.Service;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@Slf4j
public class FaviconFetcherService {

    @ConfigProperty(name = "chainlink.favicon.timeout", defaultValue = "5S")
    Duration timeout;

    @ConfigProperty(name = "chainlink.favicon.max-bytes", defaultValue = "262144")
    int maxBytes;

    @ConfigProperty(name = "chainlink.favicon.max-redirects", defaultValue = "3")
    int maxRedirects;

    public @NonNull Optional<FetchedFavicon> fetchFor(@NonNull URL bookmarkUrl) {
        try {
            URI faviconUri = URI.create(canonicalOrigin(bookmarkUrl) + "/favicon.ico");
            return fetch(faviconUri, maxRedirects);
        } catch (Exception e) {
            LOG.debug("Favicon fetch failed for {}: {}", bookmarkUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private @NonNull Optional<FetchedFavicon> fetch(@NonNull URI uri, int redirectsLeft) throws IOException {
        if (redirectsLeft < 0 || !isAllowedScheme(uri.getScheme()) || !isPublicHost(uri.getHost())) {
            return Optional.empty();
        }

        HttpURLConnection conn = openConnection(uri);
        try {
            int status = conn.getResponseCode();
            if (isRedirect(status)) {
                return followRedirect(conn, uri, redirectsLeft);
            }
            if (status != 200) {
                return Optional.empty();
            }
            return readImageBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private @NonNull HttpURLConnection openConnection(@NonNull URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout((int) timeout.toMillis());
        conn.setReadTimeout((int) timeout.toMillis());
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Chainlink-FaviconProxy/1.0");
        conn.setRequestProperty("Accept", "image/*");
        return conn;
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private @NonNull Optional<FetchedFavicon> followRedirect(
        @NonNull HttpURLConnection conn, @NonNull URI uri, int redirectsLeft
    ) throws IOException {
        String location = conn.getHeaderField("Location");
        if (location == null) {
            return Optional.empty();
        }
        return fetch(uri.resolve(location), redirectsLeft - 1);
    }

    private @NonNull Optional<FetchedFavicon> readImageBody(@NonNull HttpURLConnection conn) throws IOException {
        String mediaType = parseImageMediaType(conn.getContentType());
        if (mediaType == null) {
            return Optional.empty();
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = readAtMost(in, maxBytes);
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(new FetchedFavicon(bytes, mediaType));
        }
    }

    private static @Nullable String parseImageMediaType(@Nullable String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return null;
        }
        return contentType.split(";")[0].trim();
    }

    /** Reads up to {@code limit} bytes; returns {@code null} if the stream exceeds the limit. */
    private static byte @Nullable [] readAtMost(@NonNull InputStream in, int limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[Math.min(limit, 8192)];
        int total = 0;
        int read;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    static @NonNull String canonicalOrigin(@NonNull URL url) {
        String scheme = url.getProtocol().toLowerCase();
        String host = url.getHost().toLowerCase();
        int port = url.getPort();
        String defaultPort = "https".equals(scheme) ? "443" : "80";
        if (port == -1 || String.valueOf(port).equals(defaultPort)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    static boolean isAllowedScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    static boolean isPublicHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress addr : addresses) {
                if (!isPublicAddress(addr)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException _) {
            return false;
        }
    }

    static boolean isPublicAddress(@NonNull InetAddress addr) {
        if (addr.isAnyLocalAddress()
            || addr.isLoopbackAddress()
            || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress()
            || addr.isMulticastAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xff;
            int o1 = b[1] & 0xff;
            // 169.254.169.254 (cloud metadata) is link-local; covered above.
            // 100.64.0.0/10 carrier-grade NAT
            if (o0 == 100 && (o1 & 0xc0) == 64) {
                return false;
            }
            // 0.0.0.0/8 reserved
            if (o0 == 0) {
                return false;
            }
        }
        if (b.length == 16) {
            // fc00::/7 unique local addresses
            if ((b[0] & 0xfe) == 0xfc) {
                return false;
            }
        }
        return true;
    }

    public record FetchedFavicon(byte @NonNull [] bytes, @NonNull String contentType) {}
}
