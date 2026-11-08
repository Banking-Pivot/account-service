package com.banking.account_service.infrastructure.adapter.out.postgres;

import io.r2dbc.postgresql.api.PostgresqlException;

/** Reads PostgreSQL error details from the cause chain of a Spring data access exception. */
final class PostgresErrors {

    private static final String UNIQUE_VIOLATION = "23505";

    private PostgresErrors() {
    }

    /** Whether the error is a unique violation of the named constraint or unique index. */
    static boolean isUniqueViolation(Throwable error, String constraintName) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof PostgresqlException postgres) {
                return UNIQUE_VIOLATION.equals(postgres.getErrorDetails().getCode())
                        && postgres.getErrorDetails().getConstraintName().filter(constraintName::equals).isPresent();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
