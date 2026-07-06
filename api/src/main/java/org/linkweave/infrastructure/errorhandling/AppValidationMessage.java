package org.linkweave.infrastructure.errorhandling;

import java.util.List;

import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.linkweave.api.types.id.ID;

@Value
@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public class AppValidationMessage {

    public static final String EMAIL = "email";
    // This key is sent to the client in the error response
    String clientKey;
    I18nMessage i18nMessage;
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
            I18nMessage.of("AppValidation.INVALID_EMAIL", EMAIL, email)
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

    public static @NonNull AppValidationMessage emailAlreadyRegistered(@NonNull String email) {
        return of(
            "EmailAlreadyRegistered",
            I18nMessage.of("AppValidation.EMAIL_ALREADY_REGISTERED", EMAIL, email)
        );
    }

    public static @NonNull AppValidationMessage cantDeleteLastCollection() {
        return of(
            "CanNotDeleteLastCollection",
            I18nMessage.of("AppValidation.CANT_DELETE_LAST_COLLECTION")
        );
    }

    public static @NonNull AppValidationMessage shareUserNotFound(@NonNull String email) {
        return of(
            "ShareUserNotFound",
            I18nMessage.of("AppValidation.SHARE_USER_NOT_FOUND", EMAIL, email)
        );
    }

    public static @NonNull AppValidationMessage shareCannotShareWithSelf() {
        return of(
            "ShareCannotShareWithSelf",
            I18nMessage.of("AppValidation.SHARE_CANNOT_SHARE_WITH_SELF")
        );
    }

    public static @NonNull AppValidationMessage shareAlreadyHasAccess() {
        return of(
            "ShareAlreadyHasAccess",
            I18nMessage.of("AppValidation.SHARE_ALREADY_HAS_ACCESS")
        );
    }

    public static @NonNull AppValidationMessage shareCannotAssignOwner() {
        return of(
            "ShareCannotAssignOwner",
            I18nMessage.of("AppValidation.SHARE_CANNOT_ASSIGN_OWNER")
        );
    }

    public static @NonNull AppValidationMessage cannotRevokeOwner() {
        return of(
            "CannotRevokeOwner",
            I18nMessage.of("AppValidation.CANNOT_REVOKE_OWNER")
        );
    }

    public static @NonNull AppValidationMessage cannotChangeOwnerRole() {
        return of(
            "CannotChangeOwnerRole",
            I18nMessage.of("AppValidation.CANNOT_CHANGE_OWNER_ROLE")
        );
    }

    public static @NonNull AppValidationMessage collectionMemberNotFound() {
        return of(
            "CollectionMemberNotFound",
            I18nMessage.of("AppValidation.COLLECTION_MEMBER_NOT_FOUND")
        );
    }

    public static @NonNull AppValidationMessage propertyDefinitionCollectionMismatch(
        @NonNull ID<?> propertyDefinitionId,
        @NonNull ID<?> bookmarkCollectionId,
        @NonNull ID<?> definitionCollectionId
    ) {
        return of(
            "PropertyDefinitionCollectionMismatch",
            I18nMessage.of(
                "AppValidation.PROPERTY_DEFINITION_COLLECTION_MISMATCH",
                "propertyDefinitionId", propertyDefinitionId,
                "bookmarkCollectionId", bookmarkCollectionId,
                "definitionCollectionId", definitionCollectionId
            )
        );
    }

    public static @NonNull AppValidationMessage browserFetchAllowlistInvalidPattern(@NonNull String pattern) {
        return of(
            "BrowserFetchAllowlistInvalidPattern",
            I18nMessage.of("AppValidation.BROWSER_FETCH_ALLOWLIST_INVALID_PATTERN", "pattern", pattern)
        );
    }

    public static @NonNull AppValidationMessage maxApiKeysReached() {
        return of(
            "MaxApiKeysReached",
            I18nMessage.of("AppValidation.MAX_API_KEYS_REACHED")
        );
    }

    public static @NonNull AppValidationMessage apiKeyAlreadyRevoked() {
        return of(
            "ApiKeyAlreadyRevoked",
            I18nMessage.of("AppValidation.API_KEY_ALREADY_REVOKED")
        );
    }
}
