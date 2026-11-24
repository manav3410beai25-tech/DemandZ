package database;

import java.sql.SQLException;

/** Database error with a short message suitable for the terminal. */
public class DatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DatabaseException from(String operation, SQLException cause) {
        String state = cause.getSQLState();
        String suffix = state == null ? "" : " (SQL state " + state + ")";
        return new DatabaseException(operation + " failed" + suffix
                + ". Check the database setup and try again.", cause);
    }
}
