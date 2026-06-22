package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.infrastructure.db.DatabaseService;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FolderRepoITest {

    @Inject
    FolderRepo folderRepo;


    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void saveFolder_shouldSaveFolder() {
        Collection collection = fixtureService.createTestCollection();

        Folder testFolder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );

        var allFolders = folderRepo.findAll();
        Assertions.assertThat(allFolders).anyMatch(f -> f.getId().equals(testFolder.getId()));
        Assertions.assertThat(folderRepo.findById(testFolder.getId())).isPresent();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findById_shouldReturnFolder_whenFolderExists() {
        Collection collection = fixtureService.createTestCollection();

        Folder testFolder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );

        var foundFolder = folderRepo.findById(testFolder.getId());

        Assertions.assertThat(foundFolder).isPresent();
        Assertions.assertThat(foundFolder.get().getName()).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.get().getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findById_shouldReturnEmpty_whenFolderDoesNotExist() {
        var nonExistentId = ch.dvbern.dvbstarter.types.id.ID.of(java.util.UUID.randomUUID(), Folder.class);

        var foundFolder = folderRepo.findById(nonExistentId);

        Assertions.assertThat(foundFolder).isEmpty();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void getById_shouldReturnFolder_whenFolderExists() {
        Collection collection = fixtureService.createTestCollection();

        Folder testFolder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );

        var foundFolder = folderRepo.getById(testFolder.getId());

        Assertions.assertThat(foundFolder).isNotNull();
        Assertions.assertThat(foundFolder.getName()).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void findAll_shouldReturnAllFolders() {
        Collection collection = fixtureService.createTestCollection();

        Folder folder1 = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Folder 1")
        );

        Folder folder2 = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Folder 2")
        );

        var allFolders = folderRepo.findAll();

        Assertions.assertThat(allFolders)
            .anyMatch(f -> f.getName().equals("Folder 1") && f.getId().equals(folder1.getId()))
            .anyMatch(f -> f.getName().equals("Folder 2") && f.getId().equals(folder2.getId()));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    @Transactional
    void updateFolder_shouldUpdateFolder() {
        Collection collection = fixtureService.createTestCollection();

        Folder testFolder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Original Name")
        );

        Folder foundFolder = folderRepo.getById(testFolder.getId());
        foundFolder.setName("Updated Name");

        var updatedFolder = folderRepo.getById(testFolder.getId());

        Assertions.assertThat(updatedFolder.getName()).isEqualTo("Updated Name");
        Assertions.assertThat(updatedFolder.getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void deleteFolder_shouldRemoveFolder() {
        Collection collection = fixtureService.createTestCollection();

        Folder testFolder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );

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
        Collection collection = fixtureService.createTestCollection();

        Folder folder1 = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Folder 1")
        );

        Folder folder2 = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Folder 2")
        );

        var folders = folderRepo.findByCollection(collection.getId());

        Assertions.assertThat(folders)
            .anyMatch(f -> f.getName().equals("Folder 1") && f.getId().equals(folder1.getId()))
            .anyMatch(f -> f.getName().equals("Folder 2") && f.getId().equals(folder2.getId()));
    }
}
