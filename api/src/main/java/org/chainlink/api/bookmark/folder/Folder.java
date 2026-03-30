package org.chainlink.api.bookmark.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table()
@NoArgsConstructor
@AllArgsConstructor
public class Folder extends AbstractEntity<Folder> {

    @NonNull
    @ManyToOne(optional = false)
    public Collection collection;

    @Nullable
    @ManyToOne(optional = true) // could be root folder hence optional
    public Folder parent;

    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    public String name;
}
