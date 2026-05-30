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

package org.chainlink.api.shared.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.infrastructure.deployment.DeploymentEnvironment;
import org.chainlink.infrastructure.stereotypes.Service;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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


    @ConfigProperty(name = "chainlink.quick-search.max-results", defaultValue = "40")
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

    @ConfigProperty(name = "chainlink.cleanup.default-threshold-months", defaultValue = "6")
    int cleanupDefaultThresholdMonths;

    @ConfigProperty(name = "chainlink.cleanup.available-thresholds", defaultValue = "3,6,12")
    List<Integer> cleanupAvailableThresholds;

    @ConfigProperty(name = "chainlink.favicon.cache-dir", defaultValue = "developer-local-settings/favicon-cache")
    String faviconCacheDir;

    @ConfigProperty(name = "chainlink.favicon.success-ttl", defaultValue = "30D")
    Duration faviconSuccessTtl;

    @ConfigProperty(name = "chainlink.favicon.negative-ttl", defaultValue = "6H")
    Duration faviconNegativeTtl;

    @ConfigProperty(name = "chainlink.favicon.timeout", defaultValue = "5S")
    Duration faviconTimeout;

    @ConfigProperty(name = "chainlink.favicon.max-bytes", defaultValue = "262144")
    int faviconMaxBytes;

    @ConfigProperty(name = "chainlink.favicon.max-redirects", defaultValue = "3")
    int faviconMaxRedirects;

    @ConfigProperty(name = "chainlink.favicon.cache-cleanup.max-size", defaultValue = "40MB")
    String faviconCacheCleanupMaxSize;

    @ConfigProperty(name = "chainlink.favicon.cache-cleanup.min-bookmark-age", defaultValue = "28D")
    Duration faviconCacheCleanupMinBookmarkAge;

    @ConfigProperty(name = "chainlink.favicon.cache-cleanup.enabled", defaultValue = "true")
    boolean faviconCacheCleanupEnabled;

    @ConfigProperty(name = "chainlink.screenshot.enabled", defaultValue = "true")
    boolean screenshotEnabled;

    @ConfigProperty(name = "chainlink.screenshot.capture-job.enabled", defaultValue = "true")
    boolean screenshotCaptureJobEnabled;

    @ConfigProperty(name = "chainlink.screenshot.capture-job.batch-size", defaultValue = "10")
    int screenshotCaptureJobBatchSize;

    @ConfigProperty(name = "chainlink.screenshot.cache-dir", defaultValue = "developer-local-settings/screenshot-cache")
    String screenshotCacheDir;

    @ConfigProperty(name = "chainlink.screenshot.success-ttl", defaultValue = "30D")
    Duration screenshotSuccessTtl;

    @ConfigProperty(name = "chainlink.screenshot.negative-ttl", defaultValue = "12H")
    Duration screenshotNegativeTtl;

    @ConfigProperty(name = "chainlink.screenshot.cache-cleanup.enabled", defaultValue = "true")
    boolean screenshotCacheCleanupEnabled;

    @ConfigProperty(name = "chainlink.screenshot.cache-cleanup.max-size", defaultValue = "200MB")
    String screenshotCacheCleanupMaxSize;

    @ConfigProperty(name = "chainlink.screenshot.cache-cleanup.min-bookmark-age", defaultValue = "28D")
    Duration screenshotCacheCleanupMinBookmarkAge;

    @ConfigProperty(name = "chainlink.feature.bookmark-properties.enabled", defaultValue = "true")
    boolean bookmarkPropertiesEnabled;

    @ConfigProperty(name = "chainlink.metrics.refresh.enabled", defaultValue = "true")
    boolean metricsRefreshEnabled;

    @ConfigProperty(name = "chainlink.sentry.frontend-project-id", defaultValue = "4511463699120208")
    String sentryFrontendProject;




    public boolean isStufeProd() {
        var env = getEnvironment();
        return env.isProd();
    }


}
