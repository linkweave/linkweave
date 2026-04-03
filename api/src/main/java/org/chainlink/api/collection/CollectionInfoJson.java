package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@JaxDTO
public class CollectionInfoJson {

    @NonNull ID<Collection> id;
    @NonNull String name;
    List<BookmarkJson> bookmarks;
    List<TagJson> tags;
    List<FolderJson> folders;

    public CollectionInfoJson(
        @NonNull ID<Collection> id,
        @NonNull String name,
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
