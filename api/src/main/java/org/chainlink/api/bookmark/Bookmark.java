package org.chainlink.api.bookmark;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table()
@Getter
@Setter
@NoArgsConstructor
public class Bookmark extends AbstractEntity<Bookmark> {

    @NonNull
    @ManyToOne(optional = false)
    private Collection collection;

    @Nullable
    @ManyToOne(optional = true)
    private Folder folder;

    @NotBlank
    @NonNull
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String title;

    @NonNull
    @Column(nullable = false, length = DbConst.DB_TEXTAREA_MAX_LENGTH_2000)
    private URL url;

    @Nullable
    @Column(nullable = true, length = DbConst.DB_TEXTAREA_MAX_LENGTH_5000)
    private String description;

    @ManyToMany()
    private Set<Tag> tags = new HashSet<>();

}
