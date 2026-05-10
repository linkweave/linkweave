package org.chainlink.api.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_collectionaccess_user_id", columnList = "user_id, id"),
        @Index(name = "ix_collectionaccess_collection_id", columnList = "collection_id, id"),
        @Index(name = "ix_collectionaccess_user_default", columnList = "user_id, isDefault"),
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_collectionaccess_collection_user", columnNames = {"collection_id", "user_id"}),
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CollectionAccess extends AbstractEntity<CollectionAccess> {

    public CollectionAccess(
        @NonNull Collection collection,
        @NonNull User user,
        @NonNull CollectionRole role,
        boolean isDefault
    ) {
        this(collection, user, role, isDefault, null);
    }


    @NonNull
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_collectionaccess_collection"), nullable = false)
    private Collection collection;

    @NonNull
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_collectionaccess_user"), nullable = false)
    private User user;

    @NonNull
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = DbConst.DB_ENUM_LENGTH)
    private CollectionRole role;

    @NotNull
    @Column(nullable = false)
    private boolean isDefault = false;

    @Lob
    @Column(columnDefinition = "TEXT")
    private @Nullable String settings;
}
