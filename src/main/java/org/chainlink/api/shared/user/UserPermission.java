package org.chainlink.api.shared.user;

import java.io.Serial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.auth.Permission;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(
    indexes = @Index(name = "ix_userpermission_user", columnList = "user_id"),
    uniqueConstraints = @UniqueConstraint(
        name = "uc_userpermission_user_permission", columnNames = { "user_id", "permission" }
    )
)
@ToString(onlyExplicitlyIncluded = true)
public class UserPermission extends AbstractEntity<UserPermission> {

    @Serial
    private static final long serialVersionUID = 3647102230877993926L;

    @NotNull
    @NonNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_userpermission_user"),
        nullable = false,
        updatable = false)
    private User user;

    @NotNull
    @NonNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private Permission permission;
}

