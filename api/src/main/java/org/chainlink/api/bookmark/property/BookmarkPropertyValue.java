package org.chainlink.api.bookmark.property;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.infrastructure.db.DbConst;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_bookmark_property_value_definition_id", columnList = "propertyDefinition_id, bookmark_id, id"),
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uc_bookmark_property_value_bookmark_definition",
        columnNames = {"bookmark_id", "propertyDefinition_id"}
    )
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BookmarkPropertyValue extends AbstractEntity<BookmarkPropertyValue> {

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "bookmark_id", foreignKey = @ForeignKey(name = "fk_bookmark_property_value_bookmark"), nullable = false)
    private Bookmark bookmark;

    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "propertyDefinition_id", foreignKey = @ForeignKey(name = "fk_bookmark_property_value_definition"), nullable = false)
    private PropertyDefinition propertyDefinition;

    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String valueText;

    @Nullable
    @Column(precision = 19, scale = 2)
    private BigDecimal valueNumber;

    @Nullable
    @Column
    private Boolean valueBoolean;
}
