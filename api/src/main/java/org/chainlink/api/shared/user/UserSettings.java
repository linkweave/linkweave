package org.chainlink.api.shared.user;

import java.io.Serial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.NonNull;

@Entity
@Audited
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "ix_usersettings_id", columnList = "user_id, version")
})
public class UserSettings extends AbstractEntity<UserSettings> {

    @Serial
    private static final long serialVersionUID = 1L;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_usersettings_user"),
        nullable = false,
        updatable = false
    )
    @NotNull
    @NonNull
    private User user;

    @Column(nullable = false)
    private boolean offlineCachingEnabled = true;

    public UserSettings(@NonNull User user) {
        this.user = user;
    }
}
