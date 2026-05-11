package org.chainlink.api.bookmark;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ch.dvbern.dvbstarter.types.id.ID;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.auth.BelongsToCollection;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(indexes = {
    @Index(name = "ix_bookmark_collection_id", columnList = "collection_id, id"),
    @Index(name = "ix_bookmark_folder_id", columnList = "folder_id, id"),
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Bookmark extends AbstractEntity<Bookmark> implements BelongsToCollection {

    @Override
    public @NonNull ID<Collection> getCollectionId() {
        return collection.getId();
    }


    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_bookmark_collection"), nullable = false)
    private Collection collection;

    @Nullable
    @ManyToOne(optional = true)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_bookmark_folder"), nullable = true)
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

    @ManyToMany
    @JoinTable(name = "Bookmark_Tag",
        joinColumns = @JoinColumn(
            name = "bookmark_id",
            foreignKey = @ForeignKey(name = "fk_bookmark_tag_bookmark")),
        inverseJoinColumns = @JoinColumn(
            name = "tag_id",
            foreignKey = @ForeignKey(name = "fk_bookmark_tag_tag")),
        indexes = {
            @Index(name = "ix_bookmark_tag_bookmark_id", columnList = "bookmark_id, tag_id"),
            @Index(name = "ix_bookmark_tag_tag_id", columnList = "tag_id, bookmark_id"),
        }
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "click_count", nullable = false)
    private int clickCount = 0;

    @Nullable
    @Column(name = "last_clicked_at", nullable = true)
    private OffsetDateTime lastClickedAt;

    @Nullable
    @Column(name = "deleted_at", nullable = true)
    private OffsetDateTime deletedAt;

    @Nullable
    @Column(name = "suggestion_dismissed_at", nullable = true)
    private OffsetDateTime suggestionDismissedAt;

}
