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
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
public class CollectionInfoJson {

    @NotNull @NonNull @Schema(required = true) ID<Collection> id;
    @NotNull @NonNull @Schema(required = true) String name;
    @Nullable @Schema(required = false) String faviconAllowlist;
    @Schema(required = true) List<BookmarkJson> bookmarks;
    @Schema(required = true) List<TagJson> tags;
    @Schema(required = true) List<FolderJson> folders;
    @Schema(required = true) List<AutoTagRuleJson> autoTagRules;

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
