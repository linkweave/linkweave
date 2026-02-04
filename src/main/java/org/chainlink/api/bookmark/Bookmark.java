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

import lombok.NoArgsConstructor;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table()
@NoArgsConstructor

public class Bookmark extends AbstractEntity<Bookmark> {

    @NotBlank
    @NonNull
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    public String title;

    @NotBlank
    @NonNull
    @Column(nullable = false, length = DbConst.DB_TEXTAREA_MAX_LENGTH_2000)
    public URL url;

    @Nullable
    @Column(nullable = true, length = DbConst.DB_TEXTAREA_MAX_LENGTH_5000)
    public String notes;

    @NonNull
    @ManyToOne(optional = false)
    public Folder folder;

    @ManyToMany()
    public Set<Tag> tags = new HashSet<>();

}
