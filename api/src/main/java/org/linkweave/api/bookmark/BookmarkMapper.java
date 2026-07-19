package org.linkweave.api.bookmark;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.BookmarkJson;
import org.linkweave.api.bookmark.json.BookmarkSaveJson;
import org.linkweave.api.bookmark.property.BookmarkPropertyValueMapper;
import org.linkweave.api.bookmark.property.json.BookmarkPropertyValueJson;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class BookmarkMapper {

    @NonNull
    public static BookmarkJson toJson(@NonNull Bookmark bookmark) {
        return toJson(bookmark, List.of());
    }

    @NonNull
    public static BookmarkJson toJsonWithProperties(@NonNull Bookmark bookmark) {
        List<BookmarkPropertyValueJson> propertyValues = bookmark.getPropertyValues()
            .stream()
            .map(BookmarkPropertyValueMapper::toJson)
            .toList();
        return toJson(bookmark, propertyValues);
    }

    @NonNull
    public static BookmarkJson toJson(@NonNull Bookmark bookmark, @NonNull List<BookmarkPropertyValueJson> propertyValues) {
        return new BookmarkJson(
            bookmark.getId(),
            EntityInfoJson.fromEntity(bookmark),
            new BookmarkSaveJson(
                bookmark.getCollection().getId(),
                bookmark.getFolder() != null ? Objects.requireNonNull(bookmark.getFolder()).getId() : null,
                bookmark.getTitle(),
                bookmark.getUrl().toString(),
                bookmark.getDescription(),
                bookmark.getTags().stream()
                    .map(Tag::getId)
                    .collect(Collectors.toSet())
            ),
            bookmark.getSortOrder(),
            bookmark.getClickCount(),
            bookmark.getLastClickedAt(),
            bookmark.getDeletedAt(),
            propertyValues
        );
    }
}
