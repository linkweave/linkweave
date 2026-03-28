package org.chainlink.infrastructure.exceptionmapper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class JsonMappingReferenceParser {

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class Entry {
        private @Nullable String fieldName;
        private @Nullable Integer index;

        public String toPathEntry() {
            if (getIndex() != null) {
                return Integer.toString(getIndex());
            }

            return Objects.requireNonNull(getFieldName());
        }
    }

    private final List<Entry> entries;

    public static JsonMappingReferenceParser fromRefrenceList(List<Reference> referenceList) {
        var entries = referenceList.stream()
            .map(reference -> new Entry(
                    reference.getFieldName(),
                    reference.getIndex() == -1
                        ? null
                        : reference.getIndex()
                )
            )
            .toList();

        return new JsonMappingReferenceParser(entries);
    }

    public String parse() {
        var result = entries.stream()
            .map(Entry::toPathEntry)
            .collect(Collectors.joining("."));

        return result;
    }

}
