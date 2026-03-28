package org.chainlink.api.bookmark;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FolderRepoITest {

    @Inject
    FolderRepo folderRepo;

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void saveFolder_shouldSaveFolder() {
        Folder testFolder = new Folder();
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);
        Assertions.assertThat(folderRepo.findAll()).isNotEmpty();
        Assertions.assertThat(folderRepo.findById(testFolder.getId())).isPresent();
    }

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void findById_shouldReturnFolder_whenFolderExists() {
        Folder testFolder = new Folder();
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var foundFolder = folderRepo.findById(testFolder.getId());

        Assertions.assertThat(foundFolder).isPresent();
        Assertions.assertThat(foundFolder.get().name).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.get().getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void findById_shouldReturnEmpty_whenFolderDoesNotExist() {
        var nonExistentId = ID.of(java.util.UUID.randomUUID(), Folder.class);

        var foundFolder = folderRepo.findById(nonExistentId);

        Assertions.assertThat(foundFolder).isEmpty();
    }

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void getById_shouldReturnFolder_whenFolderExists() {
        Folder testFolder = new Folder();
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var foundFolder = folderRepo.getById(testFolder.getId());

        Assertions.assertThat(foundFolder).isNotNull();
        Assertions.assertThat(foundFolder.name).isEqualTo("Test Folder");
        Assertions.assertThat(foundFolder.getId()).isEqualTo(testFolder.getId());
    }

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void findAll_shouldReturnAllFolders() {
        Folder folder1 = new Folder();
        folder1.name = "Folder 1";
        folderRepo.persist(folder1);

        Folder folder2 = new Folder();
        folder2.name = "Folder 2";
        folderRepo.persist(folder2);

        var allFolders = folderRepo.findAll();

        Assertions.assertThat(allFolders).hasSizeGreaterThanOrEqualTo(2);
        Assertions.assertThat(allFolders).anyMatch(f -> f.name.equals("Folder 1"));
        Assertions.assertThat(allFolders).anyMatch(f -> f.name.equals("Folder 2"));
    }

    @Test
    @TestSecurity(
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    @Transactional
    void updateFolder_shouldUpdateFolder() {
        Folder testFolder = new Folder();
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
        user = "Testuser",
        roles = {"BOOKMARK_WRITE"}
    )
    void deleteFolder_shouldRemoveFolder() {
        Folder testFolder = new Folder();
        testFolder.name = "Test Folder";
        folderRepo.persist(testFolder);

        var folderId = testFolder.getId();

        folderRepo.remove(folderId);

        var deletedFolder = folderRepo.findById(folderId);
        Assertions.assertThat(deletedFolder).isEmpty();
    }

}
