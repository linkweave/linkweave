package org.linkweave.api.shared.abstractentity;

import org.linkweave.infrastructure.clock.AppClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.user.CurrentUserService;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
@AllArgsConstructor(access = AccessLevel.PACKAGE)
// required for JPA :(
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AbstractEntityListener {

    @Inject
    AppClock clock;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ConfigService configService;

    @PrePersist
    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void prePersist(@NonNull AbstractEntity<?> entity) {
        var now = clock.offsetDateTime().now();
        var currentUserId = currentUserService.currentUserRef().getId().getId();

        // `timestampErstellt` is fill-if-empty so callers like the bookmark
        // import can preserve the date carried in the source file.
        if (entity.getTimestampErstellt() == null) {
            entity.setTimestampErstellt(now);
        }
        entity.setTimestampMutiert(now);
        entity.setUserErstellt(currentUserId);
        entity.setUserMutiert(currentUserId);
    }

    @PreUpdate
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void preUpdate(@NonNull AbstractEntity<?> entity) {
        var now = clock.offsetDateTime().now();

        entity.setTimestampMutiert(now);
        entity.setUserMutiert(currentUserService.currentUserRef().getId().getId());

    }

}
