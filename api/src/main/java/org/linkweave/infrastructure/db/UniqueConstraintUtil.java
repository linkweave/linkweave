package org.linkweave.infrastructure.db;

import java.sql.SQLException;

import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import lombok.experimental.UtilityClass;

/**
 * Handles unique-constraint violations that arise during {@code persistAndFlush}.
 * <p>
 * SQLite wraps constraint errors as {@code GenericJDBCException} (Hibernate)
 * rather than {@code ConstraintViolationException} because the SQLite JDBC
 * driver does not set a SQLState that Hibernate recognises. This utility
 * detects both the standard Hibernate path and the SQLite-specific path by
 * inspecting the exception chain.
 */
@UtilityClass
public class UniqueConstraintUtil {

    /**
     * Run {@code persistAction} and translate a unique-constraint violation
     * on the named constraint into an {@link AppValidationException} with the
     * given i18n key.
     * <p>
     * Detection strategy:
     * <ol>
     *   <li>On databases with proper SQLState support (Postgres, etc.),
     *       Hibernate throws {@code ConstraintViolationException} with
     *       {@link org.hibernate.exception.ConstraintViolationException#getConstraintName()}.</li>
     *   <li>On SQLite, the exception surfaces as {@code GenericJDBCException}
     *       wrapping an {@code SQLException} whose message contains
     *       {@code SQLITE_CONSTRAINT_UNIQUE} and the column names. We match
     *       against {@code tableName.columnName} pairs to identify the right
     *       constraint.</li>
     * </ol>
     *
     * @param persistAction    the write+flush lambda (e.g. {@code () -> repo.persistAndFlush(entity)})
     * @param constraintName   the DB constraint name (e.g. {@code "uc_tag_name_collection"})
     * @param columnNames      column names qualified with the entity table name as they appear
     *                         in SQLite error messages (e.g. {@code "Tag.name, Tag.collection_id"})
     * @param translationKey   the i18n key for the translated user-facing message
     *                         (e.g. {@code "AppValidation.uq_tag_name_collection"})
     */
    public static void persistAndHandleUnique(Runnable persistAction,
                                              String constraintName,
                                              String columnNames,
                                              String translationKey) {
        try {
            persistAction.run();
        } catch (Exception e) {
            if (isUniqueConstraintViolation(e, constraintName, columnNames)) {
                throw new AppValidationException(
                    AppValidationMessage.genericMessage(translationKey));
            }
            throw e;
        }
    }

    private static boolean isUniqueConstraintViolation(Throwable e,
                                                       String constraintName,
                                                       String columnNames) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            // Strategy 1: Hibernate ConstraintViolationException (databases with SQLState support)
            if (cur instanceof org.hibernate.exception.ConstraintViolationException hce) {
                if (constraintName.equals(hce.getConstraintName())) {
                    return true;
                }
            }
            // Strategy 2: SQLite JDBC — match on SQLITE_CONSTRAINT_UNIQUE + qualified column names
            if (cur instanceof SQLException sqlEx) {
                String msg = sqlEx.getMessage();
                if (msg != null
                    && msg.contains("SQLITE_CONSTRAINT_UNIQUE")
                    && msg.contains(columnNames)) {
                    return true;
                }
            }
        }
        return false;
    }
}
