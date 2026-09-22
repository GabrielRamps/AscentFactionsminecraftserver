package gg.ascent.api.item;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One {@code items} row.
 *
 * @param itemId the UUIDv7 stamped on the item
 * @param kind what it is
 * @param data kind-specific details as JSON (enchant, level, tier, mob type...)
 * @param quantityCreated how many were in the stack when it was tagged
 * @param createdBy who caused it to exist, or null for the server
 * @param createdReason a short reason such as {@code enchanter}, {@code kit:pvp} or {@code admin}
 * @param createdAt when
 * @param destroyedAt when it was recorded destroyed, or null while it may still exist
 */
public record ItemRecord(
    UUID itemId,
    ItemKind kind,
    String data,
    int quantityCreated,
    @Nullable UUID createdBy,
    String createdReason,
    Instant createdAt,
    @Nullable Instant destroyedAt) {}
