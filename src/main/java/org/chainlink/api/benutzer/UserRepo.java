package org.chainlink.api.benutzer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;
import com.querydsl.core.types.Predicate;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.NonUniqueResultException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.shared.user.QUser;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.db.SmartJPAQuery;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.checkerframework.checker.nullness.qual.NonNull;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepo extends BaseRepo<User> {

    private final AppClock appClock;

    @NonNull
    public Optional<User> findByEmail(@NonNull EmailAddress email) {
        return db.selectFrom(QUser.user)
            .where(QUser.user.email.eq(email))
            .fetchOne();
    }

    // to read this from the CurrentUserService we make sure to run this in a new transaction, if it uses preexisting
    // transactions it may produce stack overflows
    @NonNull
    @Transactional(TxType.REQUIRES_NEW)
    public Optional<User> getByBenutzernameWithBerechtigungen(@NonNull EmailAddress email) {
        return getByPredicateWithBerechtigungen(QUser.user.email.eq(email));
    }

    // to read this from the CurrentUserService we make sure to run this in a new transaction, if it uses preexisting
    // transactions it may produce stack overflows
    @Transactional(TxType.REQUIRES_NEW)
    @NonNull
    public Optional<ID<User>> findBenutzerIdFromBenutzername(@NonNull EmailAddress parsedEmail) {
        Optional<UUID> userIdOpt = db.select(QUser.user.id)
            .from(QUser.user)
            .where(QUser.user.email.eq(parsedEmail))
            .fetchOne();
        return userIdOpt.map(uuid -> ID.of(uuid, User.class));
    }

    @NonNull
    private Optional<User> getByPredicateWithBerechtigungen(@NonNull Predicate where) {
        EntityGraph<User> entityGraph = db.getEntityManager().createEntityGraph(User.class);
        entityGraph.addAttributeNodes(QUser.user.berechtigungen.getMetadata().getName());

        SmartJPAQuery<User> benutzerSmartJPAQuery = db.selectFrom(QUser.user)
            .where(where);
        benutzerSmartJPAQuery = benutzerSmartJPAQuery.setHint("jakarta.persistence.loadgraph", entityGraph);

        List<User> fetchedUsers = benutzerSmartJPAQuery.fetch();

        if (fetchedUsers.size() > 1) {
            throw new NonUniqueResultException("Found more than one Benutzer");
        }

        if (fetchedUsers.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fetchedUsers.get(0));
    }

    @NonNull
    public List<User> findAll() {
        // Unsere Systemadmins nicht anzeigen
        List<UUID> systemAdmins = List.of(
            User.getSystemAdminId().getUUID()

        );

        return db.selectFrom(QUser.user)
            .where(QUser.user.id.notIn(systemAdmins))
            .orderBy(QUser.user.vorname.asc(), QUser.user.nachname.asc())
            .fetch();
    }

    @NonNull
    public List<User> findAllActive() {
        return db.selectFrom(QUser.user)
            .where(QUser.user.aktiv.isTrue())
            .orderBy(QUser.user.vorname.asc(), QUser.user.nachname.asc())
            .fetch();
    }




    @NonNull
    public User getSystemAdmin() {
        User benutzer = db.selectFrom(QUser.user)
            .where(QUser.user.id.eq(User.getSystemAdminId().getUUID()))
            .fetchFirst();
        if (benutzer == null) {
            throw new IllegalStateException("System admin not found");
        }
        return benutzer;
    }
}
