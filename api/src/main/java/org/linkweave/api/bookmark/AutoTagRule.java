package org.linkweave.api.bookmark;

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
import ch.dvbern.dvbstarter.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.api.shared.auth.BelongsToCollection;
import org.linkweave.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_auto_tag_rule_collection_id", columnList = "collection_id, sortOrder, id"),
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AutoTagRule extends AbstractEntity<AutoTagRule> implements BelongsToCollection {

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_auto_tag_rule_collection"), nullable = false)
    private Collection collection;

    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Column(nullable = false, length = DbConst.DB_ENUM_LIST_LENGTH)
    private String pattern;

    /** Comma-separated, lowercased, deduplicated tag names. */
    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Column(nullable = false, length = DbConst.DB_ENUM_LIST_LENGTH)
    private String tagNames;

    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int sortOrder;

    @Override
    public @NonNull ID<Collection> getCollectionId() {
        return collection.getId();
    }
}
