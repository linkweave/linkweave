package org.linkweave.api.bookmark.property;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.property.json.BookmarkPropertyValueJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class BookmarkPropertyValueMapper {

    @NonNull
    public static BookmarkPropertyValueJson toJson(@NonNull BookmarkPropertyValue value) {
        return new BookmarkPropertyValueJson(
            value.getId(),
            value.getPropertyDefinition().getId(),
            value.getValueText(),
            value.getValueNumber(),
            Boolean.TRUE.equals(value.getValueBoolean())
        );
    }
}
