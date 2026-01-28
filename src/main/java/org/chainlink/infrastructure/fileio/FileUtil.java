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

package org.chainlink.infrastructure.fileio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationMessage;
import ch.dvbern.dvbstarter.shared.types.cleanfilename.CleanFileName;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.Validate;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.jspecify.annotations.NonNull;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@UtilityClass
@Slf4j
public class FileUtil {
    private static final Tika TIKA = new Tika();

    @NonNull
    public static String generateUUIDWithFileExtension(@NonNull CleanFileName filename) {
        return UUID.randomUUID() + "." + FilenameUtils.getExtension(filename.getFileName());
    }

    @NonNull
    public static String generateUUIDWithFileExtension(@NonNull String prefix, @NonNull CleanFileName filename) {
        return prefix + '-' + UUID.randomUUID() + '.' + FilenameUtils.getExtension(filename.getFileName());
    }

    @NonNull
    public static String generateUUIDWithOriginalFilename(@NonNull String prefix, @NonNull CleanFileName filename) {
        return prefix + '-' + UUID.randomUUID() + "__" + filename.getFileName();
    }

    @NonNull
    public static String extractOriginalFileNameFromFileBucketId(@NonNull String fileBucketIdentifier) {
        // find the first occurence of doubled underscore and return rest of the string
        return fileBucketIdentifier.substring(fileBucketIdentifier.indexOf("__") + 2);
    }

    public static boolean mediaTypeIsAllowed(@NonNull FileUpload uplaodFile, @NonNull Set<MediaType> mediaTypes) {
        var contentMediaType = parseMediaTypeFromContent(uplaodFile.filePath());
        // pass original file name for content type to filename matching
        ensureFileExtensionMatchesContentType(CleanFileName.parse(uplaodFile.fileName()), contentMediaType);

        boolean allowedType = mediaTypes.contains(contentMediaType);
        if (!allowedType) {
            LOG.warn(
                "Unsupported file extension provided for uploaded file '{}' with type {}. \nAllowed are {}",
                uplaodFile.fileName(),
                contentMediaType,
                mediaTypes);
        }
        return allowedType;
    }

    /**
     * Checks if the filename extension matches the actual content type. Throws if not
     *
     * @throws AppFailureException if the file extension does not match the content type
     */
    private static void ensureFileExtensionMatchesContentType(
        @NonNull CleanFileName filename,
        @NonNull MediaType mediaTypeFromContent
    ) {

        var filenameMediaType = parseMediaTypeFromFilename(filename);
        if (filenameMediaType.equals(mediaTypeFromContent)) {
            return;
        }
        // csv is a special case since content matching may produce text/plain instead of text/csv, we can tolerate this
        if (filenameMediaType.equals(MediaType.parse("text/csv"))
            && (
            mediaTypeFromContent.equals(MediaType.parse("text/plain"))
                || mediaTypeFromContent.equals(MediaType.parse("application/octet-stream"))
        )
        ) {
            return;
        }

        String msg =
            "File extension of file '%s' does not match content type: '%s'. Expected due to file extension: '%s'"
                .formatted(filename, mediaTypeFromContent, filenameMediaType);

        LOG.debug(msg);
        throw new AppValidationException(AppValidationMessage.uploadProblem(msg));
    }

    @NonNull
    private static MediaType parseMediaTypeFromFilename(@NonNull CleanFileName filename) {
        String mediaType = TIKA.detect(filename.getFileName());
        LOG.debug("Detected mediaType from file-content={}, from filename={}", mediaType, filename);

        return MediaType.parse(mediaType);
    }

    @NonNull
    public static MediaType parseMediaTypeFromContent(@NonNull Path path) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(path)) {
            String mediaType = TIKA.detect(tikaInputStream);
            LOG.debug("Detected mediaType from file-content={}, from filename={}", mediaType, path);

            return MediaType.parse(mediaType);
        } catch (IOException e) {
            String text = "checkFileExtension failed: " + e.getMessage();
            throw new AppFailureException(AppFailureMessage.internalError(text));
        }
    }

    @NonNull
    public static MediaType parseMediaTypeOrFileExtensionToMediaType(@NonNull String mediaTypeOrFileExtension) {
        if (mediaTypeOrFileExtension.contains("/")) {
            return MediaType.parse(mediaTypeOrFileExtension);
        }
        return getMediaTypeFromExtension(mediaTypeOrFileExtension);
    }

    /**
     * Gets the MediaType for a given file extension using Apache Tika
     *
     * @param extension file extension (with or without leading dot)
     * @return MediaType object, or null if extension is not recognized
     */
    @NonNull
    static MediaType getMediaTypeFromExtension(@NonNull String extension) {
        String trimmedExtension = extension.trim();
        Validate.isTrue(trimmedExtension.startsWith("."), "Extension must start with a dot");

        String detect = TIKA.detect("File" + extension);
        return MediaType.parse(detect);

    }


    @NonNull
    public static InputStream createInputStreamFromPath(@NonNull Path fileUpload) {
        try {
            return Files.newInputStream(fileUpload);
        } catch (IOException e) {
            throw new AppFailureException(AppFailureMessage.internalError("Could not read the uploaded file"), e);
        }
    }


    @NonNull
    public static Reader createReaderFromPath(@NonNull Path fileUpload) {
        Reader reader;
        try {
            InputStream is = Files.newInputStream(fileUpload);
            reader = createReaderFromInputStream(is);
        } catch (IOException e) {
            throw new AppFailureException(AppFailureMessage.internalError("Could not read the uploaded file"), e);
        }
        return reader;
    }

    @NonNull
    public static Reader createReaderFromInputStream(@NonNull InputStream is) {
        Reader reader;
        try {
            // BOMInputStream will skip the BOM if present
            BOMInputStream bomIn = BOMInputStream.builder()
                .setInputStream(is)
                .setInclude(false)
                .get();
            reader = new BufferedReader(new InputStreamReader(bomIn, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AppFailureException(AppFailureMessage.internalError("Could not read the file"), e);
        }
        return reader;
    }

    @NonNull
    public static String readTextFromPath(@NonNull Path filePath) {
        return readTextFromPath(filePath, "\n");
    }

    @NonNull
    public static String readTextFromPath(@NonNull Path filePath, @NonNull String lineBreak) {
        try (Reader reader = createReaderFromPath(filePath);
            BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                content.append(line).append(lineBreak); // always append newline, even if line is empty
            }
            return content.toString();
        } catch (IOException e) {
            throw new AppFailureException(AppFailureMessage.internalError("Could not read the text from file"), e);
        }
    }

    @NonNull
    public static List<String> readLineBasedTextFromPath(@NonNull Path filePath) {
        List<String> result = new ArrayList<>();
        try (Reader reader = createReaderFromPath(filePath);
            BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.add(line);
            }
            return result;
        } catch (IOException e) {
            throw new AppFailureException(AppFailureMessage.internalError("Could not read the text from file"), e);
        }
    }

    public static FileUpload fileToFileUpload(@NonNull Path path, @NonNull String originalFilename) {
        return new FileUpload() {
            @Override
            public String name() {
                return originalFilename;
            }

            @Override
            public Path filePath() {
                return path;
            }

            @Override
            public String fileName() {
                return originalFilename;
            }

            @Override
            public long size() {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    throw new AppFailureException(
                        AppFailureMessage.internalError(
                            "Could not read the size of the File during conversion to FileUpload" +
                                " for file %s".formatted(path)
                        ), e);
                }
            }

            @Override
            public String contentType() {
                return FileUtil.parseMediaTypeFromContent(path).toString();
            }

            @Override
            public String charSet() {
                return Charset.defaultCharset().name();
            }
        };
    }
}
