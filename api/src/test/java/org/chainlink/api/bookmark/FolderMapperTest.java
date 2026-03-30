package org.chainlink.api.bookmark;

import java.time.OffsetDateTime;

import org.assertj.core.api.Assertions;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.junit.jupiter.api.Test;

class FolderMapperTest {

    @Test
    void toJson_shouldMapFolderWithoutParent() {
        Folder folder = new Folder();
        folder.name = "Test Folder";
        folder.setTimestampErstellt(OffsetDateTime.now());
        folder.setTimestampMutiert(OffsetDateTime.now());
        folder.setUserErstellt("test@example.com");
        folder.setUserMutiert("test@example.com");

        var result = FolderMapper.toJson(folder);

        Assertions.assertThat(result.getId()).isEqualTo(folder.getId());
        Assertions.assertThat(result.getData().getName()).isEqualTo("Test Folder");
        Assertions.assertThat(result.getData().getParentId()).isNull();
        Assertions.assertThat(result.getEntityInfo()).isNotNull();
        Assertions.assertThat(result.getEntityInfo().getUserErstellt()).isEqualTo("test@example.com");
    }

    @Test
    void toJson_shouldMapFolderWithParent() {
        Folder parent = new Folder();
        parent.name = "Parent Folder";
        parent.setTimestampErstellt(OffsetDateTime.now());
        parent.setTimestampMutiert(OffsetDateTime.now());
        parent.setUserErstellt("test@example.com");
        parent.setUserMutiert("test@example.com");

        Folder child = new Folder();
        child.name = "Child Folder";
        child.parent = parent;
        child.setTimestampErstellt(OffsetDateTime.now());
        child.setTimestampMutiert(OffsetDateTime.now());
        child.setUserErstellt("test@example.com");
        child.setUserMutiert("test@example.com");

        var result = FolderMapper.toJson(child);

        Assertions.assertThat(result.getData().getParentId()).isEqualTo(parent.getId());
        Assertions.assertThat(result.getData().getName()).isEqualTo("Child Folder");
    }
}
