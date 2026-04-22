package org.chainlink.api.bookmark.folder;

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
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
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
public class Folder extends AbstractEntity<Folder> {

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
}
