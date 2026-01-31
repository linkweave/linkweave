package org.chainlink.infrastructure.db;

import jakarta.persistence.EntityManager;

public interface DbFlusher {
    /**
     * Delegates to {@link EntityManager#flush}
     */
    void flush();
}
