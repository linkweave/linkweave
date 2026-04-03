package org.chainlink.api.bookmark;

import java.util.Collections;
import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.TagSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final String CONSTRAINT_UQ_TAG_NAME_COLLECTION = "uq_tag_name_collection";

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
        try {
            tagRepo.persistAndFlush(tag);
        } catch (ConstraintViolationException e) {
            if (CONSTRAINT_UQ_TAG_NAME_COLLECTION.equals(e.getConstraintName())) {
                throw new AppValidationException(AppValidationMessage.genericMessage(
                    "AppValidation.uq_tag_name_collection"));
            }
            throw e;
        }
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
}
