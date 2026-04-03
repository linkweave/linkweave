package org.chainlink.api.testutil.fixture;

import java.util.function.Consumer;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagRepo;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
@RequiredArgsConstructor
@Service
@Transactional(TxType.REQUIRES_NEW)
public class FixtureService {

    @Inject
    UserRepo userRepo;

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FolderRepo folderRepo;

    @Inject
    TagRepo tagRepo;

    @Inject
    BookmarkRepo bookmarkRepo;

    @NonNull
    public User persistUser(Consumer<org.chainlink.api.testutil.builder.UserBuilder> block) {
        User user = org.chainlink.api.testutil.builder.UserBuilder.build(block);
        userRepo.persist(user);
        return user;
    }

    @NonNull
    public Collection persistCollection(Consumer<org.chainlink.api.testutil.builder.CollectionBuilder> block) {
        Collection collection = org.chainlink.api.testutil.builder.CollectionBuilder.build(block);
        collectionRepo.persist(collection);
        return collection;
    }

    @NonNull
    public CollectionAccess persistCollectionAccess(Consumer<org.chainlink.api.testutil.builder.CollectionAccessBuilder> block) {
        CollectionAccess access = org.chainlink.api.testutil.builder.CollectionAccessBuilder.build(block);
        collectionAccessRepo.persist(access);
        return access;
    }

    @NonNull
    public Folder persistFolder(Consumer<org.chainlink.api.testutil.builder.FolderBuilder> block) {
        Folder folder = org.chainlink.api.testutil.builder.FolderBuilder.build(block);
        folderRepo.persist(folder);
        return folder;
    }

    @NonNull
    public Tag persistTag(Consumer<org.chainlink.api.testutil.builder.TagBuilder> block) {
        Tag tag = org.chainlink.api.testutil.builder.TagBuilder.build(block);
        tagRepo.persist(tag);
        return tag;
    }

    @NonNull
    public Bookmark persistBookmark(Consumer<org.chainlink.api.testutil.builder.BookmarkBuilder> block) {
        Bookmark bookmark = org.chainlink.api.testutil.builder.BookmarkBuilder.build(block);
        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    /**
     * Creates a Collection owned by the test user (test@example.com)
     * with CollectionAccess. Replaces the copy-pasted createTestCollection().
     */
    @NonNull
    public Collection createTestCollection() {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com"))
            .orElseThrow(() -> new RuntimeException("Test user not found"));

        Collection collection = org.chainlink.api.testutil.builder.CollectionBuilder.build(b -> b
            .withOwner(user)
            .withName("Test Collection")
        );
        collectionRepo.persist(collection);

        CollectionAccess access = org.chainlink.api.testutil.builder.CollectionAccessBuilder.build(b -> b
            .withCollection(collection)
            .withUser(user)
            .withRole(CollectionRole.OWNER)
            .withDefault(true)
        );
        collectionAccessRepo.persist(access);

        return collection;
    }

    /**
     * Creates a Collection + Folder in one call.
     */
    @NonNull
    public Folder createTestFolder(Consumer<org.chainlink.api.testutil.builder.FolderBuilder> block) {
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
    public Tag createTestTag(Consumer<org.chainlink.api.testutil.builder.TagBuilder> block) {
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
    public Bookmark createTestBookmark(Consumer<org.chainlink.api.testutil.builder.BookmarkBuilder> block) {
        Collection collection = createTestCollection();
        return persistBookmark(b -> {
            b.withCollection(collection);
            block.accept(b);
        });
    }
}
