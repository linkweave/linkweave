package org.chainlink.api.shared.abstractentity;

import ch.dvbern.dvbstarter.clock.AppClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.config.ConfigService;
import org.checkerframework.checker.nullness.qual.NonNull;

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

        entity.setTimestampErstellt(now);
        entity.setTimestampMutiert(now);
        entity.setUserErstellt(currentUserService.currentUserRef().getId().getId());
        entity.setUserMutiert(currentUserService.currentUserRef().getId().getId());


    }

    @PreUpdate
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void preUpdate(@NonNull AbstractEntity<?> entity) {
        var now = clock.offsetDateTime().now();

        entity.setTimestampMutiert(now);
        entity.setUserMutiert(currentUserService.currentUserRef().getId().getId());

    }

}
