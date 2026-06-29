package org.linkweave.api.auth;

import java.net.URI;
import java.util.UUID;

import org.linkweave.api.types.emailaddress.EmailAddress;
import org.linkweave.api.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.TagRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccess;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.collection.CollectionRole;
import org.linkweave.api.collection.CollectionService;
import org.linkweave.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
class UserServiceITest {

    @Inject UserService userService;
    @Inject RegistrationService registrationService;
    @Inject UserRepo userRepo;
    @Inject CollectionService collectionService;
    @Inject CollectionRepo collectionRepo;
    @Inject CollectionAccessRepo collectionAccessRepo;
    @Inject BookmarkRepo bookmarkRepo;
    @Inject TagRepo tagRepo;
    @Inject FolderRepo folderRepo;

    @Test
    void shouldHardDeleteUserAndAllOwnedData() throws Exception {
        // ARRANGE — register a fresh victim user and seed their owned data.
        String suffix = UUID.randomUUID().toString();
        String victimEmail = "victim-" + suffix + "@example.com";
        User victim = registrationService.register(victimEmail, "test-password-123", "V", "Ictim");
        ID<User> victimId = victim.getId();

        Collection ownedCollection = collectionService.getDefaultCollectionOrAutoprovision(victim);
        ID<Collection> ownedCollectionId = ownedCollection.getId();

        Folder folder = new Folder();
        folder.setCollection(ownedCollection);
        folder.setName("Folder-" + suffix);
        folderRepo.persistAndFlush(folder);
        ID<Folder> folderId = folder.getId();

        Tag tag = new Tag();
        tag.setCollection(ownedCollection);
        tag.setName("tag-" + suffix);
        tag.setColor("#abcdef");
        tagRepo.persistAndFlush(tag);
        ID<Tag> tagId = tag.getId();

        Bookmark bookmark = new Bookmark();
        bookmark.setCollection(ownedCollection);
        bookmark.setTitle("Bookmark-" + suffix);
        bookmark.setUrl(URI.create("https://example.com/" + suffix).toURL());
        bookmarkRepo.persistAndFlush(bookmark);
        ID<Bookmark> bookmarkId = bookmark.getId();

        // Add the victim as a MEMBER on someone else's seeded collection so
        // we can verify cross-membership cleanup as well.
        User other = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        Collection otherCollection = collectionService.getDefaultCollectionOrAutoprovision(other);
        CollectionAccess membership = new CollectionAccess(otherCollection, victim, CollectionRole.MEMBER, false);
        collectionAccessRepo.persistAndFlush(membership);

        // Sanity — the data we just seeded is visible.
        assertThat(userRepo.findById(victimId)).isPresent();
        assertThat(collectionRepo.findById(ownedCollectionId)).isPresent();
        assertThat(folderRepo.findById(folderId)).isPresent();
        assertThat(tagRepo.findById(tagId)).isPresent();
        assertThat(bookmarkRepo.findById(bookmarkId)).isPresent();
        assertThat(collectionAccessRepo.findByUser(victimId)).isNotEmpty();

        // ACT
        userService.hardDeleteUser(victimId);

        // ASSERT — victim and everything they owned is gone.
        assertThat(userRepo.findById(victimId)).isEmpty();
        assertThat(collectionRepo.findById(ownedCollectionId)).isEmpty();
        assertThat(folderRepo.findById(folderId)).isEmpty();
        assertThat(tagRepo.findById(tagId)).isEmpty();
        assertThat(bookmarkRepo.findById(bookmarkId)).isEmpty();
        assertThat(collectionAccessRepo.findByUser(victimId)).isEmpty();

        // The seeded "other" user is unaffected.
        assertThat(userRepo.findById(other.getId())).isPresent();
        assertThat(collectionRepo.findById(otherCollection.getId())).isPresent();
    }
}
