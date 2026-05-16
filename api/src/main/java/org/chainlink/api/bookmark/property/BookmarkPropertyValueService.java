package org.chainlink.api.bookmark.property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.bookmark.property.json.BookmarkPropertyValueJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class BookmarkPropertyValueService {

    private final BookmarkRepo bookmarkRepo;
    private final BookmarkPropertyValueRepo bookmarkPropertyValueRepo;
    private final PropertyDefinitionRepo propertyDefinitionRepo;

    @NonNull
    public Bookmark getBookmark(@NonNull ID<Bookmark> bookmarkId) {
        return bookmarkRepo.getById(bookmarkId);
    }

    @NonNull
    public Map<ID<Bookmark>, List<BookmarkPropertyValue>> findValuesByBookmarkForCollection(
        @NonNull ID<Collection> collectionId
    ) {
        return bookmarkPropertyValueRepo.findByCollection(collectionId).stream()
            .collect(Collectors.groupingBy(pv -> pv.getBookmark().getId()));
    }

    @NonNull
    public Bookmark replacePropertyValues(@NonNull Bookmark bookmark, @NonNull List<BookmarkPropertyValueJson> values) {
        Map<ID<BookmarkPropertyValue>, BookmarkPropertyValue> existingById = new HashMap<>();
        for (BookmarkPropertyValue existing : bookmark.getPropertyValues()) {
            existingById.put(existing.getId(), existing);
        }

        Set<ID<BookmarkPropertyValue>> keptIds = new HashSet<>();
        List<BookmarkPropertyValueJson> toCreate = new ArrayList<>();

        for (BookmarkPropertyValueJson pvJson : values) {
            PropertyDefinition def = propertyDefinitionRepo.getById(pvJson.getDefinitionId());
            if (!def.getCollectionId().equals(bookmark.getCollectionId())) {
                throw new AppValidationException(AppValidationMessage.propertyDefinitionCollectionMismatch(
                    def.getId(),
                    bookmark.getCollectionId(),
                    def.getCollectionId()
                ));
            }

            if (pvJson.getId() != null && existingById.containsKey(pvJson.getId())) {
                BookmarkPropertyValue pv = existingById.get(pvJson.getId());
                pv.setPropertyDefinition(def);
                pv.setValueText(pvJson.getValueText());
                pv.setValueNumber(pvJson.getValueNumber());
                pv.setValueBoolean(pvJson.isValueBoolean());
                keptIds.add(pv.getId());
            } else {
                toCreate.add(pvJson);
            }
        }

        boolean hadRemovals = bookmark.getPropertyValues()
            .removeIf(pv -> !keptIds.contains(pv.getId()));

        // Ensure orphan deletes are flushed before inserts so the
        // (bookmark_id, propertyDefinition_id) unique constraint can't trip
        // when the same definition appears in both the old and new value sets.
        if (hadRemovals && !toCreate.isEmpty()) {
            bookmarkPropertyValueRepo.flush();
        }

        for (BookmarkPropertyValueJson pvJson : toCreate) {
            PropertyDefinition def = propertyDefinitionRepo.getById(pvJson.getDefinitionId());

            BookmarkPropertyValue propVal = new BookmarkPropertyValue(
                bookmark,
                def,
                pvJson.getValueText(),
                pvJson.getValueNumber(),
                pvJson.isValueBoolean()
            );
            bookmark.getPropertyValues().add(propVal);
        }

        return bookmark;
    }
}
