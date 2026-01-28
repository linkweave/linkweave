package org.chainlink.infrastructure.errorhandling;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import ch.dvbern.dvbstarter.shared.types.id.ID;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import ch.dvbern.oss.hemed.esc.api.features.anlass.Anlass;
import ch.dvbern.oss.hemed.esc.api.features.anlass.anlassconfig.AnlassConfig;
import ch.dvbern.oss.hemed.esc.api.features.kinder.Kind;
import ch.dvbern.oss.hemed.esc.api.features.schule.Schule;
import ch.dvbern.oss.hemed.esc.api.features.schule.klasse.Klasse;
import ch.dvbern.oss.hemed.esc.api.shared.abstractentity.AbstractEntity;
import ch.dvbern.oss.hemed.esc.api.shared.types.IgnoreForIdClassTest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public class AppValidationMessage {

    // This key is sent to the client in the error response
    private final String clientKey;
    private final I18nMessage i18nMessage;
    private static final String CONST_KIND_NAME = "kindName";
    private static final String CONST_KIND_ID = "kindId";
    private static final String CONST_ENTITY_CLASS = "class";
    private static final String ENTITY_ID = "entityId";

    @NonNull
    public static AppValidationMessage cannotAddEntityToInactiveKind(
        @NonNull Kind kind,
        @NonNull AbstractEntity<?> entity
    ) {
        return of(
            "CannotAddEntityToInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_ADD_ENTITY_TO_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId(),
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName())
        );
    }

    @NonNull
    public static AppValidationMessage cannotCreateEinladungForInactiveKind(
        @NonNull Kind kind,
        @NonNull ID<Anlass> anlassId
    ) {
        return of(
            "CannotCreateEinladungForInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_CREATE_EINLADUNG_FOR_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId(),
                "anlassId", anlassId)
        );
    }

    public static AppValidationMessage cannotCreateEntityForInactiveKind(
        @NonNull Kind kind,
        @NonNull AbstractEntity<?> entity
    ) {
        return of(
            "CannotCeateEntityForInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_CREATE_ENTITY_FOR_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId(),
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName())
        );
    }

    public static AppValidationMessage cannotEditEntityForInactiveKind(
        @NonNull Kind kind,
        @NonNull AbstractEntity<?> entity
    ) {
        return of(
            "CannotEditEntityForInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_EDIT_ENTITY_FOR_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId(),
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName(),
                ENTITY_ID, entity.getId())
        );
    }

    public static AppValidationMessage cannotEditInactiveKind(@NonNull Kind kind) {
        return of(
            "CannotEditInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_EDIT_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId())
        );
    }

    public static AppValidationMessage cannotRemoveEntityFromInactiveKind(
        @NonNull Kind kind,
        @NonNull AbstractEntity<?> entity
    ) {
        return of(
            "CannotRemoveEntityFromInactiveKind",
            I18nMessage.of(
                "AppValidation.CANNOT_REMOVE_ENTITY_FROM_INACTIVE_KIND",
                CONST_KIND_NAME, kind.getNameVorname(),
                CONST_KIND_ID, kind.getId(),
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName(),
                ENTITY_ID, entity.getId())
        );
    }

    public static AppValidationMessage errorMassenimport(
        @NonNull String message
    ) {
        return of(
            "ErrorMassenimport",
            I18nMessage.of("AppValidation.ERROR_MASSENIMPORT", "msg", message)
        );
    }

    @NonNull
    public static AppValidationMessage genericMessage(@NonNull String key) {
        return of(
            "GenericMessage",
            I18nMessage.of(key)
        );
    }

    @NonNull
    public static AppValidationMessage invalidEmail(@NonNull String email) {
        return of(
            "InvalidEmail",
            I18nMessage.of("AppValidation.INVALID_EMAIL", "email", email)
        );
    }

    @NonNull
    public static AppValidationMessage invalidPhoneInSwissRegion(@NonNull String input) {
        return of(
            "InvalidPhoneInSwissRegion",
            I18nMessage.of("AppValidation.INVALID_PHONE_IN_SWISS_REGION", "phoneNumber", input)
        );
    }

    @NonNull
    public static AppValidationMessage noUserLoggedIn() {
        return of(
            "NoUserLoggedIn",
            I18nMessage.of("AppValidation.NO_USER_LOGGED_IN")
        );
    }

    @NonNull
    public static AppValidationMessage clientVersionVerificationFailed() {
        return of(
            "ClientVersionVerificationFailed",
            I18nMessage.of("AppValidation.CLIENT_VERSION_VERIFICATION_FAILED")
        );
    }

    @NonNull
    public static AppValidationMessage invalidSozialversicherungsnummer(@NonNull String svn) {
        return of(
            "InvalidSozialversicherungsnummer",
            I18nMessage.of("AppValidation.INVALID_SOZIALVERSICHERUNGSNUMMER", "svn", svn)
        );
    }

    @NonNull
    public static AppValidationMessage missingTranslatedString(@NonNull String language) {
        return of(
            "MissingTranslatedString",
            I18nMessage.of("AppValidation.MISSING_TRANSLATED_STRING", "language", language)
        );
    }

    @NonNull
    public static AppValidationMessage referencedForeignKey() {
        return of(
            "ReferencedForeignKey",
            I18nMessage.of("AppValidation.REFERENCED_FOREIGN_KEY")
        );
    }

    @NonNull
    public static AppValidationMessage uniqueKeyViolation(@NonNull String constraintName) {
        return of(
            "UniqueKeyViolation",
            I18nMessage.of("AppValidation.UNIQUE_KEY_VIOLATION", "constraintName", constraintName)
        );
    }

    @NonNull
    public static AppValidationMessage schuleToDeleteIsFromEscada(
        @NonNull String schulhaus,
        @NonNull ID<Schule> schuleID) {
        return of(
            "SchuleToDeleteIsFromEscada",
            I18nMessage.of(
                "AppValidation.SCHULE_TO_DELETE_IS_FROM_ESCADA",
                "schulhaus",
                schulhaus,
                "schuleID",
                schuleID)
        );
    }

    @NonNull
    public static AppValidationMessage klasseToDeleteIsFromEscada(
        @NonNull String klasse,
        @NonNull ID<Klasse> klasseID) {
        return of(
            "KlasseToDeleteIsFromEscada",
            I18nMessage.of("AppValidation.KLASSE_TO_DELETE_IS_FROM_ESCADA", "klasse", klasse, "klasseID", klasseID)
        );
    }

    @NonNull
    public static AppValidationMessage uploadProblem(@NonNull String msg) {
        return of(
            "UploadProblem",
            I18nMessage.of("AppValidation.UPLOAD_PROBLEM", "msg", msg)
        );
    }

    @NonNull
    public static AppValidationMessage invalidFileType(@NonNull String type) {
        return of(
            "UploadInvalidFiletype",
            I18nMessage.of("AppValidation.UPLOAD_INVALID_FILETYPE", "type", type)
        );
    }

    @NonNull
    public static AppValidationMessage invalidCSVFormat(
        long lineNum,
        int foundRowValueCount,
        @NonNull String[] expectedColNames
    ) {
        String colnames = String.join("; ", expectedColNames);
        return of(
            "InvalidCSVFormat",
            I18nMessage.of(
                "AppValidation.INVALID_CSV_FORMAT",
                "lineNum",
                lineNum,
                "foundRowValueCount",
                foundRowValueCount,
                "expectedColNames",
                colnames
            )
        );
    }

    @NonNull
    public static AppValidationMessage anlassCanNotBeDeleted() {
        return of(
            "AnlassCanNotBeDeleted",
            I18nMessage.of("AppValidation.ANLASS_CAN_NOT_BE_DELETED")
        );
    }

    @NonNull
    public static AppValidationMessage noSchulhausnummer(@NonNull String schulhaus) {
        return of(
            "NoSchulhausnummer",
            I18nMessage.of("AppValidation.NO_SCHULHAUSNUMMER", "schulhaus", schulhaus));
    }

    @NonNull
    public static AppValidationMessage invalidEdokidNummer(@NonNull String nummer) {
        return of(
            "InvalidNummer",
            I18nMessage.of("AppValidation.INVALID_NUMMER", "nummer", nummer));
    }

    @NonNull
    public static AppValidationMessage escadaObjectsCannotBeUpdated(@NonNull AbstractEntity<?> entity) {
        return of(
            "EscadaObjectsCannotBeUpdated",
            I18nMessage.of(
                "AppValidation.ESCADA_OBJECTS_CANNOT_BE_UPDATED",
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName(),
                "id", entity.getId()));
    }

    @NonNull
    public static AppValidationMessage escadaObjectsCannotBeDeactivated(@NonNull AbstractEntity<?> entity) {
        return of(
            "EscadaObjectsCannotBeDeactivated",
            I18nMessage.of(
                "AppValidation.ESCADA_OBJECTS_CANNOT_BE_DEACTIVATED",
                CONST_ENTITY_CLASS, entity.getClass().getSimpleName(),
                "id", entity.getId()));
    }

    @NonNull
    public static AppValidationMessage autoLabelsCannotBeEditedOrDeleted() {
        return of(
            "AutoLabelsCannotBeEditedOrDeleted",
            I18nMessage.of("AppValidation.AUTO_LABELS_CANNOT_BE_EDITED_OR_DELETED"));
    }

    @NonNull
    public static AppValidationMessage autoLabelsCannotBeAddedOrRemovedFromNotizOrAufgabe() {
        return of(
            "AutoLabelsCannotBeEditedOrDeleted",
            I18nMessage.of("AppValidation.AUTO_LABELS_CANNOT_BE_REMOVED_FROM_NOTIZ_OR_AUFGABE"));
    }

    @NonNull
    public static AppValidationMessage tooManyQueuedDocuments(int max) {
        return of(
            "TooManyQueuedDocuments",
            I18nMessage.of("AppValidation.TOO_MANY_QUEUED_DOCUMENTS", "max", max));
    }

    @NonNull
    public static AppValidationMessage inactiveInfomaterialCannotBeAddedOrRemoved() {
        return of(
            "InactiveInfomaterialCannotBeEditedOrDeleted",
            I18nMessage.of("AppValidation.INACTIVE_INFOMATERIAL_CANNOT_BE_REMOVED"));
    }

    @NonNull
    public static AppValidationMessage docMergeKeyNotSupportedByBriefvorlageType(@NonNull String key) {
        return of(
            "docMergeKeyNotSupportedByType",
            I18nMessage.of("AppValidation.DOCMERGE_KEY_NOT_SUPPORTED_BY_BRIEFVORLAGE_TYPE", "key", key));
    }

    @NonNull
    public static AppValidationMessage noMpaForAufgabeEmpfaenger(Anlass anlass) {
        return of(
            "NoMpaForAufgabeErstellt",
            I18nMessage.of(
                "AppValidation.NO_MPA_FOR_AUFGABE_EMPFAENGER",
                "titel",
                anlass.getTitel(),
                "anlassId",
                anlass.getId())
        );
    }

    @NonNull
    public static AppValidationMessage invalidEntityRelation(
        @NonNull ID<?> entityId,
        @NonNull ID<?> expectedForeignKey,
        @Nullable ID<?> actualForeignKey
    ) {
        return of(
            "InvalidEntityRelation", I18nMessage.of(
                "AppValidation.INVALID_ENTITY_RELATION",
                ENTITY_ID,
                entityId,
                "expectedForeignKey",
                expectedForeignKey,
                "actualForeignKey",
                actualForeignKey));
    }

    @NonNull
    public static AppValidationMessage invalidEntityRelation(
        @NonNull ID<?> entityId,
        @NonNull ID<?> expectedForeignKey,
        @NonNull List<? extends ID<?>> actualForeignKeys
    ) {
        return of(
            "InvalidEntityRelation", I18nMessage.of(
                "AppValidation.INVALID_ENTITY_RELATION",
                ENTITY_ID,
                entityId,
                "expectedForeignKey",
                expectedForeignKey,
                "actualForeignKey",
                actualForeignKeys.toString()));
    }

    @NonNull
    public static AppValidationMessage anlassConfigUsedInAtLeastOneAnlassCannotBeEdited() {
        return of(
            "AnlassConfigUsedInAtLeastOneAnlassCannotBeEdited",
            I18nMessage.of("AppValidation.ANLASS_CONFIG_USED_IN_AT_LEAST_ONE_ANLASS_CANNOT_BE_EDITED"));
    }

    @NonNull
    public static AppValidationMessage anlassConfigCannotStartBeforeLastAnlassConfig(
        @NonNull LocalDate minDatumVon
    ) {
        return of(
            "AnlassConfigCannotStartBeforeLastAnlassConfig",
            I18nMessage.of(
                "AppValidation.ANLASS_CONFIG_CANNOT_START_BEFORE_LAST_ANLASS_CONFIG",
                "minDatumVon",
                minDatumVon.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
            ));
    }

    @NonNull
    public static AppValidationMessage anlassConfigGapOrOverlapDetected(
        @NonNull AnlassConfig config1,
        @NonNull AnlassConfig config2
    ) {
        return of(
            "AnlassConfigGapOrOverlapDetected",
            I18nMessage.of(
                "AppValidation.ANLASS_CONFIG_GAP_OR_OVERLAP_DETECTED",
                "config1",
                config1.getId(),
                "config2",
                config2.getId()
            ));
    }

    @NonNull
    public static AppValidationMessage fileIngressInvalidFilenameForFragebogenIngress(
        @NonNull String filename
    ) {
        return of(
            "InvalidFilenameForFragebogenIngress",
            I18nMessage.of(
                "AppValidation.INVALID_FILENAME_FOR_FRAGEBOGEN_INGRESS",
                "filename",
                filename
            ));
    }

    @NonNull
    public static AppValidationMessage fileIngressNoDossierForEsadId(
        @NonNull @IgnoreForIdClassTest String esadId
    ) {
        return of(
            "NoDossierForEsadId",
            I18nMessage.of(
                "AppValidation.NO_DOSSIER_FOR_ESAD_ID",
                "esadId",
                esadId
            ));
    }

    @NonNull
    public static AppValidationMessage fileIngressGeburtsdatumMissmatch(
        @NonNull @IgnoreForIdClassTest String esadId
    ) {
        return of(
            "FileIngressGeburtsdatumMissmatch",
            I18nMessage.of(
                "AppValidation.FILE_INGRESS_GEBURTSDATUM_MISSMATCH",
                "esadId",
                esadId
            ));
    }

    @NonNull
    public static AppValidationMessage fileIngressNoSuitableAnlass(
        @NonNull @IgnoreForIdClassTest String esadId
    ) {
        return of(
            "FileIngressNoSuitableAnlass",
            I18nMessage.of(
                "AppValidation.FILE_INGRESS_NO_SUITABLE_ANLASS",
                "esadId",
                esadId
            ));
    }
}
