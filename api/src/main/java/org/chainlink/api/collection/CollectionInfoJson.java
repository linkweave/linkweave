package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.AutoTagRuleJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
public class CollectionInfoJson {

    @NotNull @NonNull ID<Collection> id;
    @NotNull @NonNull String name;
    @Nullable String faviconAllowlist;
    @NonNull List<BookmarkJson> bookmarks;
    @NonNull List<TagJson> tags;
    @NonNull List<FolderJson> folders;
    @NonNull List<AutoTagRuleJson> autoTagRules;

    public CollectionInfoJson(
        @NotNull @NonNull ID<Collection> id,
        @NotNull @NonNull String name,
        @Nullable String faviconAllowlist,
        List<BookmarkJson> bookmarks,
        List<FolderJson> folders,
        List<TagJson> tags,
        List<AutoTagRuleJson> autoTagRules) {

        this.id = id;
        this.name = name;
        this.faviconAllowlist = faviconAllowlist;
        this.folders = folders;
        this.bookmarks = bookmarks;
        this.tags = tags;
        this.autoTagRules = autoTagRules;
    }
}
