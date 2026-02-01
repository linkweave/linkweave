package org.chainlink.api.shared.user;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.checkerframework.checker.nullness.qual.Nullable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class BenutzerLoginInfos implements Serializable {

    @Serial
    private static final long serialVersionUID = -4471131500166716925L;

    @Nullable
    @Column(nullable = true)
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String lastUsedIp;

    @Nullable
    @Column(nullable = true)
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @IgnoreForIdClassTest
    private String lastUsedSessionId;

    @Nullable
    @Column(nullable = true)
    @ToString.Include
    private OffsetDateTime lastUsedSessionTimestamp;

    @Nullable
    @Column(nullable = true)
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String oidcIssuer;
}
