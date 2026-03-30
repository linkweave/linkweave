package org.chainlink.api.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_collectionaccess_user", columnList = "user_id"),
        @Index(name = "ix_collectionaccess_collection", columnList = "collection_id"),
        @Index(name = "ix_collectionaccess_user_default", columnList = "user_id, isDefault"),
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_collectionaccess_collection_user", columnNames = {"collection_id", "user_id"}),
    }
)
@NoArgsConstructor
@AllArgsConstructor
public class CollectionAccess extends AbstractEntity<CollectionAccess> {

    @NonNull
    @NotNull
    @ManyToOne(optional = false)
    public Collection collection;

    @NonNull
    @NotNull
    @ManyToOne(optional = false)
    public User user;

    @NonNull
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = DbConst.DB_ENUM_LENGTH)
    public CollectionRole role;

    @NotNull
    @Column(nullable = false)
    public boolean isDefault = false;
}
