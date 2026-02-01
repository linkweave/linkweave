package org.chainlink.api.shared.user;

import java.io.Serial;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.chainlink.api.shared.abstractentity.AbstractEntity;
import org.chainlink.api.shared.auth.FachRolle;
import org.chainlink.api.shared.util.EnumSetUtil;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.envers.Audited;

@Entity
@Table(
    indexes = {
        @Index(name = "ix_user_id", columnList = "id, version"),
        @Index(name = "ix_user_email", columnList = "email, id"),
        @Index(name = "ix_user_keycloakId", columnList = "keycloakId, id"),
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_user_keycloakId", columnNames = "keycloakId"),
        @UniqueConstraint(name = "uc_user_email", columnNames = "email"),
    }
)
@Audited
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class User extends AbstractEntity<User> {

    @Serial
    private static final long serialVersionUID = 4416654585500344772L;

    public static final ID<User> SYSTEM_ADMIN_ID =
        ID.parse("11111111-1111-1111-1111-111111111112", User.class);


    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = true)
    @IgnoreForIdClassTest
    private String keycloakId;

    @NotNull
    @NonNull
    @Column(nullable = false)
    private EmailAddress email;

    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = true, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    private String titel;

    @NotNull
    @NonNull
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false)
    private String nachname;

    @NotNull
    @NonNull
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false)
    private String vorname;



    @Valid
    @Embedded
    @Nullable
    private BenutzerLoginInfos loginInfos;

    @Nullable
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Column(nullable = true, length = DbConst.DB_ENUM_LIST_LENGTH)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String fachRollen;

    @NonNull
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<UserPermission> berechtigungen = new HashSet<>();

    /**
     * The actual deactivation happens in the login system (i.e. Keycloak or Entra) but this serves as a flag, so we
     * know which users were last thought of by esc as deaktiviert, this will be used to remove them from dropdowns etc
     */
    @Column(nullable = false)
    private boolean aktiv = true;

    @Nullable
    @Column(nullable = true)
    private OffsetDateTime timestampLastManuallyActivated;

    @NonNull
    public String getNameVorname() {
        return nachname + ' ' + vorname;
    }

    @NonNull
    public String getVornameName() {
        return vorname + ' ' + nachname;
    }

    public void setFachRollen(@NonNull EnumSet<FachRolle> fachRollen) {
        this.fachRollen = EnumSetUtil.toString(fachRollen);
    }

    @NonNull
    public EnumSet<FachRolle> getFachRollen() {
        return EnumSetUtil.fromString(FachRolle.class, fachRollen);
    }

    @NonNull
    public static ID<User> getSystemAdminId() {
       return SYSTEM_ADMIN_ID;
    }
}
