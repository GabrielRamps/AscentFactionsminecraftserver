package gg.ascent.plugin.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;

/** Small helpers for futures whose failures nobody would otherwise see. */
public final class Futures {

  private Futures() {}

  /** Logs a failure of {@code future} with {@code what} for context; returns the same future. */
  public static <T> CompletableFuture<T> logFailure(
      CompletableFuture<T> future, Logger log, String what) {
    future.whenComplete(
        (value, error) -> {
          if (error != null) {
            log.error("{} failed", what, unwrap(error));
          }
        });
    return future;
  }

  /** The exception a future actually failed with, without the {@link CompletionException}. */
  public static Throwable unwrap(Throwable t) {
    return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
  }
}
