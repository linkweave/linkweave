package org.chainlink.infrastructure.db;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestScoped
class DBProducer {

    private final Db db;

    @Inject
    @SuppressWarnings("unused")
    public DBProducer(EntityManager em) {
        db = new Db(em);
    }

    /**
     * Mostly for unit tests: allows injecting a mock instance
     */
    @SuppressWarnings("unused")
    public DBProducer(Db db) {
        this.db = db;
    }

    @Produces
    @Default
    public Db produceDb() {
        return db;
    }

    @Produces
    @Default
    public DbFlusher produceDbFlusher() {
        return new DbFlusherImpl(db);
    }

    private record DbFlusherImpl(Db db) implements DbFlusher {
        @Override
        public void flush() {
            db.flush();
        }
    }
}
