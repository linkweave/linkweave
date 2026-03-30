package org.chainlink.infrastructure.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
public class DatabaseService {

    @Inject
    Flyway flyway;

    public void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }
}
