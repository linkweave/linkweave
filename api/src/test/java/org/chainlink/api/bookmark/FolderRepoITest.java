package org.chainlink.api.bookmark;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FolderRepoITest {

    @Inject
    FolderRepo folderRepo;

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    UserRepo userRepo;

    private Collection createTestCollection() {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        Collection collection = new Collection();
        collection.name = "Test Collection";
        collection.owner = user;
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess();
        access.collection = collection;
        access.user = user;
        access.role = CollectionRole.OWNER;
        access.isDefault = true;
        collectionAccessRepo.persist(access);

        return collection;
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void saveFolder_shouldSaveFolder() {
        Collection collection = createTestCollection();

        Folder testFolder = new Folder();
        testFolder.collection = collection;
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);
        Assertions.assertThat(folderRepo.findAll()).isNotEmpty();
        Assertions.assertThat(folderRepo.findById(testFolder.getId())).isPresent();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findById_shouldReturnFolder_whenFolderExists() {
        Collection collection = createTestCollection();

        Folder testFolder = new Folder();
        testFolder.collection = collection;
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var foundFolder = folderRepo.findById(testFolder.getId());

        Assertions.assertThat(foundFolder).isPresent();
        Assertions.assertThat(foundFolder.get().name).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.get().getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findById_shouldReturnEmpty_whenFolderDoesNotExist() {
        var nonExistentId = ID.of(java.util.UUID.randomUUID(), Folder.class);

        var foundFolder = folderRepo.findById(nonExistentId);

        Assertions.assertThat(foundFolder).isEmpty();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void getById_shouldReturnFolder_whenFolderExists() {
        Collection collection = createTestCollection();

        Folder testFolder = new Folder();
        testFolder.collection = collection;
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var foundFolder = folderRepo.getById(testFolder.getId());

        Assertions.assertThat(foundFolder).isNotNull();
        Assertions.assertThat(foundFolder.name).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findAll_shouldReturnAllFolders() {
        Collection collection = createTestCollection();

        Folder folder1 = new Folder();
        folder1.collection = collection;
        folder1.name = "Folder 1";
        folderRepo.persist(folder1);

        Folder folder2 = new Folder();
        folder2.collection = collection;
        folder2.name = "Folder 2";
        folderRepo.persist(folder2);

        var allFolders = folderRepo.findAll();

        Assertions.assertThat(allFolders).hasSizeGreaterThanOrEqualTo(2)
            .anyMatch(f -> f.name.equals("Folder 1"))
            .anyMatch(f -> f.name.equals("Folder 2"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    @Transactional
    void updateFolder_shouldUpdateFolder() {
        Collection collection = createTestCollection();

        Folder testFolder = new Folder();
        testFolder.collection = collection;
        testFolder.name = "Original Name";
        folderRepo.persist(testFolder);

        Folder foundFolder = folderRepo.getById(testFolder.getId());
        foundFolder.name = "Updated Name";

        var updatedFolder = folderRepo.getById(testFolder.getId());

        Assertions.assertThat(updatedFolder.name).isEqualTo("Updated Name");
        Assertions.assertThat(updatedFolder.getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void deleteFolder_shouldRemoveFolder() {
        Collection collection = createTestCollection();

        Folder testFolder = new Folder();
        testFolder.collection = collection;
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var folderId = testFolder.getId();

        folderRepo.remove(folderId);

        var deletedFolder = folderRepo.findById(folderId);
        Assertions.assertThat(deletedFolder).isEmpty();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findByCollection_shouldReturnFoldersForCollection() {
        Collection collection = createTestCollection();

        Folder folder1 = new Folder();
        folder1.collection = collection;
        folder1.name = "Folder 1";
        folderRepo.persist(folder1);

        Folder folder2 = new Folder();
        folder2.collection = collection;
        folder2.name = "Folder 2";
        folderRepo.persist(folder2);

        var folders = folderRepo.findByCollection(collection.getId());

        Assertions.assertThat(folders).hasSize(2)
            .anyMatch(f -> f.name.equals("Folder 1"))
            .anyMatch(f -> f.name.equals("Folder 2"));
    }
}
