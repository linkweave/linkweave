package org.chainlink.api.collection;

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
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(indexes = {
    @Index(name = "ix_collection_owner_id", columnList = "owner_id, id"),
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Collection extends AbstractEntity<Collection> {

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String name;

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_collection_owner"), nullable = false)
    private User owner;

    @Nullable
    @Size(max = DbConst.DB_TEXTAREA_MAX_LENGTH_2000)
    @Column(length = DbConst.DB_TEXTAREA_MAX_LENGTH_2000)
    private String browserFetchAllowlist;

    @Column(nullable = false)
    private boolean screenshotEnabled;

    public Collection(@NotBlank String name, @NonNull User owner) {
        this.name = name;
        this.owner = owner;
    }
}
