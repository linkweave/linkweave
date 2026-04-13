package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
public class CollectionInfoJson {

    @NotNull @NonNull ID<Collection> id;
    @NotNull @NonNull String name;
    List<BookmarkJson> bookmarks;
    List<TagJson> tags;
    List<FolderJson> folders;

    public CollectionInfoJson(
        @NotNull @NonNull ID<Collection> id,
        @NotNull @NonNull String name,
        List<BookmarkJson> bookmarks,
        List<FolderJson> folders,
        List<TagJson> tags) {

        this.id = id;
        this.name = name;
        this.folders = folders;
        this.bookmarks = bookmarks;
        this.tags = tags;

    }
}
