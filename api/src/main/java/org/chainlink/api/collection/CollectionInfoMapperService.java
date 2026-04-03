package org.chainlink.api.collection;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkMapper;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagMapper;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.infrastructure.stereotypes.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionInfoMapperService {

    public CollectionInfoJson toCollectionInfoJson(
        Collection collection,
        List<Bookmark> bookmarks,
        List<Folder> folders,
        List<Tag> tags
    ) {
        return new CollectionInfoJson(
            collection.getId(),
            collection.getName(),
            bookmarks.stream().map(BookmarkMapper::toJson).toList(),
            folders.stream().map(FolderMapper::toJson).toList(),
            tags.stream().map(TagMapper::toJson).toList()
        );
        }
    }
}
