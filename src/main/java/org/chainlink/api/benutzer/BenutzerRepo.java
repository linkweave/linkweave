package org.chainlink.api.benutzer;

import java.time.OffsetDateTime;
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
import org.chainlink.api.shared.auth.FachRolle;
import org.chainlink.api.shared.benutzer.Benutzer;
import org.chainlink.api.shared.benutzer.QBenutzer;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.db.SmartJPAQuery;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BenutzerRepo extends BaseRepo<Benutzer> {

    private final AppClock appClock;

    @NonNull
    public Optional<Benutzer> findByEmail(@NonNull EmailAddress email) {
        return db.selectFrom(QBenutzer.benutzer)
            .where(QBenutzer.benutzer.email.eq(email))
            .fetchOne();
    }

    // to read this from the CurrentUserService we make sure to run this in a new transaction, if it uses preexisting
    // transactions it may produce stack overflows
    @NonNull
    @Transactional(TxType.REQUIRES_NEW)
    public Optional<Benutzer> getByBenutzernameWithBerechtigungen(@NonNull EmailAddress email) {
        return getByPredicateWithBerechtigungen(QBenutzer.benutzer.email.eq(email));
    }

    // to read this from the CurrentUserService we make sure to run this in a new transaction, if it uses preexisting
    // transactions it may produce stack overflows
    @Transactional(TxType.REQUIRES_NEW)
    @NonNull
    public Optional<ID<Benutzer>> findBenutzerIdFromBenutzername(@NonNull EmailAddress parsedEmail) {
        Optional<UUID> userIdOpt = db.select(QBenutzer.benutzer.id)
            .from(QBenutzer.benutzer)
            .where(QBenutzer.benutzer.email.eq(parsedEmail))
            .fetchOne();
        return userIdOpt.map(uuid -> ID.of(uuid, Benutzer.class));
    }

    @NonNull
    private Optional<Benutzer> getByPredicateWithBerechtigungen(@NonNull Predicate where) {
        EntityGraph<Benutzer> entityGraph = db.getEntityManager().createEntityGraph(Benutzer.class);
        entityGraph.addAttributeNodes(QBenutzer.benutzer.berechtigungen.getMetadata().getName());

        SmartJPAQuery<Benutzer> benutzerSmartJPAQuery = db.selectFrom(QBenutzer.benutzer)
            .where(where);
        benutzerSmartJPAQuery = benutzerSmartJPAQuery.setHint("jakarta.persistence.loadgraph", entityGraph);

        List<Benutzer> fetchedUsers = benutzerSmartJPAQuery.fetch();

        if (fetchedUsers.size() > 1) {
            throw new NonUniqueResultException("Found more than one Benutzer");
        }

        if (fetchedUsers.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fetchedUsers.get(0));
    }

    @NonNull
    public List<Benutzer> findAll() {
        // Unsere Systemadmins nicht anzeigen
        List<UUID> systemAdmins = List.of(
            Benutzer.getSystemAdminId().getUUID()

        );

        return db.selectFrom(QBenutzer.benutzer)
            .where(QBenutzer.benutzer.id.notIn(systemAdmins))
            .orderBy(QBenutzer.benutzer.vorname.asc(), QBenutzer.benutzer.nachname.asc())
            .fetch();
    }

    @NonNull
    public List<Benutzer> findAllActive() {
        return db.selectFrom(QBenutzer.benutzer)
            .where(QBenutzer.benutzer.aktiv.isTrue())
            .orderBy(QBenutzer.benutzer.vorname.asc(), QBenutzer.benutzer.nachname.asc())
            .fetch();
    }




    @NonNull
    public Benutzer getSystemAdmin() {
        Benutzer benutzer = db.selectFrom(QBenutzer.benutzer)
            .where(QBenutzer.benutzer.id.eq(Benutzer.getSystemAdminId().getUUID()))
            .fetchFirst();
        if (benutzer == null) {
            throw new IllegalStateException("System admin not found");
        }
        return benutzer;
    }
}
