package org.chainlink.api.bookmark;

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
                bookmark.collection.getId(),
                bookmark.folder != null ? bookmark.folder.getId() : null,
                bookmark.title,
                bookmark.url.toString(),
                bookmark.description,
                bookmark.tags.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toSet())
            )
        );
    }
}
