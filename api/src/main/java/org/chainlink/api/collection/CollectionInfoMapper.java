package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.AutoTagRule;
import org.chainlink.api.bookmark.AutoTagRuleMapper;
import org.chainlink.api.bookmark.AutoTagRuleService;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkMapper;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagMapper;
import org.chainlink.api.bookmark.TagService;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.chainlink.api.bookmark.folder.FolderService;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class CollectionInfoMapper {

    private final CollectionService collectionService;
    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final TagService tagService;
    private final AutoTagRuleService autoTagRuleService;

    public @NonNull CollectionInfoJson toCollectionInfoJson(@NonNull ID<Collection> collectionId) {
        return toCollectionInfoJson(
            collectionService.getCollection(collectionId),
            bookmarkService.getBookmarksByCollection(collectionId),
            folderService.getFoldersByCollection(collectionId),
            tagService.findByCollection(collectionId),
            autoTagRuleService.findByCollection(collectionId)
        );
    }

    public static @NonNull CollectionInfoJson toCollectionInfoJson(
        Collection collection,
        List<Bookmark> bookmarks,
        List<Folder> folders,
        List<Tag> tags,
        List<AutoTagRule> autoTagRules
    ) {
        return new CollectionInfoJson(
            collection.getId(),
            collection.getName(),
            collection.getFaviconAllowlist(),
            bookmarks.stream().map(BookmarkMapper::toJson).toList(),
            folders.stream().map(FolderMapper::toJson).toList(),
            tags.stream().map(TagMapper::toJson).toList(),
            autoTagRules.stream().map(AutoTagRuleMapper::toJson).toList()
        );
    }
}
