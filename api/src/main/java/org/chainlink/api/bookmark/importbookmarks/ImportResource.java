package org.chainlink.api.bookmark.importbookmarks;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/import")
public class ImportResource {

    private static final long MAX_FILE_SIZE = 5 * 1024L * 1024;

    private final BookmarkImportService bookmarkImportService;
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
