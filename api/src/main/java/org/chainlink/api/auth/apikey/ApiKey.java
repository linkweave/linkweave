package org.chainlink.api.auth.apikey;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Index(name = "ix_apikey_user_id", columnList = "user_id, id"),
    @Index(name = "ix_apikey_key_hash", columnList = "keyHash", unique = true),
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ApiKey extends AbstractEntity<ApiKey> {

    public ApiKey(@NonNull User user, @NonNull String name, @NonNull String keyHash, @NonNull String keyPrefix, @Nullable OffsetDateTime expiresAt) {
        this.user = user;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.expiresAt = expiresAt;
    }

    @NonNull
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_apikey_user"), nullable = false)
    private User user;

    @NotBlank
    @Size(max = DbConst.DB_API_KEY_NAME_LENGTH)
    @Column(nullable = false, length = DbConst.DB_API_KEY_NAME_LENGTH)
    private String name;

    @NotBlank
    @Column(nullable = false, length = DbConst.DB_API_KEY_HASH_LENGTH)
    private String keyHash;

    @NotBlank
    @Column(nullable = false, length = DbConst.DB_API_KEY_PREFIX_LENGTH)
    private String keyPrefix;

    @Nullable
    @Column(nullable = true)
    private OffsetDateTime expiresAt;

    @Nullable
    @Column(nullable = true)
    private OffsetDateTime lastUsedAt;

    @Nullable
    @Column(nullable = true)
    private OffsetDateTime revokedAt;
}
