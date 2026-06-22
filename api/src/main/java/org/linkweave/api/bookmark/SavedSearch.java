package org.linkweave.api.bookmark;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.api.shared.auth.BelongsToCollection;
import org.linkweave.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_saved_search_collection_id", columnList = "collection_id, id"),
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uc_saved_search_name_collection",
        columnNames = {"name", "collection_id"}
    )
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SavedSearch extends AbstractEntity<SavedSearch> implements BelongsToCollection {

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_saved_search_collection"), nullable = false)
    private Collection collection;

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String name;

    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Column(nullable = false, length = DbConst.DB_ENUM_LIST_LENGTH)
    private String query;

    @Override
    public @NonNull ID<Collection> getCollectionId() {
        return collection.getId();
    }
}
