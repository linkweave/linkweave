package org.linkweave.api.collection;

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
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.db.DbConst;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(indexes = {
    @Index(name = "ix_collection_owner_id", columnList = "owner_id, id"),
})
@Audited
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

    /**
     * Whether LLM tag suggestions (FR-095) run for this collection (UC-112).
     *
     * <p>Defaults to {@code true}, unlike {@link #screenshotEnabled}: auto-tagging
     * is already live everywhere, so this is an opt-out, not an opt-in. The field
     * initialiser matters as much as the column default — Hibernate writes an
     * explicit value on insert, so a collection created through code would be
     * persisted as {@code false} and never see the DDL default.
     */
    @Column(nullable = false)
    private boolean aiTaggingEnabled = true;

    public Collection(@NotBlank String name, @NonNull User owner) {
        this.name = name;
        this.owner = owner;
    }
}
