package org.linkweave.api.collection.events;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.AutoTagRule;
import org.linkweave.api.bookmark.AutoTagRuleService;
import org.linkweave.api.bookmark.TagService;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderService;
import org.linkweave.api.bookmark.folder.json.FolderSaveJson;
import org.linkweave.api.bookmark.json.AutoTagRuleSaveJson;
import org.linkweave.api.bookmark.json.TagSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.id.ID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-104 phase 3 — the collection's *structure*, not just its bookmarks.
 *
 * <p>Folders and tags were the last write paths another member could change
 * without anyone else finding out until they reloaded. Folder deletion is the
 * one worth reading twice: it soft-deletes every bookmark inside, so a single
 * click can empty a large part of someone else's view.
 */
@QuarkusTest
class StructureChangeNotificationITest {

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    FolderService folderService;

    @Inject
    TagService tagService;

    @Inject
    AutoTagRuleService autoTagRuleService;

    @Inject
    FixtureService fixtureService;

    private AssertSubscriber<CollectionEventJson> listen(Collection collection) {
        return broadcaster.subscribe(collection.getId(), null)
            .subscribe().withSubscriber(AssertSubscriber.create(10));
    }

    private static ChangeKind onlyKind(AssertSubscriber<CollectionEventJson> subscriber) {
        assertThat(subscriber.getItems()).hasSize(1);
        return subscriber.getItems().getFirst().getKind();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceACreatedFolder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        folderService.createFolder(new FolderSaveJson(collection.getId(), null, "Reading list", null));

        // ASSERT
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.FOLDER_ADDED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceARenamedFolder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Before"));
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        folderService.updateFolder(
            folder.getId(), new FolderSaveJson(collection.getId(), null, "After", null));

        // ASSERT
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.FOLDER_CHANGED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceADeletedFolderOnceForTheWholeCascade() {
        // ARRANGE — a folder with bookmarks in it, all of which go with it
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Doomed"));
        fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("a").withUrl("https://example.com/a"));
        fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("b").withUrl("https://example.com/b"));
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        folderService.removeFolder(folder.getId());

        // ASSERT — one event for the operation the user performed, not one per
        // bookmark that disappeared with it
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.FOLDER_REMOVED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceNothingWhenDeletingAnAlreadyDeletedFolder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Gone"));
        folderService.removeFolder(folder.getId());
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        folderService.removeFolder(folder.getId());

        // ASSERT — nothing changed the second time
        subscriber.assertHasNotReceivedAnyItem();
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceARestoredFolder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Back"));
        folderService.removeFolder(folder.getId());
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        folderService.restoreFolder(folder.getId());

        // ASSERT — it reappears in the sidebar, which reads as an addition
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.FOLDER_ADDED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceATagTheWholeCollectionShares() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        tagService.createTag(new TagSaveJson(collection.getId(), "urgent", null));

        // ASSERT — tags, property definitions and auto-tag rules share one kind:
        // the client re-reads the same document for all of them
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.COLLECTION_CHANGED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceAReorderThatMovedSomething() {
        // ARRANGE — two rules, subscribed to only after creating them so their
        // own creation events do not count
        Collection collection = fixtureService.createTestCollection();
        var first = autoTagRuleService.createRule(
            new AutoTagRuleSaveJson(collection.getId(), "example\\.com", "news", null, true));
        var second = autoTagRuleService.createRule(
            new AutoTagRuleSaveJson(collection.getId(), "example\\.org", "blog", null, true));
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        autoTagRuleService.reorder(collection.getId(), List.of(second.getId(), first.getId()));

        // ASSERT
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.COLLECTION_CHANGED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceNothingForAReorderThatMovedNothing() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        autoTagRuleService.createRule(
            new AutoTagRuleSaveJson(collection.getId(), "example\\.net", "misc", null, true));
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT — an empty list, and one naming a rule that belongs to no rule of
        // this collection. Both write nothing: ids that match nothing are
        // skipped, and the request DTO permits an empty list.
        autoTagRuleService.reorder(collection.getId(), List.of());
        autoTagRuleService.reorder(collection.getId(), List.of(ID.random(AutoTagRule.class)));

        // ASSERT — a no-op must not make every other member reload
        subscriber.assertHasNotReceivedAnyItem();
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceADeletedTagBecauseEveryBookmarkLosesIt() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        var tag = tagService.createTag(new TagSaveJson(collection.getId(), "temporary", null));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("tagged").withUrl("https://example.com/tagged"));
        assertThat(bookmark).isNotNull();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        tagService.removeTag(tag.getId());

        // ASSERT
        assertThat(onlyKind(subscriber)).isEqualTo(ChangeKind.COLLECTION_CHANGED);
        subscriber.cancel();
    }
}
