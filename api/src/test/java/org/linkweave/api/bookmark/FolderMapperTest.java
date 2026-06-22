package org.linkweave.api.bookmark;

import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderMapper;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.EntityTestHelper;
import org.linkweave.api.testutil.builder.CollectionBuilder;
import org.linkweave.api.testutil.builder.FolderBuilder;
import org.linkweave.api.testutil.builder.UserBuilder;
import org.junit.jupiter.api.Test;

class FolderMapperTest {

    private Collection createTestCollection() {
        Collection collection = CollectionBuilder.build(b -> b
            .withOwner(UserBuilder.build(u -> u
                .withEmail("test@example.com")
                .withVorname("Test")
                .withNachname("User")
            ))
            .withName("Test Collection")
        );
        return EntityTestHelper.initEntityInfo(collection);
    }

    @Test
    void toJson_shouldMapFolderWithoutParent() {
        Collection collection = createTestCollection();

        Folder folder = FolderBuilder.build(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );
        EntityTestHelper.initEntityInfo(folder);

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

        Folder parent = FolderBuilder.build(b -> b
            .withCollection(collection)
            .withName("Parent Folder")
        );
        EntityTestHelper.initEntityInfo(parent);

        Folder child = FolderBuilder.build(b -> b
            .withCollection(collection)
            .withName("Child Folder")
            .withParent(parent)
        );
        EntityTestHelper.initEntityInfo(child);

        var result = FolderMapper.toJson(child);

        Assertions.assertThat(result.getData().getCollectionId()).isEqualTo(collection.getId());
        Assertions.assertThat(result.getData().getParentId()).isEqualTo(parent.getId());
        Assertions.assertThat(result.getData().getName()).isEqualTo("Child Folder");
    }
}
