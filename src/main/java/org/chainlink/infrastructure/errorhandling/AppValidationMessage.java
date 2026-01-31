package org.chainlink.infrastructure.errorhandling;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import ch.dvbern.dvbstarter.types.id.ID;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public class AppValidationMessage {

    // This key is sent to the client in the error response
    private final String clientKey;
    private final I18nMessage i18nMessage;
    private static final String ENTITY_ID = "entityId";


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

}
