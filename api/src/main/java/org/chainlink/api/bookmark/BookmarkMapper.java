package org.chainlink.api.bookmark;

import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.BookmarkSaveJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class BookmarkMapper {

    @NonNull
    public static BookmarkJson toJson(@NonNull Bookmark bookmark) {
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
            bookmark.getClickCount(),
            bookmark.getLastClickedAt(),
            bookmark.getDeletedAt()
        );
    }
}
