/*
 *
 * Copyright (C) 2024 DV Bern AG, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linkweave.api.shared.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.shared.net.HostPatternSet;
import org.linkweave.infrastructure.deployment.DeploymentEnvironment;
import org.linkweave.infrastructure.stereotypes.Service;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NonNull;

@Slf4j
@ApplicationScoped
@Getter
@RequiredArgsConstructor
@Service
@Transactional(TxType.NOT_SUPPORTED)
public class ConfigService {


    @ConfigProperty(name = "app.deployment.app-project")
    String appProject;

    @ConfigProperty(name = "app.deployment.app-module")
    String appModule;

    @ConfigProperty(name = "app.deployment.environment")
    DeploymentEnvironment environment;

    @ConfigProperty(name = "app.deployment.instance")
    String instance;

    @ConfigProperty(name = "app.deployment.version")
    String version;


    @ConfigProperty(name = "app.version-filter-enabled", defaultValue = "false")
    boolean versionFilterEnabled;


    @ConfigProperty(name = "linkweave.quick-search.max-results", defaultValue = "40")
    int quickSearchMaxResults;


    /**
     * a list of file extensions that are allowed to be uploaded, can be extension or media type (e.g. .pdf or
     * application/pdf)
     * !!!IMPORTANT!!!
     * Keep list in frontend in sync, as we do not sync the config to the client yet
     * See usages of feat-upload-table and feat-file-drop to cover all upload use cases
     */
    @ConfigProperty(name = "esc.upload.extension.whitelist",
        defaultValue = ".pdf,.docx,.xlsx,.pptx,.eml,.msg,.jpg,.jpeg,.png,.heic,.webp")
    Set<String> uploadExtensionWhitelist;

    /**
     * Dieses File wurde von
     * <a href="https://swisspost.opendatasoft.com/explore/dataset/plz_verzeichnis_v2/table/">DiePost</a>
     * heruntergeladen. Die Spalten Geo Shape und Geokoordinaten wurden geleert
     */
    @ConfigProperty(name = "esc.plz-import.file-location", defaultValue = "/plz/plz_verzeichnis_v2_nogeo.csv")
    String plzImportFileLocation;

    /**
     * defines how many Anlasesse are loaded for the user on the dashboard
     */
    @ConfigProperty(name = "esc.dashboard.anlass.limit", defaultValue = "5")
    int dashboardAnlassLimit;

    /**
     * defines how many Aktivitaeten are loaded for the user on the dashboard
     */
    @ConfigProperty(name = "esc.dashboard.aktivitaet.history.limit", defaultValue = "20")
    int dashboardAktivitaetHistoryLimit;

    @ConfigProperty(name = "esc.feature.optimistic.locking.disabled", defaultValue = "false")
    boolean featureOptimisticLockingDisabled;

    @ConfigProperty(name = "linkweave.cleanup.default-threshold-months", defaultValue = "6")
    int cleanupDefaultThresholdMonths;

    @ConfigProperty(name = "linkweave.cleanup.available-thresholds", defaultValue = "3,6,12")
    List<Integer> cleanupAvailableThresholds;

    // Operator-facing global backend fetch denylist: hostnames (or *.wildcards)
    // the backend must never fetch favicons or screenshots for. Use for domains
    // the server cannot reach (internal CDNs) or should not hammer (rate-limiting
    // hosts). Comma- or newline-separated. Parsed once and cached; see
    // getBackendFetchDenylist.
    @ConfigProperty(name = "linkweave.fetch.skip-domains")
    Optional<String> backendFetchDenylistRaw;

    private HostPatternSet backendFetchDenylist;

    @ConfigProperty(name = "linkweave.favicon.cache-dir", defaultValue = "developer-local-settings/favicon-cache")
    String faviconCacheDir;

    // Desktop bundle (UC-052) only: directory the SPA is served from. Unset in the normal
    // (Caddy-fronted) deployment, where Caddy serves the frontend. See DesktopWebRootRoute.
    @ConfigProperty(name = "linkweave.desktop.web-root")
    Optional<String> desktopWebRoot;

    @ConfigProperty(name = "linkweave.favicon.success-ttl", defaultValue = "30D")
    Duration faviconSuccessTtl;

    @ConfigProperty(name = "linkweave.favicon.negative-ttl", defaultValue = "6H")
    Duration faviconNegativeTtl;

    // Backoff ceiling: each consecutive favicon fetch failure doubles the
    // negative TTL from negative-ttl up to this cap, so permanently-unreachable
    // hosts stop being re-fetched every 6h.
    @ConfigProperty(name = "linkweave.favicon.negative-ttl-max", defaultValue = "30D")
    Duration faviconNegativeTtlMax;

    @ConfigProperty(name = "linkweave.favicon.timeout", defaultValue = "5S")
    Duration faviconTimeout;

    @ConfigProperty(name = "linkweave.favicon.max-bytes", defaultValue = "262144")
    int faviconMaxBytes;

    @ConfigProperty(name = "linkweave.favicon.max-redirects", defaultValue = "5")
    int faviconMaxRedirects;

    // A realistic browser User-Agent. Many sites sit behind a WAF/CDN (e.g.
    // CloudFront, Akamai) that returns 403 to non-browser User-Agents, so a
    // bespoke token like "LinkWeave-FaviconProxy/1.0" never reaches the icon.
    @ConfigProperty(name = "linkweave.favicon.user-agent",
        defaultValue = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
    String faviconUserAgent;

    @ConfigProperty(name = "linkweave.favicon.cache-cleanup.max-size", defaultValue = "40MB")
    String faviconCacheCleanupMaxSize;

    @ConfigProperty(name = "linkweave.favicon.cache-cleanup.min-bookmark-age", defaultValue = "28D")
    Duration faviconCacheCleanupMinBookmarkAge;

    @ConfigProperty(name = "linkweave.favicon.cache-cleanup.enabled", defaultValue = "true")
    boolean faviconCacheCleanupEnabled;

    @ConfigProperty(name = "linkweave.screenshot.enabled", defaultValue = "true")
    boolean screenshotEnabled;

    @ConfigProperty(name = "linkweave.screenshot.capture-job.enabled", defaultValue = "true")
    boolean screenshotCaptureJobEnabled;

    @ConfigProperty(name = "linkweave.screenshot.capture-job.batch-size", defaultValue = "10")
    int screenshotCaptureJobBatchSize;

    @ConfigProperty(name = "linkweave.screenshot.cache-dir", defaultValue = "developer-local-settings/screenshot-cache")
    String screenshotCacheDir;

    @ConfigProperty(name = "linkweave.screenshot.success-ttl", defaultValue = "30D")
    Duration screenshotSuccessTtl;

    @ConfigProperty(name = "linkweave.screenshot.negative-ttl", defaultValue = "12H")
    Duration screenshotNegativeTtl;

    // Backoff ceiling: each consecutive screenshot capture failure doubles the
    // negative TTL from negative-ttl up to this cap, so permanently-unreachable
    // pages stop being re-attempted (and re-hit) every 12h.
    @ConfigProperty(name = "linkweave.screenshot.negative-ttl-max", defaultValue = "30D")
    Duration screenshotNegativeTtlMax;

    @ConfigProperty(name = "linkweave.screenshot.cache-cleanup.enabled", defaultValue = "true")
    boolean screenshotCacheCleanupEnabled;

    @ConfigProperty(name = "linkweave.screenshot.cache-cleanup.max-size", defaultValue = "200MB")
    String screenshotCacheCleanupMaxSize;

    @ConfigProperty(name = "linkweave.screenshot.cache-cleanup.min-bookmark-age", defaultValue = "28D")
    Duration screenshotCacheCleanupMinBookmarkAge;

    @ConfigProperty(name = "linkweave.feature.bookmark-properties.enabled", defaultValue = "true")
    boolean bookmarkPropertiesEnabled;

    @ConfigProperty(name = "linkweave.metrics.refresh.enabled", defaultValue = "true")
    boolean metricsRefreshEnabled;

    @ConfigProperty(name = "linkweave.sentry.frontend-project-id", defaultValue = "4511463699120208")
    String sentryFrontendProject;

    // LLM auto-tagging (FR-095). The master switch (FR-096) lets the whole
    // feature be left out; the system then falls back to client-side rule
    // suggestions (UC-045).
    @ConfigProperty(name = "linkweave.autotag.llm.enabled", defaultValue = "true")
    boolean autotagLlmEnabled;

    // Provider selection (FR-097): "ollama" (local) or "openai" (any
    // OpenAI-compatible hosted API, e.g. z.ai GLM Coding Plan).
    @ConfigProperty(name = "linkweave.autotag.provider", defaultValue = "ollama")
    String autotagProvider;

    @ConfigProperty(name = "linkweave.autotag.model", defaultValue = "gemma2:2b")
    String autotagModel;

    // Ollama keep_alive: idle window the model stays resident after the last
    // call. "0" unloads immediately, "-1" keeps it resident forever.
    @ConfigProperty(name = "linkweave.autotag.keep-alive", defaultValue = "15m")
    String autotagKeepAlive;

    // Cap on the enum vocabulary sent to the model; 0 = no cap.
    @ConfigProperty(name = "linkweave.autotag.max-vocab", defaultValue = "0")
    int autotagMaxVocab;

    // OpenAI-compatible hosted provider (used when provider = openai). The base
    // URL is set via quarkus.rest-client.openai-autotag.url. The API key is a
    // secret — source it from an env var, never commit it.
    @ConfigProperty(name = "linkweave.autotag.openai.model", defaultValue = "glm-4.6")
    String autotagOpenAiModel;

    @ConfigProperty(name = "linkweave.autotag.openai.api-key")
    Optional<String> autotagOpenAiApiKey;

    // --- Degradation controls (UC-108) ---

    // BR-108-1: the interactive budget. A suggestion fires while the user waits
    // in the bookmark dialog, so it gets a small multiple of the 700ms debounce
    // rather than a network default. Feeds
    // quarkus.rest-client.ollama.read-timeout and is echoed to the client so the
    // browser aborts on the same budget. Milliseconds, to stay in the same unit
    // as the REST Client read-timeout it feeds.
    @ConfigProperty(name = "linkweave.autotag.suggest-timeout-ms", defaultValue = "8000")
    int autotagSuggestTimeoutMs;

    // BR-108-7: whether suggestions fire automatically as the user types, or
    // only when they click "Suggest tags with AI". Hosts too small to serve the
    // model at interactive speed turn this off instead of losing the feature.
    @ConfigProperty(name = "linkweave.autotag.auto-fire", defaultValue = "true")
    boolean autotagAutoFire;

    // BR-108-4: consecutive failures that open the circuit, and the cooldown
    // before a single probe is let through. The cooldown doubles on each failed
    // probe up to the ceiling.
    @ConfigProperty(name = "linkweave.autotag.circuit.failure-threshold", defaultValue = "3")
    int autotagCircuitFailureThreshold;

    @ConfigProperty(name = "linkweave.autotag.circuit.cooldown", defaultValue = "30s")
    Duration autotagCircuitCooldown;

    @ConfigProperty(name = "linkweave.autotag.circuit.cooldown-max", defaultValue = "10m")
    Duration autotagCircuitCooldownMax;

    // BR-108-9: ceiling on concurrent in-flight model calls. Requests beyond the
    // cap answer "unavailable" immediately rather than queueing, so a slow model
    // can never occupy the worker pool that serves bookmark reads and writes. A
    // permit belongs to a user-in-a-collection, not to a request, so this counts
    // concurrent editors rather than keystrokes.
    @ConfigProperty(name = "linkweave.autotag.max-concurrent", defaultValue = "6")
    int autotagMaxConcurrent;

    // BR-108-6: minimum interval between model pulls, doubling up to the ceiling
    // after each failure. Guards against the re-pull feedback loop where a
    // timeout triggers a download that makes the next timeout more likely.
    @ConfigProperty(name = "linkweave.autotag.pull.min-interval", defaultValue = "5m")
    Duration autotagPullMinInterval;

    @ConfigProperty(name = "linkweave.autotag.pull.min-interval-max", defaultValue = "1h")
    Duration autotagPullMinIntervalMax;

    /** True when the hosted OpenAI-compatible provider is selected. */
    public boolean isAutotagProviderOpenAi() {
        return "openai".equalsIgnoreCase(autotagProvider);
    }




    public boolean isStufeProd() {
        var env = getEnvironment();
        return env.isProd();
    }

    /**
     * The parsed global backend fetch denylist, cached after first access. Both
     * the favicon and screenshot fetchers consult this before any DNS/network work.
     */
    @NonNull
    public HostPatternSet getBackendFetchDenylist() {
        HostPatternSet local = backendFetchDenylist;
        if (local == null) {
            local = HostPatternSet.parse(backendFetchDenylistRaw.orElse(null));
            backendFetchDenylist = local;
        }
        return local;
    }


}
