package org.chainlink.api.collection;

import java.util.List;

import org.chainlink.api.bookmark.AutoTagRule;
import org.chainlink.api.bookmark.AutoTagRuleMapper;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkMapper;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagMapper;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.chainlink.infrastructure.stereotypes.JaxMapper;

@JaxMapper
public class CollectionInfoMapper {

    public static CollectionInfoJson toCollectionInfoJson(
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
