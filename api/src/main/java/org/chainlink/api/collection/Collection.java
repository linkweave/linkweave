package org.chainlink.api.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_collection_owner", columnList = "owner_id"),
    }
)
@NoArgsConstructor
@AllArgsConstructor
public class Collection extends AbstractEntity<Collection> {

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    public String name;

    @NonNull
    @ManyToOne(optional = false)
    public User owner;
}
