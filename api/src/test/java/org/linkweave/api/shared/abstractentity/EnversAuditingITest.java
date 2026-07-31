package org.linkweave.api.shared.abstractentity;

import java.util.List;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Envers writes nothing and says nothing when an entity is not {@link
 * org.hibernate.envers.Audited @Audited} — the {@code *_AUD} tables just stay
 * empty. {@link org.linkweave.api.shared.archunit.EnversAuditingTest} guards the
 * annotation; this guards that the wiring and the migrated schema actually
 * produce rows.
 */
@QuarkusTest
class EnversAuditingITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    DatabaseService databaseService;

    @Inject
    EntityManager em;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRecordInsertAndUpdateRevisionsForBookmark() {
        // ARRANGE
        Bookmark bookmark = fixtureService.createTestBookmark(b -> b.withTitle("Original"));

        // ACT
        renameBookmark(bookmark.getId(), "Renamed");

        // ASSERT
        List<Object[]> revisions = auditHistoryOf(Bookmark.class, bookmark.getId().getUUID());
        Assertions.assertThat(revisions)
            .as("one revision for the insert, one for the update")
            .hasSize(2);
        Assertions.assertThat(revisions).extracting(row -> row[2]).containsExactly(
            RevisionType.ADD, RevisionType.MOD);
        Assertions.assertThat(revisions).extracting(row -> ((Bookmark) row[0]).getTitle())
            .containsExactly("Original", "Renamed");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRecordRevisionsForEveryAuditedEntityType() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(t -> t.withCollection(collection).withName("audited"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("With tag")
            .withTags(Set.of(tag)));

        // ACT / ASSERT — the join table of the owning @ManyToMany is audited too,
        // which is what needs Bookmark_Tag_AUD to exist (V6).
        Assertions.assertThat(auditHistoryOf(Collection.class, collection.getId().getUUID()))
            .isNotEmpty();
        Assertions.assertThat(auditHistoryOf(Tag.class, tag.getId().getUUID()))
            .isNotEmpty();
        Assertions.assertThat(auditHistoryOf(Bookmark.class, bookmark.getId().getUUID()))
            .isNotEmpty();
        Assertions.assertThat(taggedBookmarkAuditRowCount()).isPositive();
    }

    @Transactional
    void renameBookmark(org.linkweave.api.types.id.ID<Bookmark> bookmarkId, String title) {
        Bookmark managed = bookmarkRepo.getById(bookmarkId);
        managed.setTitle(title);
        bookmarkRepo.persist(managed);
    }

    /** Rows of {@code [entity, revisionEntity, revisionType]}, oldest revision first. */
    @Transactional
    @SuppressWarnings("unchecked")
    <T> List<Object[]> auditHistoryOf(Class<T> entityClass, Object id) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.createQuery()
            .forRevisionsOfEntity(entityClass, false, true)
            .add(AuditEntity.id().eq(id))
            .addOrder(AuditEntity.revisionNumber().asc())
            .getResultList();
    }

    @Transactional
    long taggedBookmarkAuditRowCount() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM Bookmark_Tag_AUD")
            .getSingleResult()).longValue();
    }
}
