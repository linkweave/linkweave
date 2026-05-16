package org.chainlink.api.collection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.chainlink.api.bookmark.property.BookmarkPropertyValue;
import org.chainlink.api.bookmark.property.BookmarkPropertyValueMapper;
import org.chainlink.api.bookmark.property.BookmarkPropertyValueService;
import org.chainlink.api.bookmark.property.PropertyDefinition;
import org.chainlink.api.bookmark.property.PropertyDefinitionMapper;
import org.chainlink.api.bookmark.property.PropertyDefinitionService;
import org.chainlink.api.bookmark.property.json.BookmarkPropertyValueJson;
import org.chainlink.api.shared.config.ConfigService;
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
    private final PropertyDefinitionService propertyDefinitionService;
    private final BookmarkPropertyValueService bookmarkPropertyValueService;
    private final ConfigService configService;

    public @NonNull CollectionInfoJson toCollectionInfoJson(@NonNull ID<Collection> collectionId) {
        Collection collection = collectionService.getCollection(collectionId);
        List<Bookmark> bookmarks = bookmarkService.getBookmarksByCollection(collectionId);
        List<PropertyDefinition> propertyDefinitions = loadPropertyDefinitions(collectionId);
        Map<ID<Bookmark>, List<BookmarkPropertyValueJson>> propsByBookmark = loadPropertyValueJsonsByBookmark(collectionId);

        return new CollectionInfoJson(
            collection.getId(),
            collection.getName(),
            collection.getFaviconAllowlist(),
            bookmarks.stream()
                .map(b -> BookmarkMapper.toJson(b, propsByBookmark.getOrDefault(b.getId(), List.of())))
                .toList(),
            folderService.getFoldersByCollection(collectionId).stream().map(FolderMapper::toJson).toList(),
            tagService.findByCollection(collectionId).stream().map(TagMapper::toJson).toList(),
            autoTagRuleService.findByCollection(collectionId).stream().map(AutoTagRuleMapper::toJson).toList(),
            propertyDefinitions.stream().map(PropertyDefinitionMapper::toJson).toList()
        );
    }

    private @NonNull List<PropertyDefinition> loadPropertyDefinitions(@NonNull ID<Collection> collectionId) {
        if (!configService.isBookmarkPropertiesEnabled()) {
            return List.of();
        }
        return propertyDefinitionService.findByCollection(collectionId);
    }

    private @NonNull Map<ID<Bookmark>, List<BookmarkPropertyValueJson>> loadPropertyValueJsonsByBookmark(
        @NonNull ID<Collection> collectionId
    ) {
        if (!configService.isBookmarkPropertiesEnabled()) {
            return Map.of();
        }
        Map<ID<Bookmark>, List<BookmarkPropertyValue>> valuesByBookmark =
            bookmarkPropertyValueService.findValuesByBookmarkForCollection(collectionId);
        return valuesByBookmark.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getValue().stream().map(BookmarkPropertyValueMapper::toJson).toList()
        ));
    }

    public static @NonNull CollectionInfoJson toCollectionInfoJson(
        Collection collection,
        List<Bookmark> bookmarks,
        List<Folder> folders,
        List<Tag> tags,
        List<AutoTagRule> autoTagRules,
        List<PropertyDefinition> propertyDefinitions
    ) {
        return new CollectionInfoJson(
            collection.getId(),
            collection.getName(),
            collection.getFaviconAllowlist(),
            bookmarks.stream().map(BookmarkMapper::toJson).toList(),
            folders.stream().map(FolderMapper::toJson).toList(),
            tags.stream().map(TagMapper::toJson).toList(),
            autoTagRules.stream().map(AutoTagRuleMapper::toJson).toList(),
            propertyDefinitions.stream().map(PropertyDefinitionMapper::toJson).toList()
        );
    }
}
