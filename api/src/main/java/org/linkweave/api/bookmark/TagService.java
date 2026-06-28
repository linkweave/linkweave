package org.linkweave.api.bookmark;

import java.util.Collections;
import java.util.List;

import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.TagSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.infrastructure.db.UniqueConstraintUtil;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final String CONSTRAINT_NAME = "uc_tag_name_collection";
    private static final String CONSTRAINT_COLUMNS = "Tag.name, Tag.collection_id";

    private final TagRepo tagRepo;
    private final CollectionRepo collectionRepo;
    private final BookmarkRepo bookmarkRepo;

    @NonNull
    public Tag createTag(@NonNull TagSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();
        int existingCount = tagRepo.findByCollection(collectionId).size();

        Tag tag = new Tag(
            collectionRepo.referenceById(collectionId),
            json.getName(),
            json.getColor() != null ? json.getColor() : TagColorPalette.autoAssignColor(existingCount),
            Collections.emptySet()
        );
        upsertTagAndFlush(tag);
        return tag;
    }

    @NonNull
    public Tag getTag(@NonNull ID<Tag> id) {
        return tagRepo.getById(id);
    }


    @NonNull
    public Tag updateTag(@NonNull Tag tag, @NonNull TagSaveJson json) {
        tag.setCollection(collectionRepo.referenceById(json.getCollectionId()));
        tag.setName(json.getName());
        if (json.getColor() != null) {
            tag.setColor(json.getColor());
        }
        upsertTagAndFlush(tag);
        return tag;
    }

    private void upsertTagAndFlush(Tag tag) {
        UniqueConstraintUtil.persistAndHandleUnique(
            () -> tagRepo.persistAndFlush(tag),
            CONSTRAINT_NAME,
            CONSTRAINT_COLUMNS,
            "AppValidation.uq_tag_name_collection"
        );
    }

    public void removeTag(@NonNull ID<Tag> id) {
        Tag tag = tagRepo.getById(id);
        List<Bookmark> bookmarksWithTag = bookmarkRepo.findByTag(tag);
        for (Bookmark bookmark : bookmarksWithTag) {
            bookmark.getTags().remove(tag);
        }
        tagRepo.remove(id);
    }

    public List<Tag> findByCollection(@NonNull ID<Collection> collectionID) {
        return  tagRepo.findByCollection(collectionID);
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        tagRepo.deleteByCollection(collectionId);
    }
}
