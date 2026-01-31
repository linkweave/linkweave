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

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.infrastructure.deployment.DeploymentEnvironment;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
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

    @Getter(AccessLevel.NONE)
    @ConfigProperty(name = "app.deployment.public-url.bs" )
    URL publicUrlBS;

    @Getter(AccessLevel.NONE)
    @ConfigProperty(name = "app.deployment.public-url.win" )
    URL publicUrlWIN;

    @ConfigProperty(name = "app.deployment.version")
    String version;

    @ConfigProperty(name = "app.deployment.client-auth-config.client-id")
    @IgnoreForIdClassTest
    String clientAuthConfigClientId;

    @ConfigProperty(name = "app.deployment.client-auth-config.realm")
    String clientAuthConfigRealm;

    @ConfigProperty(name = "app.deployment.client-auth-config.logout-url")
    Optional<URL> clientAuthConfigLogoutUrl;

    @ConfigProperty(name = "app.deployment.client-auth-config.auth-url")
    URL clientAuthConfigAuthUrl;

    @ConfigProperty(name = "esc.benutzer.rolle.cache.timeout.minutes", defaultValue = "30")
    int benutzerRollenCacheTimeoutMinutes;

    @ConfigProperty(name = "app.allow-db-reset")
    boolean allowDbReset;

    @ConfigProperty(name = "app.allow-timetravel")
    boolean featureTimeTravelEndpointEnabled;

    @ConfigProperty(name = "app.version-filter-enabled")
    boolean versionFilterEnabled;

    // currently default is 60 days
    @ConfigProperty(name = "esc.scheduler.inactivate.benutzer.after.minutes.unused", defaultValue = "86400")
    int inactivateBenutzerAfterMinutesUnused;

    @ConfigProperty(name = "esc.scheduler.inactivate.kinder.after.inactive.months", defaultValue = "9")
    int inactivateKinderAfterInactiveMoths;

    @ConfigProperty(name = "esc.scheduler.inactivate.kinder.manual.inactivation.age", defaultValue = "16")
    int getInactiveKinderManualKinderInactivationAge;

    @ConfigProperty(name = "esc.s3.bucket-name")
    String s3BucketName;

    @ConfigProperty(name = "esc.s3.upload-timeout-seconds", defaultValue = "30s")
    Duration s3UploadTimeout;

    @Getter(AccessLevel.NONE)
    @ConfigProperty(name = "esc.s3.encryption-key.bs")
    Optional<String> s3EncryptionKeyBS;

    @Getter(AccessLevel.NONE)
    @ConfigProperty(name = "esc.s3.encryption-key.win")
    Optional<String> s3EncryptionKeyWIN;

    @Getter(AccessLevel.NONE)
    @ConfigProperty(name = "esc.s3.encryption-key.be")
    Optional<String> s3EncryptionKeyBE;

    @ConfigProperty(name = "esc.esca-sync.error-mail.enabled", defaultValue = "true")
    boolean escadaSyncErrorMailEnabled;

    @ConfigProperty(name = "esc.esca-sync.client.user", defaultValue = "escada-client@dvbern.ch")
    String escadaClientUser;

    @ConfigProperty(name = "esc.esca-sync.client.azp", defaultValue = "escada-client")
    String escadaAZP;

    @ConfigProperty(name = "esc.esca-sync.log.redact.personalinfo", defaultValue = "true")
    boolean escadaRedactPersonalInfo;

    @ConfigProperty(name = "esc.file-ingress.client.user", defaultValue = "file-ingress-client@dvbern.ch")
    String fileIngressClientUser;

    @ConfigProperty(name = "esc.file-ingress.client.azp", defaultValue = "file-ingress-client")
    String fileIngressAZP;

    @ConfigProperty(name = "esc.customer-admin-emails", defaultValue = "bs-esc@dvbern.ch")
    List<String> customerAdminEmails;

    @ConfigProperty(name = "esc.mail.overriding.recipients.enabled", defaultValue = "false")
    boolean mailOverridingRecipientsEnabled;

    @ConfigProperty(name = "esc.mail.overriding.recipients.address", defaultValue = "")
    List<String> mailOverridingRecipientsAddress;

    @ConfigProperty(name = "esc.quick-search.max-results", defaultValue = "40")
    int quickSearchMaxResults;

    @ConfigProperty(name = "esc.documentqueue.max.open.per.user", defaultValue = "10")
    int documentqueueMaxOpenPerUser;

    @ConfigProperty(name = "esc.documentqueue.max.life.time", defaultValue = "P7D")
    Duration documentqueueMaxLifeTime;

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

    public void validateNotProdAndDbResetFeatureEnabled() {
        if (isStufeProd() || !isAllowDbReset()) {
            LOG.warn("Resetting database is not allowed in production environment or if feature is not enabled!");
            throw new AppFailureException(AppFailureMessage.internalError("DB Reset is forbidden"));
        }
    }

    public void validateNotProdAndTimeTravelFeatureEnabled() {
        if (isStufeProd() || !isFeatureTimeTravelEndpointEnabled()) {
            LOG.warn("Time travelling is not allowed in production environment or if feature is not enabled!");
            throw new AppFailureException(AppFailureMessage.internalError("Time travelling is forbidden"));
        }
    }

    public boolean isStufeProd() {
        var env = getEnvironment();
        return env.isProd();
    }


}
