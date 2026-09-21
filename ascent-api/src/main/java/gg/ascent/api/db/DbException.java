package gg.ascent.api.db;

/**
 * A database call failed.
 *
 * <p>Unchecked so it can travel through {@code CompletableFuture} chains; the cause is the
 * underlying {@link java.sql.SQLException} when there is one.
 */
public class DbException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DbException(String message) {
    super(message);
  }

  public DbException(String message, Throwable cause) {
    super(message, cause);
  }
}
