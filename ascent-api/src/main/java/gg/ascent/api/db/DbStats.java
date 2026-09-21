package gg.ascent.api.db;

/**
 * A snapshot of the database layer, for {@code /ascent debug db}.
 *
 * @param poolActive connections currently handed out
 * @param poolIdle connections open and waiting
 * @param poolTotal connections open in total
 * @param poolWaiting threads blocked waiting for a connection
 * @param queuedTasks database tasks submitted and not yet started
 * @param completedTasks database tasks finished since startup, successful or not
 * @param failedTasks database tasks that ended in an exception since startup
 */
public record DbStats(
    int poolActive,
    int poolIdle,
    int poolTotal,
    int poolWaiting,
    int queuedTasks,
    long completedTasks,
    long failedTasks) {}
