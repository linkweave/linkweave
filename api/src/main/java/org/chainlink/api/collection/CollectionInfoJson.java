package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.AutoTagRuleJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.api.bookmark.property.json.PropertyDefinitionJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
public class CollectionInfoJson {

    @NotNull @NonNull ID<Collection> id;
    @NotNull @NonNull String name;
    @Nullable String browserFetchAllowlist;
    @Schema(required = true) boolean screenshotEnabled;
    @NotNull @NonNull List<BookmarkJson> bookmarks;
    @NotNull @NonNull List<TagJson> tags;
    @NotNull @NonNull List<FolderJson> folders;
    @NotNull @NonNull List<AutoTagRuleJson> autoTagRules;
    @NotNull @NonNull List<PropertyDefinitionJson> propertyDefinitions;

    public CollectionInfoJson(
        @NotNull @NonNull ID<Collection> id,
        @NotNull @NonNull String name,
        @Nullable String browserFetchAllowlist,
        boolean screenshotEnabled,
        @NonNull List<BookmarkJson> bookmarks,
        @NonNull List<FolderJson> folders,
        @NonNull List<TagJson> tags,
        @NonNull List<AutoTagRuleJson> autoTagRules,
        @NonNull List<PropertyDefinitionJson> propertyDefinitions) {

        this.id = id;
        this.name = name;
        this.browserFetchAllowlist = browserFetchAllowlist;
        this.screenshotEnabled = screenshotEnabled;
        this.folders = folders;
        this.bookmarks = bookmarks;
        this.tags = tags;
        this.autoTagRules = autoTagRules;
        this.propertyDefinitions = propertyDefinitions;
    }
}
