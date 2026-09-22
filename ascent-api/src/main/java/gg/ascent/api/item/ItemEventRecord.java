package gg.ascent.api.item;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One {@code item_events} row.
 *
 * @param id row id
 * @param itemId the item
 * @param event what happened
 * @param actor who did it, or null for the server
 * @param counterparty the other player in a trade or pickup of someone else's drop, or null
 * @param context extra details as JSON, or null
 * @param createdAt when
 */
public record ItemEventRecord(
    long id,
    UUID itemId,
    ItemEvent event,
    @Nullable UUID actor,
    @Nullable UUID counterparty,
    @Nullable String context,
    Instant createdAt) {}
