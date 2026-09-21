package gg.ascent.api.config;

import java.time.ZoneId;

/**
 * {@code config.yml}.
 *
 * @param debug whether verbose diagnostic logging is on
 * @param timeZone the zone daily resets and displayed times use; the PRD fixes this at UTC
 */
public record CoreSettings(boolean debug, ZoneId timeZone) {}
