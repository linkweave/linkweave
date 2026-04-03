package org.chainlink.api.bookmark;

import java.time.OffsetDateTime;

import org.assertj.core.api.Assertions;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.Test;

class FolderMapperTest {

    private Collection createTestCollection() {
        User owner = new User();
        owner.setEmail(ch.dvbern.dvbstarter.types.emailaddress.EmailAddress.fromString("test@example.com"));
        owner.setNachname("User");
        owner.setVorname("Test");

        Collection collection = new Collection();
        collection.setName("Test Collection");
        collection.setOwner(owner);
        collection.setTimestampErstellt(OffsetDateTime.now());
        collection.setTimestampMutiert(OffsetDateTime.now());
        collection.setUserErstellt("test@example.com");
        collection.setUserMutiert("test@example.com");
        return collection;
    }

    @Test
    void toJson_shouldMapFolderWithoutParent() {
        Collection collection = createTestCollection();

        Folder folder = new Folder();
        folder.setCollection(collection);
        folder.setName("Test Folder");
        folder.setTimestampErstellt(OffsetDateTime.now());
        folder.setTimestampMutiert(OffsetDateTime.now());
        folder.setUserErstellt("test@example.com");
        folder.setUserMutiert("test@example.com");

        var result = FolderMapper.toJson(folder);

        Assertions.assertThat(result.getId()).isEqualTo(folder.getId());
        Assertions.assertThat(result.getData().getCollectionId()).isEqualTo(collection.getId());
        Assertions.assertThat(result.getData().getName()).isEqualTo("Test Folder");
        Assertions.assertThat(result.getData().getParentId()).isNull();
        Assertions.assertThat(result.getEntityInfo()).isNotNull();
        Assertions.assertThat(result.getEntityInfo().getUserErstellt()).isEqualTo("test@example.com");
    }

    @Test
    void toJson_shouldMapFolderWithParent() {
        Collection collection = createTestCollection();

        Folder parent = new Folder();
        parent.setCollection(collection);
        parent.setName("Parent Folder");
        parent.setTimestampErstellt(OffsetDateTime.now());
        parent.setTimestampMutiert(OffsetDateTime.now());
        parent.setUserErstellt("test@example.com");
        parent.setUserMutiert("test@example.com");

        Folder child = new Folder();
        child.setCollection(collection);
        child.setName("Child Folder");
        child.setParent(parent);
        child.setTimestampErstellt(OffsetDateTime.now());
        child.setTimestampMutiert(OffsetDateTime.now());
        child.setUserErstellt("test@example.com");
        child.setUserMutiert("test@example.com");

        var result = FolderMapper.toJson(child);

        Assertions.assertThat(result.getData().getCollectionId()).isEqualTo(collection.getId());
        Assertions.assertThat(result.getData().getParentId()).isEqualTo(parent.getId());
        Assertions.assertThat(result.getData().getName()).isEqualTo("Child Folder");
    }
}
