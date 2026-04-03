package org.chainlink.api.bookmark;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_tag_name_collection", columnNames = {"name", "collection_id"}))
@NoArgsConstructor
@AllArgsConstructor
public class Tag extends AbstractEntity<Tag> {

    @NonNull
    @ManyToOne(optional = false)
    public Collection collection;

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    public String name;

    @NotBlank
    @Size(max = 7)
    @Column(nullable = false, length = 7)
    public String color;

    @ManyToMany(mappedBy = "tags")
    public Set<Bookmark> bookmarks = new HashSet<>();
}
