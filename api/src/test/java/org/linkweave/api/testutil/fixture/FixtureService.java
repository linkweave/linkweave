package org.linkweave.api.testutil.fixture;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

import org.linkweave.api.types.emailaddress.EmailAddress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.auth.apikey.ApiKey;
import org.linkweave.api.auth.apikey.ApiKeyRepo;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.testutil.builder.ApiKeyBuilder;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.TagRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.bookmark.property.PropertyDefinition;
import org.linkweave.api.bookmark.property.PropertyDefinitionRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccess;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRole;
import org.linkweave.api.collection.CollectionService;
import org.linkweave.api.shared.sortorder.SparseSortOrder;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
@RequiredArgsConstructor
@Service
@Transactional(TxType.REQUIRES_NEW)
public class FixtureService {

    @Inject
    ApiKeyRepo apiKeyRepo;

    @Inject
    UserRepo userRepo;

    @Inject
    CollectionService collectionService;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FolderRepo folderRepo;

    @Inject
    TagRepo tagRepo;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    PropertyDefinitionRepo propertyDefinitionRepo;

    @Inject
    EntityManager em;

    /**
     * Removes every API key. The users behind {@code @TestSecurity} are fixed annotation values
     * (e.g. test@example.com), so their keys survive in linkweave-test.db across runs and would
     * otherwise accumulate until creates fail the max-active-keys check (BR-001).
     */
    public void deleteAllApiKeys() {
        em.createQuery("delete from ApiKey").executeUpdate();
    }

    @NonNull
    public ApiKey persistApiKey(Consumer<ApiKeyBuilder> block) {
        ApiKey apiKey = ApiKeyBuilder.build(block);
        apiKeyRepo.persist(apiKey);
        return apiKey;
    }

    @NonNull
    public User persistUser(Consumer<org.linkweave.api.testutil.builder.UserBuilder> block) {
        User user = org.linkweave.api.testutil.builder.UserBuilder.build(block);
        userRepo.persist(user);
        return user;
    }

    @NonNull
    public Collection persistCollection(Consumer<org.linkweave.api.testutil.builder.CollectionBuilder> block) {
        Collection collection = org.linkweave.api.testutil.builder.CollectionBuilder.build(block);
        collectionService.saveCollection(collection);
        return collection;
    }

    @NonNull
    public CollectionAccess persistCollectionAccess(Consumer<org.linkweave.api.testutil.builder.CollectionAccessBuilder> block) {
        CollectionAccess access = org.linkweave.api.testutil.builder.CollectionAccessBuilder.build(block);
        collectionAccessRepo.persist(access);
        return access;
    }

    /**
     * Folders without an explicit {@code withSortOrder(...)} are appended after their existing
     * siblings, matching what the API does on create. Without this they would all share
     * sortOrder 0 and sort before every API-created folder.
     */
    @NonNull
    public Folder persistFolder(Consumer<org.linkweave.api.testutil.builder.FolderBuilder> block) {
        Folder folder = org.linkweave.api.testutil.builder.FolderBuilder.build(block);
        if (folder.getSortOrder() == 0) {
            ID<Folder> parentId = folder.getParent() == null ? null : folder.getParent().getId();
            folder.setSortOrder(SparseSortOrder.afterMax(
                folderRepo.findMaxSortOrderOfSiblings(folder.getCollection().getId(), parentId)));
        }
        folderRepo.persist(folder);
        return folder;
    }

    @NonNull
    public Tag persistTag(Consumer<org.linkweave.api.testutil.builder.TagBuilder> block) {
        Tag tag = org.linkweave.api.testutil.builder.TagBuilder.build(block);
        tagRepo.persist(tag);
        return tag;
    }

    @NonNull
    public Bookmark persistBookmark(Consumer<org.linkweave.api.testutil.builder.BookmarkBuilder> block) {
        Bookmark bookmark = org.linkweave.api.testutil.builder.BookmarkBuilder.build(block);
        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    @NonNull
    public PropertyDefinition persistPropertyDefinition(Consumer<org.linkweave.api.testutil.builder.PropertyDefinitionBuilder> block) {
        PropertyDefinition def = org.linkweave.api.testutil.builder.PropertyDefinitionBuilder.build(block);
        propertyDefinitionRepo.persist(def);
        return def;
    }

    /**
     * Creates a Collection owned by the test user (test@example.com)
     * with CollectionAccess. Replaces the copy-pasted createTestCollection().
     */
    @NonNull
    public Collection createTestCollection() {
        return createTestCollection(_ -> {});
    }

    /**
     * Variant that lets the test customize the collection (e.g. flip the
     * screenshot flag) before persistence. Owner + CollectionAccess for the
     * test user are still set up automatically.
     */
    @NonNull
    public Collection createTestCollection(Consumer<org.linkweave.api.testutil.builder.CollectionBuilder> block) {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com"))
            .orElseThrow(() -> new RuntimeException("Test user not found"));

        Collection collection = org.linkweave.api.testutil.builder.CollectionBuilder.build(b -> {
            b.withOwner(user).withName("Test Collection");
            block.accept(b);
        });
        collectionService.saveCollection(collection);

        CollectionAccess access = org.linkweave.api.testutil.builder.CollectionAccessBuilder.build(b -> b
            .withCollection(collection)
            .withUser(user)
            .withRole(CollectionRole.OWNER)
            .withDefault(false)
        );
        collectionAccessRepo.persist(access);

        return collection;
    }

    /**
     * Creates a Collection + Folder in one call.
     */
    @NonNull
    public Folder createTestFolder(Consumer<org.linkweave.api.testutil.builder.FolderBuilder> block) {
        Collection collection = createTestCollection();
        return persistFolder(b -> {
            b.withCollection(collection);
            block.accept(b);
        });
    }

    /**
     * Creates a Collection + Tag in one call.
     */
    @NonNull
    public Tag createTestTag(Consumer<org.linkweave.api.testutil.builder.TagBuilder> block) {
        Collection collection = createTestCollection();
        return persistTag(b -> {
            b.withCollection(collection);
            block.accept(b);
        });
    }

    /**
     * Creates a full bookmark graph: Collection + optional Folder + optional Tags + Bookmark.
     */
    @NonNull
    public Bookmark createTestBookmark(Consumer<org.linkweave.api.testutil.builder.BookmarkBuilder> block) {
        Collection collection = createTestCollection();
        return persistBookmark(b -> {
            b.withCollection(collection);
            block.accept(b);
        });
    }

    public void setTimestampErstellt(@NonNull Bookmark bookmark, @NonNull OffsetDateTime at) {
        bookmarkRepo.getById(bookmark.getId()).setTimestampErstellt(at);
    }
}
