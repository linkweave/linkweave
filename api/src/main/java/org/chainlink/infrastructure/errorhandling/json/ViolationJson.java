package org.chainlink.infrastructure.errorhandling.json;

import java.util.List;
import java.util.stream.Collectors;

import org.chainlink.infrastructure.stereotypes.JaxDTO;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JaxDTO
public class ViolationJson {

    @NonNull
    @NotNull
    @Schema(required = true)
    private final String path;

    @NonNull
    @NotNull
    @Schema(required = true)
    private final String key;

    @NonNull
    @NotNull
    @Schema(required = true)
    private final String message;


    @NonNull
    public static ViolationJson of(
        @NonNull String path,
        @NonNull String key,
        @NonNull String message
    ) {
        return new ViolationJson(path, key, message);
    }

    @NonNull
    public static ViolationJson fromReferenceList(
        @NonNull String message,
        @NonNull String key,
        @NonNull List<Reference> referenceList
    ) {
        return of(
            buildPath(referenceList),
            key,
            message
        );
    }

    @NonNull
    private static String buildPath(@NonNull List<Reference> referenceList) {
        var result = referenceList.stream()
            .map(ViolationJson::toPathEntry)
            .collect(Collectors.joining("."));
        return result;
    }

    @NonNull
    private static String toPathEntry(@NonNull Reference reference) {
        if (reference.getIndex() < 0) {
            return reference.getFieldName();
        }
        return Integer.toString(reference.getIndex());
    }
}
