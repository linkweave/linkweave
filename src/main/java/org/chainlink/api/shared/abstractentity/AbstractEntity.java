package org.chainlink.api.shared.abstractentity;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.chainlink.api.shared.Util;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.hibernate.Hibernate;
import org.hibernate.envers.Audited;

@SuppressWarnings("ClassReferencesSubclass")
@MappedSuperclass
// Envers might interfere with liquibase diff: EJB3-Extension
@Audited
@EntityListeners(AbstractEntityListener.class)
@Getter
public abstract class AbstractEntity<Entity extends AbstractEntity<Entity>> implements Serializable {

    @Serial
    private static final long serialVersionUID = -8486070006848821940L;

    @Id
    @NotNull
    @NonNull
    @Column(nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    @IgnoreForIdClassTest
    private UUID id = UUID.randomUUID();

    @Version
    @Column(nullable = false)
    private long version;

    @Transient
    @Nullable
    @Setter
    private Long versionFromFrontend;

    @Transient
    @Setter
    private boolean optimisticLockingCheckDisabled = false;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime timestampErstellt;

    @Column(nullable = false)
    private OffsetDateTime timestampMutiert;

    /**
     * Nullable, wird aber durch den EntityListener gesetzt.
     */
    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, updatable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    // No validation: managed by DefaultEntityListener
    private String userErstellt;

    /**
     * Nullable, wird aber durch den EntityListener gesetzt.
     */
    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Column(nullable = false, updatable = false, length = DbConst.DB_DEFAULT_MAX_LENGTH)
    // No validation: managed by DefaultEntityListener
    private String userMutiert;

    @NonNull
    public ID<Entity> getId() {
        return ID.of(id, getClass());
    }

    public void setId(@NonNull ID<Entity> id) {
        this.id = id.getUUID();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }

        //noinspection ObjectEquality
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }

        AbstractEntity<?> that = (AbstractEntity<?>) o;

        return Objects.equals(
            Objects.requireNonNull(getId()),
            Objects.requireNonNull(that.getId())
        );
    }

    @Override
    public final int hashCode() {
        return getId().hashCode();
    }

    /**
     * Convenience: allows for debug output on lazy loaded fields without having an active hibernate session.
     *
     * @see Util#getSilent(Supplier)
     */
    protected <Field> String getSilent(Supplier<Field> supplier) {
        return Util.getSilent(supplier);
    }

    @Override
    public String toString() {
        return toStringBuilder()
            .toString();
    }

    protected ToStringBuilder toStringBuilder() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append(getId());
    }

    public Entity setVersion(long version) {
        this.version = version;

        @SuppressWarnings("unchecked")
        Entity result = (Entity) this;
        return result;
    }

    public Entity setTimestampErstellt(OffsetDateTime createdOn) {
        this.timestampErstellt = createdOn;

        @SuppressWarnings("unchecked")
        Entity result = (Entity) this;
        return result;
    }

    public Entity setTimestampMutiert(OffsetDateTime changedOn) {
        this.timestampMutiert = changedOn;

        @SuppressWarnings("unchecked")
        Entity result = (Entity) this;
        return result;
    }

    public Entity setUserErstellt(@Nullable String createdBy) {
        this.userErstellt = createdBy;

        @SuppressWarnings("unchecked")
        Entity result = (Entity) this;
        return result;
    }

    public Entity setUserMutiert(@Nullable String changedBy) {
        this.userMutiert = changedBy;

        @SuppressWarnings("unchecked")
        Entity result = (Entity) this;
        return result;
    }

    public void disableOptimisticLockingCheck() {
        this.optimisticLockingCheckDisabled = true;
    }
}
