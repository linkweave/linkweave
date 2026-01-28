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

package org.chainlink.infrastructure.fileio;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import ch.dvbern.dvbstarter.shared.types.cleanfilename.CleanFileName;
import ch.dvbern.oss.hemed.esc.api.shared.fileio.FileInfo;
import ch.dvbern.oss.hemed.esc.api.shared.fileio.FileStoreService;
import com.google.common.net.UrlEscapers;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import mutiny.zero.flow.adapters.AdaptersToFlow;
import org.apache.tika.mime.MediaType;
import org.jspecify.annotations.NonNull;
import org.jboss.resteasy.reactive.RestMulti;

/**
 * Utility class to construct file download responses, either Multi<Buffer> or Response
 */
@UtilityClass
public class FileDownloadUtil {

    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String FILENAME = ";filename=";
    public static final String MIME_TYPE_DOCX = "vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_TYPE_XLSX = "vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @NonNull
    public static Multi<Buffer> getBufferRestMultiWithInfo(
        @NonNull FileInfo fileInfoBucket,
        @NonNull FileStoreService fileStoreService,
        @NonNull String disposition
    ) {
        String fileId = fileInfoBucket.getFileIdInBucket();
        final String filename = urlEscapeAndQuoteFilename(fileInfoBucket.getFileName().getFileName());
        final String contentType = fileInfoBucket.getMediaType().toString();

        return RestMulti.fromUniResponse(
            Uni.createFrom().completionStage(() -> fileStoreService.getFileAsync(fileId)),
            response -> Multi.createFrom()
                .safePublisher(AdaptersToFlow.publisher(response))
                .map(FileStoreService::toBuffer),
            response -> Map.of(
                CONTENT_DISPOSITION,
                List.of(disposition + FILENAME + filename),
                "Content-Type",
                List.of(contentType)));
    }

    /**
     * Create the response with the file from the storage but keep the original filename and content type
     */
    public Multi<Buffer> getBufferMultiWithInfo(
        @NonNull FileInfo fileInfoBucket,
        @NonNull FileStoreService fileStoreService,
        @NonNull String disposition
    ) {
        String fileId = fileInfoBucket.getFileIdInBucket();
        String filename = urlEscapeAndQuoteFilename(fileInfoBucket.getFileName().getFileName());
        final String contentType = fileInfoBucket.getMediaType().toString();

        return Uni.createFrom().completionStage(() -> fileStoreService.getFileAsync(fileId))
            .onItem()
            .transformToMulti(responsePublisher -> RestMulti
                .fromMultiData(Multi.createFrom().safePublisher(AdaptersToFlow.publisher(responsePublisher))
                    .map(FileStoreService::toBuffer))
                .header(CONTENT_DISPOSITION, disposition + FILENAME + filename)
                .header("Content-Type", contentType)
                .build());
    }

    @NonNull
    public static Multi<Buffer> getBufferRestMultiWithInfo(
        @NonNull FileInfo fileInfoBucket,
        @NonNull FileStoreService fileStoreService
    ) {
        return getBufferRestMultiWithInfo(fileInfoBucket, fileStoreService, "attachment");
    }

    @NonNull
    public static Response buildFileResponse(
        @NonNull String disposition,
        byte[] content,
        @NonNull FileInfo fileInfoBucket
    ) {

        String mimeType = fileInfoBucket.getMediaType().toString();

        String filename = urlEscapeAndQuoteFilename(fileInfoBucket.getFileName().getFileName());
        String contentDispositionHeaderValue = disposition + FILENAME + filename;

        return Response
            .ok(content, mimeType)
            .header(CONTENT_DISPOSITION, contentDispositionHeaderValue)
            // note: content-length is not allowed if transfer is chunked
            // ... which is used for most downloads by Jackson
            //.header(HttpHeaders.CONTENT_LENGTH, content.length)
            .build();
    }

    @NonNull
    private String urlEscapeAndQuoteFilename(@NonNull String filename) {
        return '"' + UrlEscapers.urlFragmentEscaper().escape(filename) + '"';
    }

    @NonNull
    public static FileInfo buildFileInfo(@NonNull String fileName, @NonNull String mimeType, long fileSize) {
        return new FileInfo() {
            @Serial
            private static final long serialVersionUID = -2829730724238936664L;

            @Override
            public CleanFileName getFileName() {
                return CleanFileName.parse(fileName);
            }

            @Override
            public MediaType getMediaType() {
                return MediaType.application(mimeType);
            }

            @Override
            public long getFileSize() {
                return fileSize;
            }
        };
    }
}
