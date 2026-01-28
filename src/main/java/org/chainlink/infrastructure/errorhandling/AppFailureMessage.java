/*
 *
 * Copyright (C) 2025 DV Bern AG, Switzerland
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

package org.chainlink.infrastructure.errorhandling;

import java.time.LocalDate;

import ch.dvbern.dvbstarter.shared.types.id.ID;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import ch.dvbern.oss.hemed.esc.api.features.anlass.einladung.Einladung;
import ch.dvbern.oss.hemed.esc.api.features.anlass.types.AnlassTyp;
import ch.dvbern.oss.hemed.esc.api.shared.abstractentity.AbstractEntity;
import ch.dvbern.oss.hemed.esc.api.shared.scheduler.SchedulerTyp;
import ch.dvbern.oss.hemed.esc.api.shared.types.IgnoreForIdClassTest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AppFailureMessage {

    public static final String MESSAGE = "message";
    public static final String ENTITY_NAME = "entityName";

    @NonNull
    public static AppFailureMessage internalError(@NonNull String message) {
        return build(I18nMessage.of(
            "AppFailure.INTERNAL_ERROR",
            MESSAGE, message
        ));
    }

    @NonNull
    public static AppFailureMessage jsonParsing(@NonNull String message, @NonNull String location) {
        return build(I18nMessage.of(
            "AppFailure.JSON_PARSING",
            MESSAGE, message,
            "location", location
        ));
    }

    @NonNull
    public static AppFailureMessage jsonMapping() {
        return build(I18nMessage.of(
            "AppFailure.JSON_MAPPING"
        ));
    }

    @NonNull
    public static AppFailureMessage changedByOtherUser(AbstractEntity<?> entity) {
        return build(I18nMessage.of(
            "AppFailure.CHANGED_BY_OTHER_USER",
            ENTITY_NAME, entity.getClass().getSimpleName(),
            "versionBackend", entity.getVersion(),
            "versionFrontend", entity.getVersionFromFrontend()
        ));
    }

    @NonNull
    public static AppFailureMessage couldNotCreateEntity(@NonNull String entityName) {
        return build(I18nMessage.of(
            "AppFailure.COULD_NOT_CREATE_ENTITY",
            ENTITY_NAME, entityName
        ));
    }

    @NonNull
    public static AppFailureMessage edokidIdLastLaufnummerReached() {
        return build(I18nMessage.of("AppFailure.EDOKID_ID_LAST_LAUFNUMMER_REACHED"));
    }

    @NonNull
    public static AppFailureMessage anlassIsNotAbgeschlossen() {
        return build(I18nMessage.of("AppFailure.AppFailure.ANLASS_NOT_ABGESCHLOSSEN"));
    }

    @NonNull
    public static AppFailureMessage entityNotFound(@NonNull ID<?> id) {
        return build(I18nMessage.of(
            "AppFailure.ENTITY_NOT_FOUND",
            ENTITY_NAME, id.getEntityClazz().getSimpleName(),
            "id", id.getUUID().toString()
        ));
    }

    @NonNull
    public static AppFailureMessage entityNotFound(@NonNull ID<?> id, @NonNull String message) {
        return build(I18nMessage.of(
            "AppFailure.ENTITY_NOT_FOUND_WITH_MESSAGE",
            ENTITY_NAME, id.getEntityClazz().getSimpleName(),
            "id", id.getUUID().toString(),
            MESSAGE, message
        ));
    }

    @NonNull
    public static <T> AppFailureMessage entityNotFoundUsingExternalId(
        @NonNull Class<T> clazz,
        @IgnoreForIdClassTest @NonNull String id) {
        return build(I18nMessage.of(
            "AppFailure.ENTITY_NOT_FOUND",
            ENTITY_NAME, clazz.getSimpleName(),
            "id", id
        ));
    }

    @NonNull
    public static AppFailureMessage invalidTenantClaim(@NonNull String tenantClaim) {
        return build(I18nMessage.of(
            "AppFailure.INVALID_TENANT_CLAIM",
            "claim", tenantClaim
        ));
    }

    @NonNull
    public static AppFailureMessage unrecognizedProperty(
        @NonNull Class<?> owningDto,
        @NonNull String propertyName,
        @NonNull String path,
        @NonNull String allowedProperties
    ) {
        return build(I18nMessage.of(
            "AppFailure.UNRECOGNIZED_PROPERTY",
            "dtoName", owningDto.getSimpleName(),
            "propertyName", propertyName,
            "path", path,
            "allowedProperties", allowedProperties
        ));
    }

    @NonNull
    public static AppFailureMessage schedulerConfigNotFound(@NonNull SchedulerTyp typ) {
        return build(I18nMessage.of(
            "AppFailure.SCHEDULER_CONFIG_NOT_FOUND",
            "schedulerTyp", typ
        ));
    }

    @NonNull
    public static AppFailureMessage anlassConfigNotFound(@NonNull AnlassTyp typ, @NonNull LocalDate stichTag) {
        return build(I18nMessage.of(
            "AppFailure.ANLASS_CONFIG_NOT_FOUND",
            "anlassTyp", typ,
            "stichtag", stichTag.toString()
        ));
    }

    @NonNull
    public static AppFailureMessage einladungCannotBeRemoved(@NonNull Einladung einladung) {
        return build(I18nMessage.of(
            "AppFailure.EINLADUNG_CANNOT_BE_REMOVED",
            "kindName", einladung.getDossier().getKind().getNameVorname()
        ));
    }

    @NonNull
    public static AppFailureMessage impfungInternCannotBeRemoved() {
        return build(I18nMessage.of(
            "AppFailure.IMPFUNG_INTERN_CANNOT_BE_REMOVED"
        ));
    }

    @NonNull
    public static AppFailureMessage missingBerechtigung(
        Class<?> entity,
        ID<? extends AbstractEntity<?>> id,
        String username
    ) {
        return build(I18nMessage.of(
            "AppFailure.MISSING_BERECHTIGUNG",
            "entity", entity.getSimpleName(),
            "id", id,
            "username", username));
    }

    @NonNull
    public static AppFailureMessage updateFachRollenNotAllowed() {
        return build(I18nMessage.of(
            "AppFailure.UPDATE_FACHROLLEN_NOT_ALLOWED"
        ));
    }

    private final I18nMessage i18nMessage;

    @IgnoreForIdClassTest
    private final ExceptionId id;

    @NonNull
    private static AppFailureMessage build(@NonNull I18nMessage message) {
        var id = ExceptionId.random();
        return new AppFailureMessage(message, id);
    }
}
