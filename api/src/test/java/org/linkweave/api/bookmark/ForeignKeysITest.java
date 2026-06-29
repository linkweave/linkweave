package org.linkweave.api.bookmark;

import java.util.Collections;
import java.util.UUID;

import org.linkweave.api.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.testutil.builder.BookmarkBuilder;
import org.linkweave.api.testutil.builder.CollectionBuilder;
import org.linkweave.api.testutil.builder.TagBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@AllArgsConstructor
class ForeignKeysITest {

    private final EntityManager em;
    private final TagRepo tagRepo;
    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final CurrentUserService currentUserService;

    @Test
    @Transactional
    @TestSecurity(user = "test@example.com", roles = {"USER", "BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldThrowExceptionOnDeleteWhenReferencedByBookmark() {
        // Setup: Create a Collection
        Collection collection = CollectionBuilder.build(b -> b
            .withName("FK-Test-Collection")
            .withOwner(currentUserService.currentUserRef())
        );
        collection.setId(ID.of(UUID.randomUUID(), Collection.class));
        collectionRepo.persist(collection);

        // Setup: Create a Tag and a Bookmark referencing it
        Tag tag = TagBuilder.build(b -> b
            .withName("FK-Test-Tag")
            .withColor("#000000")
            .withCollection(collection)
        );
        tag.setId(ID.of(UUID.randomUUID(), Tag.class));
        tagRepo.persist(tag);

        Bookmark bookmark = BookmarkBuilder.build(b -> b
            .withTitle("FK-Test-Bookmark")
            .withUrl("https://fk-test.com")
            .withCollection(collection)
            .withTags(Collections.singleton(tag))
        );
        bookmark.setId(ID.of(UUID.randomUUID(), Bookmark.class));
        bookmarkRepo.persist(bookmark);

        em.flush();
        em.clear();

        // Execution and Verification: Try to delete the tag directly, which should fail due to FK constraint
        Tag loadedTag = em.find(Tag.class, tag.getId().getUUID());
        em.remove(loadedTag);

        // Assertion: flush should trigger the database constraint violation
        Assertions.assertThrows(PersistenceException.class, em::flush,
            "Expected PersistenceException due to SQLite foreign key constraint violation");
    }
}
