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

import java.util.Set;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationMessage;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MediaType;
import org.jspecify.annotations.NonNull;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@UtilityClass
public final class FileUploadValidationUtil {

    public static void validateNameAndType(@NonNull FileUpload fileUpload, @NonNull Set<MediaType> whitelist) {
        validateContentTypeProvided(fileUpload);
        validateFilenameProvided(fileUpload);
        validateMimeTypeAllowed(fileUpload, whitelist);
    }

	static void validateMimeTypeAllowed(@NonNull FileUpload fileUpload, @NonNull Set<MediaType> whitelist) {
		if (!FileUtil.mediaTypeIsAllowed(fileUpload, whitelist)) {
            throw new AppValidationException(AppValidationMessage.invalidFileType(fileUpload.contentType()));
		}
	}

	static void validateContentTypeProvided(@NonNull FileUpload fileUpload) {
		if (fileUpload.contentType() == null || fileUpload.contentType().isEmpty()) {
			String msg = "No contentType provided for uploaded file";
			LOG.warn(msg);
            throw new AppValidationException(AppValidationMessage.uploadProblem(msg));
		}
	}

	static void validateFilenameProvided(@NonNull FileUpload fileUpload) {
		if (fileUpload.fileName() == null || fileUpload.fileName().isEmpty()) {
			String msg = "No filename provided for uploaded file";
			LOG.warn(msg);
            throw new AppValidationException(AppValidationMessage.uploadProblem(msg));
		}
	}
}
