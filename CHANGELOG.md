# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the version is bumped
once per sprint (PRD §5.6).

## [Unreleased]

Sprint 1, the core platform (Epic 1).

### Added

- Configuration and messaging (E1-S1). `config.yml` plus one YAML per module
  (`ranks`, `enchants`, `mines`, `spawners`, `factions`, `contracts`, `events`,
  `combat`), seeded from the jar on first run and carrying the PRD's documented
  defaults. Each file parses into an immutable settings record behind
  `ConfigService`; modules never read YAML. Errors name the file and path.
  `/ascent reload` re-reads every file; one that fails keeps its previous
  values, and a broken file on first boot falls back to the bundled default so
  the server still starts.
- `messages.yml` holds every player-facing string, rendered with MiniMessage
  through `Messages`, with `<prefix>` and per-message placeholders. A missing
  key renders a visible marker rather than throwing.
- JaCoCo coverage reports on every test run.
- Database layer (E1-S2). HikariCP pool of 10 over MariaDB, Flyway migrations
  from `db/migration/V1__init.sql` (the 24 tables of PRD §6.3), and
  `DbExecutor`: SQL runs on worker threads and every result is handed back on
  the main thread. Credentials come only from the environment; `scripts/start.sh`
  exports them from `.env`. A database that cannot be reached at startup
  disables the plugin with one clear line. Redis is initialised but never
  required. CI runs a MariaDB service for the integration tests.
- Player data service (E1-S3). `PlayerProfile` is the one in-memory object per
  online player, loaded during the login handshake (created with rank 1, $1,000
  and no starter kit on first join), written every 60 seconds when changed, on
  quit, and all at once at shutdown. A failed load kicks with a friendly
  message. Sessions are recorded and play time accrues from them.
- Economy (E1-S4). `EconomyService` with deposit, withdraw, transfer and offline
  variants; every mutation logs a `money_transactions` row with its reason.
  Vault's `Economy` is implemented so third-party plugins see the same balance.
  `/bal`, `/pay` (1 to 2^53, k/m/b suffixes, offline recipients) and `/baltop`
  from a Redis sorted set refreshed after each autosave, with a database
  fallback while Redis is down.
- Admin command base (E1-S6). `/ascent give <player> money|xp`,
  `/ascent rank set|add`, `/ascent debug tps|db|items`, all behind
  `ascent.admin` and each written to `admin_actions`. Books, dust, scrolls and
  spawners are reserved until their epics.
- Item registry and audit log (E1-S5). `ItemRegistry.tag` stamps a UUIDv7 into
  the item's persistent data and records an `items` row with a CREATED event;
  `record` logs every later event (drops, pickups and destruction are logged
  automatically). A scan every five minutes counts each id across online
  inventories and ender chests, writes `dupe_alerts` when an id outnumbers
  what was created, and alerts staff in game and on Discord (webhook from
  `DISCORD_WEBHOOK_URL`). `/ascent item lookup <id>` prints an item's history
  and `/ascent debug items` the registry state. `ItemRegisteredEvent` fires on
  every tag.
- Config files written by an older build gain any setting a newer build adds,
  copied from the bundled default with its comments; the operator's own values
  are never touched.
- Rank ladder (E2-S1). `RankService.addXp` moves a player up the curve
  `round(400 * rank^1.4)`, carrying excess over and banking XP past rank 100
  for prestige; each rank-up fires `RankUpEvent` with a chat line, title and
  sound. Every grant lands in `xp_log`, aggregated per minute and source. PvP
  kills grant XP with the repeat-victim rule; other sources arrive with their
  epics. `/rank` shows progress and the next unlock, `/rank top` a Redis-backed
  top 10.
- Unlock hooks (E2-S2). `UnlockService` answers enchant slot cap, highest book
  tier, mine tier and open kits from `ranks.yml`, `enchants.yml` and the new
  `kits.yml`; `/rank unlocks` lists every threshold with ✔/✘.
- Kits (E2-S3). `/kit` opens a menu of the kits in `kits.yml` with rank gates
  and cooldown timers; `/kit <name>` claims directly. Cooldowns live in
  `kit_cooldowns`, so they survive restarts; overflow drops at the player's
  feet; kit items with pre-applied enchants are registry-tagged (the enchants
  themselves apply once the enchant engine lands).
- Personal mines (E4-S1). A void `mines` world with a grid of 64x64 plots
  allocated on first `/mine` and released after 24h idle, built with
  FastAsyncWorldEdit (or the Bukkit API when FAWE is absent) from a generated
  layout or a per-tier schematic; the pit refills every 10 minutes or at 70%
  mined with players moved to safety first; no entry to others' plots, no
  building, no combat, no spawns; mining pays rank XP by tier and reports to
  `ProgressBus`.
- Selling (E4-S2). `/sell` sells everything sellable at `prices.yml` values
  with an itemised receipt; `SellMultiplierService` answers 1.0 for now.
- Enchant catalog (E3-S1). `enchants.yml` now carries the 41 MVP enchants as
  data: display, tier, `applies-to`, `max-level`, per-level lore with
  `<param>` placeholders, effect type and per-level parameters, cooldown. The
  loader validates every field and refuses to enable the plugin on a malformed
  entry, naming the enchant and the field. `EnchantDefinition`, `ItemTarget`
  and `EffectType` join the API.
- Books (E3-S2). `EnchantService.createUnopenedBook` and `reveal`: right-click
  reveals one, sneak and right-click the stack; the roll picks a random enchant
  of the tier, a level weighted 1/level, and success and destroy from the tier
  ranges, all proven by tests. Opened books carry enchant, level, success and
  destroy in their data and the PRD's exact lore. `/ascent give <player> books
  <tier> [amount]` hands out unopened books.
- Applying books (E3-S3). Click an opened book from the cursor onto gear for
  one honest roll: success applies (or upgrades) the enchant and rebuilds the
  lore, a failed roll that hits the destroy chance shatters the item unless a
  White Scroll takes the blow, any other failure consumes the book. Type,
  slot cap and existing level are checked first and an incompatible book is
  kept. `EnchantApplyEvent` (cancellable) and `EnchantAppliedEvent` fire; the
  registry logs APPLIED, CONSUMED and DESTROYED; every roll lands in
  `enchant_rolls`. Kit enchants are applied for real.
- White Scrolls and Magic Dust (E3-S5). A scroll dragged onto gear protects it
  (one per item, `PROTECTED` in the lore) and is spent instead of the item on a
  destroy roll; dust dragged onto an opened book of its tier adds its percent
  to the success chance, capped at 100, with strong dust named in purple. Both
  registry-tagged; `/ascent give` hands them out until the Tinkerer, contracts
  and events do.
- Enchanter (E3-S6). `/enchanter` opens a menu with one slot per tier the rank
  allows, priced in vanilla XP levels from `enchants.yml`; left-click buys 1,
  right-click 8, shift-click 16 at no discount; too few levels is a red message
  with the menu kept open.
- Effect runtime (E3-S4). Hits between players resolve through a pure
  `CombatResolver`: attacker multipliers, then armor reductions summed and
  capped at 60%, then lifesteal and heals; potion, fire, knockback and Silence
  procs honour per-player cooldowns and chances, conditions (self/target below
  a health fraction, what each side holds, projectiles) gate them, and a
  silenced player's enchants do nothing. A one-second passive tick keeps
  passive potions and extra hearts on worn gear. Auto Smelt, Telepathy,
  Reforged, Experience and Lightning work on tools and bows. Safezones are a
  seam Epic 9 fills.
- Lore renderer (E11-S3). One `LoreRenderer` writes all custom item lore:
  enchants by tier then name with the tier's color, `PROTECTED` when scrolled,
  a kind footer, and a `[n]` enchant count on the name. Idempotent.

## [0.1.0] - 2026-09-21

Sprint 0: the development environment and project skeleton (Epic 0). Verified
end to end on a WSL2 dev box: server, databases, all eleven third-party
plugins, the build-deploy-restart loop, and 1.8-style combat with both client
generations.

### Added

- Gradle multi-module project (`ascent-api`, `ascent-plugin`) on Java 21, with a
  shaded plugin jar, Spotless/Google Java Format enforced by `check`, and a
  `copyToServer` task that deploys to the dev server. (E0-S2)
- `AscentApi` and `AscentProvider`: the service entry point later modules
  register against, plus a minimal `/ascent version` command so a fresh build can
  be verified on a live server. (E0-S2)
- GitHub Actions workflow building the project and uploading the plugin jar on
  every push. (E0-S2)
- `docker-compose.yml` for MariaDB 11 and Redis 7, bound to localhost, with
  credentials read from a git-ignored `.env`. (E0-S3)
- `server/PLUGINS.md`: third-party plugin sources and the OldCombatMechanics
  1.8-combat checklist. (E0-S3)
- `server/config/server.properties` template carrying the settings the PRD
  requires. (E0-S1)
- `scripts/start.sh` with Aikar's flags, sized for the configured heap. (E0-S1)
- `scripts/update-paper.sh` to pull a pinned Paper build through the Fill v3 API,
  verify its checksum and rewrite the pin in `gradle.properties`. (E0-S1)
- `dev.sh`: build, deploy, restart and wait for the server to report `Done`, with
  startup errors surfaced. (E0-S4)
- `TESTING.md` covering the dev loop, logs, Spark and the admin command table.
  (E0-S4)
- `scripts/bootstrap.sh`: one command that downloads Paper, accepts the EULA,
  merges `server/config/server.properties` into the server's own file and
  installs the third-party plugins. Every step is idempotent. (E0-S1, E0-S3)
- `scripts/install-plugins.sh`: resolves the third-party plugins from Modrinth
  against the pinned Minecraft version, with GitHub releases for Vault and
  OldCombatMechanics. A plugin it cannot resolve warns and is skipped rather
  than failing the run. (E0-S3)
- `scripts/lib.sh`: shared path, environment and logging helpers, replacing the
  copy of `read_env` that each script carried.
- `SETUP.md`: the ordered Epic 0 runbook, from an empty box to standing in the
  server, with a check after every step. Covers Windows via WSL2, since every
  script here needs bash, tmux and GNU coreutils.

- `scripts/configure-ocm.sh`: applies the E0-S3 combat settings to the config
  OldCombatMechanics generates, in place and idempotently, keeping the original
  as `config.yml.orig`. Fails loudly if OCM moves a setting. Also removes the
  offhand slot, which did not exist in 1.8 (owner's call, beyond the checklist).

### Fixed

- Google Java Format violation in the `AscentCommand` javadoc that failed CI.
- `update-paper.sh` picked a release candidate (`1.21.11-rc3`) as the latest
  version, because `sort -V` ranks the longer string above the release it
  precedes. Pre-release strings are now filtered out.
- Spark is fetched from its own Jenkins (ci.lucko.me). Confirmed against both
  APIs: on Modrinth it publishes only fabric/forge/neoforge/quilt builds, and
  it has no GitHub releases, so neither route could ever find it.
- `dev.sh` reported "Server up in 0s" by matching the previous boot's `Done`
  line in the old log, whose mtime the stop sequence had just refreshed. It
  now identifies the new log by inode, so only a line from the new boot counts.
- The combat test asked for two clients joined at once, which one account
  cannot do. It is one client at a time.
