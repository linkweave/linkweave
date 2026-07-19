package org.linkweave.api.bookmark.folder;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linkweave.api.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.api.shared.auth.BelongsToCollection;
import org.linkweave.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(indexes = {
    @Index(name = "ix_folder_collection_id", columnList = "collection_id, id"),
    @Index(name = "ix_folder_parent_id", columnList = "parent_id, id"),
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Folder extends AbstractEntity<Folder> implements BelongsToCollection {

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_folder_collection"), nullable = false)
    private Collection collection;

    @Nullable
    @ManyToOne(optional = true)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_folder_parent"), nullable = true)
    private Folder parent;

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String name;

    @Nullable
    @Size(max = 7)
    @Column(length = 7)
    private String color;

    /**
     * Manual position among siblings (same collection + parent), UC-102.
     * Sparse numbering managed by {@link FolderService}; ties are broken by
     * creation timestamp, then id (BR-191).
     */
    @Column(nullable = false)
    private long sortOrder;

    @Nullable
    @Column(name = "deleted_at", nullable = true)
    private OffsetDateTime deletedAt;

    @Override
    public @NonNull ID<Collection> getCollectionId() {
        return collection.getId();
    }
}
