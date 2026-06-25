package org.linkweave.api.bookmark.importbookmarks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jspecify.annotations.NonNull;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.infrastructure.errorhandling.AppFailureException;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.JaxResource;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/import")
public class ImportResource {

    private static final long MAX_FILE_SIZE = 5 * 1024L * 1024;

    private final BookmarkImportService bookmarkImportService;
    private final ImportReviewService importReviewService;
    private final AuthorizationService authorizationService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public ImportSummaryJson importBookmarks(
        @PathParam("collectionId") @NotNull @NonNull ID<Collection> collectionId,
        @NotNull @NonNull @RestForm("file") FileUpload file
    ) {
        authorizationService.requireCollectionAccess(collectionId);

        validateFile(file);

        try (InputStream inputStream = Files.newInputStream(file.uploadedFile())) {
            return bookmarkImportService.importBookmarks(collectionId, inputStream, file.fileName());
        } catch (IOException e) {
            throw new AppFailureException(
                AppFailureMessage.internalError("Failed to read uploaded file: " + e.getMessage()));
        }
    }

    /**
     * Phase 1 of reviewed import (UC-096): parse the file into a manifest and
     * flag duplicates, without writing anything.
     */
    @POST
    @Path("/preview")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public ImportPreviewJson previewImport(
        @PathParam("collectionId") @NotNull @NonNull ID<Collection> collectionId,
        @NotNull @NonNull @RestForm("file") FileUpload file
    ) {
        authorizationService.requireCollectionAccess(collectionId);

        validateFile(file);

        try (InputStream inputStream = Files.newInputStream(file.uploadedFile())) {
            return importReviewService.preview(collectionId, inputStream);
        } catch (IOException e) {
            throw new AppFailureException(
                AppFailureMessage.internalError("Failed to read uploaded file: " + e.getMessage()));
        }
    }

    /**
     * Phase 2 of reviewed import (UC-096): write only the folders/bookmarks the
     * user kept, merging folders by path.
     */
    @POST
    @Path("/commit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public ImportCommitResultJson commitImport(
        @PathParam("collectionId") @NotNull @NonNull ID<Collection> collectionId,
        @NotNull @NonNull @Valid ImportCommitRequestJson request
    ) {
        authorizationService.requireCollectionAccess(collectionId);

        return importReviewService.commit(collectionId, request);
    }

    private void validateFile(@NonNull FileUpload file) {
        String fileName = file.fileName();
        if (fileName == null || (!fileName.endsWith(".html") && !fileName.endsWith(".htm"))) {
            throw new AppValidationException(
                AppValidationMessage.invalidFileType(fileName != null ? fileName : "null"));
        }

        if (file.size() == 0) {
            throw new AppValidationException(
                AppValidationMessage.uploadProblem("Please upload a valid bookmarks HTML file."));
        }

        if (file.size() > MAX_FILE_SIZE) {
            throw new AppValidationException(
                AppValidationMessage.uploadProblem("File size exceeds 5 MB limit."));
        }
    }
}
