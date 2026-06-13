package org.chainlink.api.collection.favicon;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaviconFetcherService {

    private final ConfigService configService;

    public @NonNull Optional<FetchedFavicon> fetchFor(@NonNull URL bookmarkUrl) {
        if (configService.getFetchSkipList().matches(bookmarkUrl.getHost())) {
            LOG.debug("Favicon fetch skipped for {} (matches fetch skip list)", bookmarkUrl.getHost());
            return Optional.empty();
        }
        try {
            int maxRedirects = configService.getFaviconMaxRedirects();
            URI origin = URI.create(canonicalOrigin(bookmarkUrl));

            // Fetch the origin HTML following redirects, and resolve icon hrefs
            // (and the /favicon.ico fallback) against the URL we actually landed
            // on. A site that redirects example.com -> www.example.com/ serves
            // relative icon hrefs that only resolve to the right host when the
            // *final* URL is used as the base — resolving against the original
            // origin points the fetch at a host that typically 404s.
            Optional<Resolved<String>> html = discoverHtml(origin, maxRedirects);
            URI base = html.map(Resolved::finalUri).orElse(origin);

            URI declared = html.map(h -> parseIconLink(h.value(), base)).orElse(null);
            if (declared != null) {
                Optional<FetchedFavicon> img = fetchImage(declared, maxRedirects);
                if (img.isPresent()) {
                    return img;
                }
            }
            return fetchImage(base.resolve("/favicon.ico"), maxRedirects);
        } catch (Exception e) {
            LOG.debug("Favicon fetch failed for {}: {}", bookmarkUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private @NonNull Optional<Resolved<String>> discoverHtml(@NonNull URI origin, int maxRedirects) {
        try {
            return fetchHtml(origin, maxRedirects);
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    private interface ResponseHandler<T> {
        @NonNull Optional<T> handle(@NonNull HttpURLConnection conn) throws IOException;
    }

    /** A successfully fetched body together with the final URL it was served from (after redirects). */
    private record Resolved<T>(@NonNull T value, @NonNull URI finalUri) {}

    private <T> @NonNull Optional<Resolved<T>> fetch(
        @NonNull URI uri, int redirectsLeft, @NonNull String accept, @NonNull ResponseHandler<T> handler
    ) throws IOException {
        if (redirectsLeft < 0 || !isAllowedScheme(uri.getScheme()) || !isPublicHost(uri.getHost())) {
            return Optional.empty();
        }
        HttpURLConnection conn = openConnection(uri, accept);
        try {
            int status = conn.getResponseCode();
            if (isRedirect(status)) {
                String location = conn.getHeaderField("Location");
                if (location == null) {
                    return Optional.empty();
                }
                return fetch(uri.resolve(location), redirectsLeft - 1, accept, handler);
            }
            if (status != 200) {
                return Optional.empty();
            }
            return handler.handle(conn).map(value -> new Resolved<>(value, uri));
        } finally {
            conn.disconnect();
        }
    }

    private @NonNull Optional<FetchedFavicon> fetchImage(@NonNull URI uri, int redirectsLeft) throws IOException {
        return fetch(uri, redirectsLeft, "image/*", this::readImageBody).map(Resolved::value);
    }

    private @NonNull Optional<Resolved<String>> fetchHtml(@NonNull URI uri, int redirectsLeft) throws IOException {
        return fetch(uri, redirectsLeft, "text/html,application/xhtml+xml", this::readHtmlBody);
    }

    private @NonNull HttpURLConnection openConnection(@NonNull URI uri, @NonNull String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout((int) configService.getFaviconTimeout().toMillis());
        conn.setReadTimeout((int) configService.getFaviconTimeout().toMillis());
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", configService.getFaviconUserAgent());
        conn.setRequestProperty("Accept", accept);
        return conn;
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private @NonNull Optional<FetchedFavicon> readImageBody(@NonNull HttpURLConnection conn) throws IOException {
        String mediaType = parseImageMediaType(conn.getContentType());
        if (mediaType == null) {
            return Optional.empty();
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = readAtMost(in, configService.getFaviconMaxBytes());
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(new FetchedFavicon(bytes, mediaType));
        }
    }

    private @NonNull Optional<String> readHtmlBody(@NonNull HttpURLConnection conn) throws IOException {
        String ct = conn.getContentType();
        if (ct == null) {
            return Optional.empty();
        }
        String lower = ct.toLowerCase();
        if (!lower.startsWith("text/html") && !lower.startsWith("application/xhtml")) {
            return Optional.empty();
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = readAtMost(in, configService.getFaviconMaxBytes());
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static @Nullable String parseImageMediaType(@Nullable String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return null;
        }
        return contentType.split(";")[0].trim();
    }

    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_ATTR = Pattern.compile("\\brel\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_ATTR = Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_ATTR = Pattern.compile("\\btype\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZES_ATTR = Pattern.compile("\\bsizes\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** SVG beats any raster size; "sizes=any" beats numeric sizes; otherwise highest pixel size wins. */
    private static final int SCORE_SVG = Integer.MAX_VALUE;
    private static final int SCORE_ANY = Integer.MAX_VALUE - 1;

    private record IconCandidate(@NonNull String href, int score) {}

    /**
     * Best-effort scan of the document head for {@code <link rel="...icon...">}.
     * SVG icons (by {@code type="image/svg+xml"} or {@code .svg} extension) are
     * preferred since they scale; among raster icons, the largest declared
     * {@code sizes="NxM"} wins. Returns {@code null} if no icon link is found.
     */
    static @Nullable URI parseIconLink(@NonNull String html, @NonNull URI base) {
        return LINK_TAG.matcher(headRegion(html)).results()
            .map(MatchResult::group)
            .map(FaviconFetcherService::toIconCandidate)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .max(Comparator.comparingInt(IconCandidate::score))
            .map(c -> resolveSafely(base, c.href()))
            .orElse(null);
    }

    private static @NonNull Optional<IconCandidate> toIconCandidate(@NonNull String tag) {
        if (!hasIconRel(tag)) return Optional.empty();
        String href = attr(HREF_ATTR, tag);
        if (href == null) return Optional.empty();
        return Optional.of(new IconCandidate(href.trim(), scoreIcon(tag, href)));
    }

    private static int scoreIcon(@NonNull String tag, @NonNull String href) {
        if (looksLikeSvg(tag, href)) return SCORE_SVG;
        Optional<Integer> sizeScore = parseSizes(attr(SIZES_ATTR, tag));
        return sizeScore.orElse(0);
    }

    private static boolean looksLikeSvg(@NonNull String tag, @NonNull String href) {
        String type = attr(TYPE_ATTR, tag);
        if (type != null && type.toLowerCase().contains("svg")) return true;
        String path = href.toLowerCase().split("[?#]", 2)[0];
        return path.endsWith(".svg");
    }

    private static @NonNull Optional<Integer> parseSizes(@Nullable String sizes) {
        if (sizes == null) return Optional.empty();
        String s = sizes.toLowerCase();
        if (s.contains("any")) return Optional.of(SCORE_ANY);
        int best = 0;
        for (String part : s.split("\\s+")) {
            int x = part.indexOf('x');
            if (x <= 0) continue;
            try {
                best = Math.max(best, Integer.parseInt(part.substring(0, x)));
            } catch (NumberFormatException _) { /* skip malformed */ }
        }
        return best > 0 ? Optional.of(best) : Optional.empty();
    }

    private static boolean hasIconRel(@NonNull String tag) {
        String rel = attr(REL_ATTR, tag);
        return rel != null && rel.toLowerCase().contains("icon");
    }

    private static @Nullable String attr(@NonNull Pattern attrPattern, @NonNull String tag) {
        Matcher m = attrPattern.matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private static @NonNull String headRegion(@NonNull String html) {
        int end = html.toLowerCase().indexOf("</head>");
        return end > 0 ? html.substring(0, end) : html;
    }

    private static @Nullable URI resolveSafely(@NonNull URI base, @NonNull String href) {
        try {
            return base.resolve(href);
        } catch (IllegalArgumentException _) {
            return null;
        }
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
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        host = host.toLowerCase();
        int port = url.getPort();
        String defaultPort = "https".equals(scheme) ? "443" : "80";
        if (port == -1 || String.valueOf(port).equals(defaultPort)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    public static boolean isAllowedScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public static boolean isPublicHost(String host) {
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
