package org.linkweave.api.bookmark;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ch.dvbern.dvbstarter.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.api.shared.auth.BelongsToCollection;
import org.linkweave.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_tag_collection_id", columnList = "collection_id, id"),
    },
    uniqueConstraints = @UniqueConstraint(name = "uc_tag_name_collection", columnNames = {"name", "collection_id"})
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Tag extends AbstractEntity<Tag> implements BelongsToCollection {

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_tag_collection"), nullable = false)
    private Collection collection;

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String name;

    @NotBlank
    @Size(max = 7)
    @Column(nullable = false, length = 7)
    private String color;

    @ManyToMany(mappedBy = "tags")
    private Set<Bookmark> bookmarks = new HashSet<>();

    @Override
    public @NonNull ID<Collection> getCollectionId() {
        return collection.getId();
    }
}
