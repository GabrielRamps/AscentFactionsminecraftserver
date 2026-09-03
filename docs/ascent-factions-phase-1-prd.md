# Product Requirements Document: Ascent Factions — Phase 1 (Playable Core)

**Version**: 1.0
**Date**: 2026-09-03
**Author**: Product Owner + Claude
**Status**: Draft
**Companion docs**: `ascent-factions-concept.md` (game design), CosmicPvP Design Bible (reference mechanics)

---

## 0. How to use this document

This PRD is written to be handed directly to Claude Code. It assumes a **solo owner with no prior Minecraft server experience**, working with AI tooling on a Linux cloud box. It covers **Phase 1 only** — the playable core — in full detail. Phases 2–4 (sieges, faction level, dungeons, prestige, late-era Cosmic systems) are listed in §12 as backlog epics and get their own PRDs later.

**Working order:**

1. Do Epic 0 by hand first (it's the only epic you do yourself, and it takes an afternoon).
2. Then, for each story in sprint order (§10.2), open Claude Code in the repo and paste the story — its acceptance criteria, technical notes, and the relevant schema/config sections — as the task. Stories are written to be self-contained for that purpose.
3. Run the Definition of Done checklist (§9) before marking a story complete.
4. Don't skip the test server step in any story. A plugin that compiles is not a plugin that works.

**Scope decisions already made** (from the concept doc and the stack discussion):

- Modern Paper server with 1.8-style combat via OldCombatMechanics; 1.8 clients supported through ViaVersion/ViaBackwards/ViaRewind.
- Our own factions, enchants, rank, mines, spawner, contract, and event systems — these *are* the product and integrate too deeply to bolt onto third-party plugins.
- One Paper server (one "planet") for Phase 1. Proxy and multi-planet come in Phase 2.
- Enchant numbers (XP costs, success/destroy ranges) are **our own tunable defaults**, config-driven, since the original Cosmic values could not be verified.

---

## 1. Executive Summary

Ascent Factions is a Minecraft Java server that combines the team stakes and enchant-lottery gear economy of classic OP Factions with a prisons-style personal progression spine, so that every login produces progress that cannot be lost, while faction territory remains contestable in bounded, scheduled ways.

Phase 1 delivers the playable core on a single server: a personal rank ladder fed by every activity, the custom-enchant book gamble with Enchanter/Tinkerer/Alchemist stations, tiered personal mines, virtual-yield spawners with a value-based F-Top, a home-built factions system with claims, power, roles, and claim upkeep, daily contracts, KOTH and Envoy events, 1.8-style combat with a combat-log NPC, and the item-tracking and audit infrastructure that must exist before real players arrive. Sieges (the raid rework) ship in Phase 2; in Phase 1, claimed land is not raidable and wilderness is fair game.

**Product Vision**: The factions server an adult with a job can play — nostalgic 1.8 OP Factions feel, prisons-style guaranteed progress, and zero 3am base wipes.

**Target Users**: Former CosmicPvP / OP-factions players (now 20–30, limited time), current prisons players who want team stakes, and small friend groups looking for a "our faction" game.

**Key Success Metrics**:

- Day-7 retention ≥ 35% of players who reached Rank 10
- Median session produces ≥ 1 rank-up or ≥ 1 book applied for players under Rank 60
- Zero economy rollbacks caused by dupes during the first public season

---

## 2. Product Overview

### 2.1 Problem Statement

Classic OP Factions (CosmicPvP era) had the best gear economy and team drama in Minecraft, but it put all progress on the contestable layer. One offline raid erased weeks; patching walls was a job; staff refereed cannon rules by screenshot; exploits and dupes destroyed trust; the population fell off a cliff in week two of every map. Prisons solved the "every login = progress" problem but has no team stakes and nothing to fight for. Nobody has shipped the combination, and the original Cosmic factions server shut down in 2023 citing stale gameplay and drift from its PvP core.

### 2.2 Solution

A three-layer progression model:

- **Personal layer** (rank, mines, kits, unlocks) — never lost to other players.
- **Faction layer** (territory, spawners, vault, F-Top) — contestable, but only through bounded, scheduled sieges (Phase 2). In Phase 1, claimed land is safe and wilderness is not.
- **Gear layer** (armor, weapons, enchants) — at risk on death, exactly as in classic; this is what makes PvP matter.

XP is the universal progress currency: every activity produces it, and rank, faction level, and enchant books consume it. The enchant lottery is preserved intact and made honest (published odds, single-roll apply, item-ID tracking).

### 2.3 Goals and Objectives

**Business Goals**:

- Ship a stable Phase 1 to a closed beta of 30–50 players within ~12 weeks of starting Epic 1.
- Reach a first public season with 100–200 peak concurrent players on one server.
- Establish the codebase and ops practices (audit log, backups, config-driven tuning) that Phases 2–4 build on without rewrites.

**User Goals**:

- A 20-minute session always yields visible progress (rank XP, money, a book roll).
- Gear building feels like Cosmic: books, dust, scrolls, success/destroy, lore that looks right.
- Joining a faction feels worthwhile from day one (shared F-Top, faction chat, `/f home`, claims).
- Losing a fight costs gear, not weeks.

### 2.4 Success Metrics (KPIs)

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Day-1 / Day-7 retention | ≥ 60% / ≥ 35% | `players.last_seen` vs `first_seen` in DB |
| Median session length | 25–45 min | Session table (join/quit events) |
| Rank-ups per active player per day (Rank < 60) | ≥ 1.5 | `xp_log` aggregation |
| Books applied per active player per day | ≥ 2 | `item_events` where type = BOOK_APPLY |
| Faction membership rate (players Rank ≥ 10) | ≥ 70% | `faction_members` join |
| Server TPS (200 players) | ≥ 19.5 sustained | Spark / timings, alert < 18 |
| Dupe incidents requiring rollback | 0 in first season | Audit log review |

### 2.5 Assumptions and Constraints

**Assumptions**:

- Solo product owner, all code produced with Claude Code, reviewed by the owner.
- One Linux cloud server (8 vCPU / 32 GB RAM class) hosts the Paper server, MariaDB, and Redis for Phase 1.
- Players are primarily on Lunar/Badlion 1.8.9 or vanilla 1.21.x clients.
- Enchant catalog for MVP is ~35 enchants (a curated subset of the design bible's Simple→Legendary list); the full catalog grows in later phases via config, not code.

**Constraints**:

- Minecraft EULA: no selling gameplay advantages that affect other players beyond what the EULA permits; crate odds published.
- No Cosmic names, branding, or code. Mechanics only.
- Paper API only (no NMS/reflection) unless a story explicitly permits it, to keep upgrades cheap.
- Everything tunable lives in YAML config, not constants, so balance changes don't require redeploys.

### 2.6 Out of Scope (Phase 1)

Sieges and any TNT damage inside claims · Faction level/perks/quests · Prestige · Gear XP · Dungeons and world bosses · Outposts · Soul/Heroic/Mastery tiers · Armor sets, masks, trinkets, pets · Black/Randomization/Transmog scrolls and Orbs · Auction House (players trade in person or via `/trade` in Phase 1) · Proxy/multi-planet · Webstore integration · Bedrock crossplay · Custom client features.

---

## 3. User Personas and Use Cases

### 3.1 User Personas

#### Persona 1: "Marcus" — the Returning Grinder

**Demographics**: 26, works full-time, played Cosmic 2015–2017, ~1 hour on weeknights, 3–4 on weekends.
**Goals**: Feel the old loop again — spawners, books, god set — without needing to be online at 3am.
**Pain Points**: Got raided offline on every classic server he tried; quit each time within two weeks.
**Behaviors**: Logs in, grinds spawners with a podcast on, gambles books before bed.

#### Persona 2: "Kai" — the Fighter

**Demographics**: 19, student, 1.8.9 Lunar client, cares about hit-reg and KOTH.
**Goals**: PvP that matters. Kill streaks, KOTH wins, a set worth defending.
**Pain Points**: Prisons PvP is pointless; factions PvP is dominated by whoever has the most alts and spawners.
**Behaviors**: Ignores mining, lives in the warzone, trades event loot for gear.

#### Persona 3: "Dee" — the Faction Leader

**Demographics**: 30, organizes a Discord of eight friends, plays as "the one who runs the base."
**Goals**: A team project with visible shared progress; fair rules enforced by code, not staff mood.
**Pain Points**: Insiding, rule disputes, dead members holding land, having to be the 24/7 defender.
**Behaviors**: Manages roles and claims, sets goals, recruits, watches F-Top.

#### Persona 4: "Sam" — the Prisons Convert

**Demographics**: 22, never played factions, likes number-go-up progression and cosmetics.
**Goals**: Clear daily goals, a rank ladder, no risk of losing everything.
**Pain Points**: Factions' rulebook and raid culture are intimidating.
**Behaviors**: Does contracts, mines, gambles books; joins a faction for the chat and the mine bonus.

### 3.2 User Journey Map

1. **Awareness**: Server lists, YouTube nostalgia content, Discord invites from friends.
2. **Consideration**: Joins, sees the spawn hub, reads the "How it works" NPC/book: three layers, what's safe, what isn't.
3. **Onboarding**: Starter kit, `/kit starter`, first contract set, Tier 1 mine unlocked at Rank 1, first Simple book at ~15 minutes.
4. **Active Use**: Session loop — contracts → mine or spawners → Enchanter → apply → event if up. Joins or creates a faction by Rank 10 (a contract nudges it).
5. **Retention**: Rank ladder, weekly contract chain, F-Top movement, faction chat, seasonal reset with carryover (Phase 3).

---

## 4. Functional Requirements

### 4.1 Feature Overview

| # | Feature | Priority | Description |
|---|---------|----------|-------------|
| F0 | Dev environment & project skeleton | Must Have | Java 21, Paper server, Gradle multi-module repo, test loop |
| F1 | Core platform | Must Have | Config, DB access, player data, economy, item registry, audit log, messaging |
| F2 | Personal Rank ladder | Must Have | XP from all activities, Rank 1–100, unlock hooks |
| F3 | Custom Enchants engine | Must Have | Tiers, unopened/opened books, apply roll, slot caps, White Scroll, Magic Dust, Enchanter/Tinkerer/Alchemist |
| F4 | Personal Mines | Must Have | 3 tiers, instanced, timed reset, sell |
| F5 | Spawners | Must Have | Virtual yield, stacking, values, silk-touch pickup |
| F6 | Factions | Must Have | Create/join/roles/perms, claims, power, overclaim, upkeep, `/f home`, chat, fly, F-Top |
| F7 | Daily Contracts | Must Have | 3/day across archetypes, rewards |
| F8 | KOTH + Envoys | Must Have | Scheduled events, warzone, loot |
| F9 | Combat | Must Have | OCM 1.8 rules, combat tag, logger NPC, zones, gear gating |
| F10 | Anti-abuse & operations | Must Have | Anti-cheat, audit tooling, backups, staff commands, Discord webhooks |
| F11 | Presentation | Should Have | Scoreboard, tab, chat format, lore format, spawn hub |
| F12 | `/trade` | Should Have | Safe player-to-player trade GUI (AH deferred) |

### 4.2 User Stories and Acceptance Criteria

Story IDs are `E<epic>-S<n>`. Points use Fibonacci scale calibrated for AI-augmented development (1 pt ≈ an hour or two; 8 pts ≈ most of a week including testing).

---

#### Epic 0: Development Environment & Project Skeleton

**Description**: Get a Paper server running, a plugin building, and a fast edit-test loop. This epic is done by the owner by hand, following the steps; Claude Code can generate the files but the owner should run each command to learn the loop.

##### E0-S1: Local test server runs

**Story**: As the owner, I want a Paper server running on my dev box, so that I can test plugins.

**Acceptance Criteria**:

- [ ] Java 21 (Temurin) installed; `java -version` shows 21.
- [ ] Latest stable Paper 1.21.x jar downloaded from papermc.io into `~/ascent-server/`.
- [ ] `eula.txt` set to `eula=true`; server starts with `java -Xms2G -Xmx4G -jar paper.jar --nogui` and reaches "Done".
- [ ] `server.properties`: `online-mode=true`, `view-distance=6`, `simulation-distance=4`, `spawn-protection=0`, `allow-flight=true`.
- [ ] Owner can join from a client and is `op`.

**Technical Notes**: Use the Fill v3 API (`https://fill.papermc.io/v3/`) for downloads; the old `api.papermc.io/v2` is retired. Keep a `start.sh` with Aikar's flags.

**Story Points**: 2 · **Priority**: Must Have · **Dependencies**: none

##### E0-S2: Repository and build

**Story**: As the owner, I want a Gradle repo that builds a plugin jar, so that Claude Code has a place to work.

**Acceptance Criteria**:

- [ ] Git repo `ascent/` with Gradle (Kotlin DSL), Java 21 toolchain, modules `ascent-api` and `ascent-plugin`.
- [ ] `ascent-plugin` depends on `io.papermc.paper:paper-api:1.21.x-R0.1-SNAPSHOT` (compileOnly) and `ascent-api`.
- [ ] `./gradlew build` produces `ascent-plugin/build/libs/Ascent-<version>.jar` with shaded runtime deps (HikariCP, Jedis, Caffeine, Adventure is provided by Paper).
- [ ] `plugin.yml` (or `paper-plugin.yml`) declares name `Ascent`, main class, `api-version: '1.21'`, and soft-depends on `Vault`, `PlaceholderAPI`, `LuckPerms`.
- [ ] A `copyToServer` Gradle task copies the jar into `~/ascent-server/plugins/`.
- [ ] `.editorconfig`, Spotless (Google Java Format), and a GitHub Actions workflow that runs `./gradlew build` on push.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E0-S1

##### E0-S3: Third-party plugins installed and configured

**Story**: As the owner, I want the supporting plugins in place, so that my plugin builds on a stable base.

**Acceptance Criteria**:

- [ ] Installed: LuckPerms, Vault, PlaceholderAPI, OldCombatMechanics, ViaVersion, ViaBackwards, ViaRewind, GrimAC, Spark, FastAsyncWorldEdit, WorldGuard (for spawn/warzone regions only).
- [ ] OldCombatMechanics config: disable attack cooldown, old armour strength, old golden apples, old regen, sword blocking, old knockback, disable crafting of shields/elytra/tridents/crossbows/netherite items.
- [ ] A 1.8.9 client and a 1.21 client can both join; hits register on both.
- [ ] `docker-compose.yml` runs MariaDB 11 and Redis 7 with persistent volumes; credentials in `.env` (git-ignored).

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E0-S1

##### E0-S4: Fast test loop

**Story**: As the owner, I want a one-command rebuild-and-restart, so that testing a change takes under a minute.

**Acceptance Criteria**:

- [ ] `./dev.sh` runs `./gradlew build copyToServer` then restarts the server (systemd or tmux).
- [ ] Server boots to "Done" in < 30s on the dev box with all plugins.
- [ ] A `TESTING.md` explains: how to give yourself rank/XP/books via admin commands (added in later stories), how to read `logs/latest.log`, how to run `/spark tps`.

**Story Points**: 2 · **Priority**: Must Have · **Dependencies**: E0-S2, E0-S3

---

#### Epic 1: Core Platform

**Description**: The shared services every other epic depends on. Nothing player-facing ships here except the economy commands, but every later story assumes these exist.

##### E1-S1: Configuration and messaging

**Story**: As a developer, I want typed config loading and a message system, so that every feature is tunable and every string is editable.

**Acceptance Criteria**:

- [ ] `config.yml` plus one YAML per module (`ranks.yml`, `enchants.yml`, `mines.yml`, `spawners.yml`, `factions.yml`, `contracts.yml`, `events.yml`, `combat.yml`).
- [ ] `messages.yml` holds every player-facing string with MiniMessage formatting and placeholders (`<player>`, `<amount>`).
- [ ] `/ascent reload` reloads all YAML without restart; invalid YAML logs a clear error and keeps the last good config.
- [ ] A `Settings` object per module exposes typed getters; no raw `getConfig().getString(...)` outside loaders.

**Technical Notes**: Use Paper's Adventure API for all chat; never legacy `§` codes in code (lore may be rendered from MiniMessage).

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E0-S2

##### E1-S2: Database layer

**Story**: As a developer, I want a pooled MariaDB connection and migrations, so that all persistent data has one home.

**Acceptance Criteria**:

- [ ] HikariCP pool (max 10) configured from `.env`/`config.yml`; connection failure at startup disables the plugin with a clear log line.
- [ ] Flyway (or hand-rolled versioned SQL) applies migrations from `resources/db/migration/V1__init.sql` onward.
- [ ] All DB calls run off the main thread via a `DbExecutor`; results are handed back to the main thread with `CompletableFuture` + `Bukkit.getScheduler().runTask`.
- [ ] Redis client (Jedis) initialised but only used for the leaderboard cache in Phase 1.
- [ ] Schema in §6.3 created by V1 migration.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S1

##### E1-S3: Player data service

**Story**: As a player, I want my data loaded when I join and saved reliably, so that progress is never lost.

**Acceptance Criteria**:

Given a player joins for the first time
When the join event fires
Then a `players` row is created with rank 1, xp 0, balance = `starting_balance` (default $1,000), and the starter kit flag unset.

- [ ] Data loads async on `AsyncPlayerPreLoginEvent`; the player is kicked with a friendly message if the load fails.
- [ ] Dirty-flag autosave every 60s and on quit; server shutdown flushes all.
- [ ] `PlayerProfile` is the single in-memory object other modules read from; no module keeps its own player map.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S2

##### E1-S4: Economy

**Story**: As a player, I want a money balance with `/bal`, `/pay`, and `/baltop`, so that the shop, spawners, and claims have a currency.

**Acceptance Criteria**:

- [ ] `EconomyService` with `deposit`, `withdraw`, `transfer`, `getBalance`; all mutations write a `money_transactions` row with reason enum.
- [ ] Implements Vault's `Economy` interface so third-party plugins see the same balance.
- [ ] `/pay <player> <amount>` validates positive amount, sufficient funds, target online-or-known; min 1, max 2^53.
- [ ] `/baltop` reads from a Redis sorted set refreshed every 60s.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S3

##### E1-S5: Item registry and audit log

**Story**: As the owner, I want every valuable item to carry a unique ID and every important item event logged, so that dupes can be detected and rolled back.

**Acceptance Criteria**:

- [ ] `ItemRegistry.tag(ItemStack, ItemKind)` writes a UUIDv7 into the PDC key `ascent:item_id` and inserts an `items` row (kind, created_by, created_reason).
- [ ] Tagged kinds: enchant books (opened and unopened), scrolls, dust, spawners, any armor/weapon with ≥ 1 custom enchant, kit items above Simple tier.
- [ ] `ItemEventLog.record(itemId, eventType, actorUuid, context)` for: CREATED, APPLIED, CONSUMED, DESTROYED, TRADED, DROPPED, PICKED_UP, SOLD, ADMIN_GIVE.
- [ ] A scheduled job (every 5 min) scans online inventories + enderchests for duplicate `item_id`s and writes `dupe_alerts`; staff get an in-game and Discord alert.
- [ ] `/ascent item lookup <uuid>` prints the item's full event history.

**Technical Notes**: Stackable tagged items (dust, spawners) share one ID per stack creation; splitting a stack keeps the ID — the duplicate scan only alerts when the *total count* across inventories exceeds `items.quantity_created`.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E1-S2, E1-S3

##### E1-S6: Admin and staff command base

**Story**: As staff, I want a permissioned `/ascent` command tree, so that testing and moderation don't require console access.

**Acceptance Criteria**:

- [ ] `/ascent give <player> <kind> [args]` for books, dust, scrolls, spawners, money, xp.
- [ ] `/ascent rank set|add <player> <value>`; `/ascent reload`; `/ascent debug tps|db|items`.
- [ ] All admin commands require `ascent.admin` via LuckPerms and are logged to `admin_actions`.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E1-S3

---

#### Epic 2: Personal Rank Ladder

**Description**: The "every login = progress" spine. XP from every activity; Rank 1–100; unlock hooks other modules query.

##### E2-S1: XP accrual and rank-up

**Story**: As a player, I want to earn XP from everything I do and rank up, so that every session moves me forward.

**Acceptance Criteria**:

Given a player at Rank r with `xp_to_next(r) = round(400 * r^1.4)`
When they earn XP from any registered source
Then progress increments, and on reaching the threshold the rank increases, excess XP carries over, and a rank-up message + sound + title plays.

- [ ] XP sources and default values (all in `ranks.yml`): mine block by tier (T1 2, T2 5, T3 12); spawner melee kill (by mob, e.g. zombie 8, iron golem 40); PvP kill 500 (same victim within 10 min → 10% value); event participation KOTH 2,000 / Envoy crate 300; contract completion (per contract); vanilla XP orbs ×0.
- [ ] Player XP is separate from vanilla XP levels. Vanilla XP remains the Enchanter currency (see E3).
- [ ] Rank 100 caps; further XP is banked in `xp_banked` for Phase 3 prestige.
- [ ] `/rank` shows current rank, progress bar, next unlock; `/rank top` shows top 10 (Redis).

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S3

##### E2-S2: Unlock hooks

**Story**: As a developer, I want a single `UnlockService` other modules query, so that rank gates are consistent.

**Acceptance Criteria**:

- [ ] `UnlockService.enchantSlotCap(rank) = 6 + floor(rank / 10)` (max 16).
- [ ] `UnlockService.maxBookTier(rank)`: Simple 1, Unique 5, Elite 15, Ultimate 25, Legendary 40 (config).
- [ ] `UnlockService.mineTier(rank)`: T1 at 1, T2 at 20, T3 at 40.
- [ ] `UnlockService.kitTier(rank)`: starter always; `rank10`, `rank25`, `rank50`, `rank75` kits defined in `kits.yml`.
- [ ] `/rank unlocks` lists all thresholds with ✔/✘.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E2-S1

##### E2-S3: Kits

**Story**: As a player, I want `/kit` with rank-gated daily kits, so that gearing up has a floor.

**Acceptance Criteria**:

- [ ] `/kit` opens a GUI listing kits with cooldown timers; `/kit starter` once per 24h from Rank 1.
- [ ] Kit contents in `kits.yml` (items, amounts, optional pre-applied enchants); items above Simple tier are registry-tagged.
- [ ] Cooldowns persist across restarts (`kit_cooldowns` table).

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E2-S2, E1-S5

---

#### Epic 3: Custom Enchants Engine

**Description**: The core lottery. Tiers, books, the apply roll, slot caps, White Scrolls, Magic Dust, and the three stations. The enchant *catalog* is data (`enchants.yml`); the *engine* is code. MVP ships ~35 enchants; the design bible's full list is added later by config.

##### E3-S1: Enchant catalog and definitions

**Story**: As a developer, I want enchants defined in YAML with a fixed schema, so that adding enchants never requires code for common effect types.

**Acceptance Criteria**:

- [ ] `enchants.yml` schema per enchant: `id`, `display`, `tier` (SIMPLE|UNIQUE|ELITE|ULTIMATE|LEGENDARY), `applies_to` (list of HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, AXE, BOW, PICKAXE, HOE, ALL_ARMOR, ALL_WEAPONS), `max_level`, `description` (per level, MiniMessage), `effect` (type + params per level), `cooldown_ticks`, `proc_chance` per level.
- [ ] Effect types implemented in code for MVP: `POTION_ON_HIT`, `POTION_ON_HURT`, `POTION_PASSIVE`, `DAMAGE_MULTIPLIER` (conditional: target holding X, self below Y% HP), `DAMAGE_REDUCTION_STACKABLE`, `LIFESTEAL`, `EXTRA_HEARTS`, `HEAL_ON_KILL`, `LIGHTNING_ON_ARROW`, `AUTO_SMELT`, `TELEPATHY`, `NO_DURABILITY_LOSS`, `KNOCKBACK_MULTIPLIER`, `DOUBLE_STRIKE`, `SILENCE`.
- [ ] Tier display colors: Simple `<white>`, Unique `<green>`, Elite `<aqua>`, Ultimate `<yellow>`, Legendary `<gold>` (config).
- [ ] Loader validates schema and refuses to enable the plugin on a malformed enchant, printing the enchant id and field.

**Technical Notes**: MVP catalog (35): Simple — Aquatic, Glowing, Haste, Auto Smelt, Experience, Obliterate, Lightning, Confusion; Unique — Berserk, Ward, Curse, EnderShift, Molten, Featherweight, Telepathy, Famine; Elite — Springs, Cactus, Execute, Frozen, Poison, Reforged, Voodoo, Wither, Vampire; Ultimate — Angelic, Assassin, Dodge, Heavy, Tank, Valor, Enrage, Ice Aspect; Legendary — Overload, Lifesteal, Deathbringer, Double Strike, Enlighted, Silence, Gears, Armored. Effects follow the design bible descriptions; numbers are ours.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E1-S1

##### E3-S2: Unopened and opened books

**Story**: As a player, I want to buy an unopened tier book and reveal it, so that I get the lottery moment.

**Acceptance Criteria**:

Given a player right-clicks an unopened book of tier T
When the reveal runs
Then it becomes an opened book with a random enchant from T's pool, a random level in `[1, max_level]` weighted toward lower levels (weight = 1/level), success% and destroy% rolled uniformly from T's ranges, and lore in the exact format below.

- [ ] Default ranges (`enchants.yml`): Simple success 60–100 / destroy 0–20; Unique 50–95 / 5–35; Elite 40–90 / 10–50; Ultimate 30–80 / 20–70; Legendary 20–70 / 30–90.
- [ ] Sneak + right-click reveals the whole stack.
- [ ] Opened book lore format (MiniMessage, tier-colored name):
  ```
  <tier_color><bold>Lifesteal III</bold>
  <green>100% Success Rate
  <red>45% Destroy Rate
  <gray>Applies to: Swords
  <gray>Heals 1.5 hearts on hit (12% chance)
  <dark_gray>Drag and drop onto item to apply.
  ```
- [ ] Opened books carry PDC: `enchant_id`, `level`, `success`, `destroy`, `item_id` (registry). Unopened books carry `tier`, `item_id`.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E3-S1, E1-S5

##### E3-S3: Applying a book (the roll)

**Story**: As a player, I want to drag a book onto gear and get one honest roll, so that the gamble is real and understandable.

**Acceptance Criteria**:

Given an opened book is dragged onto a compatible item in the player's inventory
When the drop happens
Then exactly one roll `r = random(0,100)` decides: if `r < success` → enchant applied; else if `random(0,100) < destroy` → item destroyed; else → book consumed, item unchanged. Message and sound differ for each of the three outcomes.

- [ ] Compatibility: item type in `applies_to`; item's current custom-enchant count < slot cap (rank-based, plus orb bonus in Phase 3); the item does not already have this enchant at ≥ this level (higher level upgrades; lower or equal is rejected before the roll).
- [ ] Destroy: item is removed; if the item has a White Scroll flag, the scroll is consumed instead and the item survives. Registry logs DESTROYED or CONSUMED accordingly.
- [ ] Applied enchant is stored in PDC as a map `enchant_id → level`; lore is rebuilt by `LoreRenderer` (see E11-S3), sorted by tier then name.
- [ ] Attempting to drop a book on an incompatible item does nothing and shows a red message; the book is not consumed.
- [ ] Every outcome writes an `enchant_rolls` row (player, item_id, book_id, success, destroy, outcome) — this is the data that lets us prove the odds are honest.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E3-S2, E2-S2

##### E3-S4: Effect runtime

**Story**: As a player, I want my enchants to actually do things in combat, so that gear matters.

**Acceptance Criteria**:

- [ ] `EnchantListener` hooks `EntityDamageByEntityEvent`, `PlayerItemDamageEvent`, `BlockBreakEvent`, `ProjectileHitEvent`, `PlayerMoveEvent` (throttled), and an every-second passive tick for online players.
- [ ] Per-player per-enchant cooldown map; procs respect `cooldown_ticks`.
- [ ] `Silence` blocks other players' enchant procs on the target for its duration; stackable reduction enchants (Heavy/Tank/Valor/Armored) sum across worn pieces and are capped at 60% total reduction.
- [ ] Effects never run for players in safezones.
- [ ] Damage math order: base 1.8 damage (OCM) → attacker multipliers → victim reductions → lifesteal/heal.
- [ ] Unit tests with MockBukkit for each effect type.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E3-S3

##### E3-S5: White Scroll and Magic Dust

**Story**: As a player, I want scrolls and dust, so that I can protect items and improve odds.

**Acceptance Criteria**:

- [ ] White Scroll: drag onto any gear → sets `white_scroll=true`, adds lore line `<white>PROTECTED`; one per item; consumed on a failed destroy.
- [ ] Magic Dust: item with `percent` (1–15) and `tier`; drag onto an opened book of the same tier → `success = min(100, success + percent)`; dust consumed. Dust ≥ 10% renders with a distinct name color.
- [ ] Both are registry-tagged; both obtainable from Tinkerer (E3-S7), contracts, and events.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E3-S3

##### E3-S6: Enchanter

**Story**: As a player, I want to buy unopened books with vanilla XP levels, so that grinding converts into gear.

**Acceptance Criteria**:

- [ ] `/enchanter` (and an NPC at spawn) opens a GUI with one slot per tier the player has unlocked, showing cost in XP levels and the player's current levels.
- [ ] Default costs (`enchants.yml`, XP *levels*): Simple 10, Unique 20, Elite 35, Ultimate 55, Legendary 90. Buying in stacks of 1/8/16 with no discount.
- [ ] Insufficient levels → red message, GUI stays open.

**Technical Notes**: Spawner melee kills drop vanilla XP orbs (E5); this is the intended pipeline. Vanilla XP is *not* the rank XP.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E3-S2

##### E3-S7: Tinkerer

**Story**: As a player, I want to trade unwanted books and gear for XP bottles and dust, so that bad rolls aren't dead weight.

**Acceptance Criteria**:

- [ ] `/tinkerer` opens a two-pane GUI: left = items offered, right = preview of returns; confirm button.
- [ ] Exchange table (config): opened book → XP bottle worth 40% of its tier's Enchanter cost; enchanted gear → sum of its enchants' book values × 0.5; unopened books not accepted.
- [ ] 10% chance per Elite+ book to also return a Magic Dust of that tier (1–8%).
- [ ] Returned XP bottles are custom items that grant levels on use (not vanilla bottles).

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E3-S5

##### E3-S8: Alchemist

**Story**: As a player, I want to combine two identical books into a higher level, so that grinding duplicates has a path.

**Acceptance Criteria**:

- [ ] `/alchemist` GUI with two input slots and one output; requires same `enchant_id` and same `level`, level < max.
- [ ] Output: level + 1, success = avg of inputs, destroy = avg of inputs, cost in XP levels = tier base × level.
- [ ] Two Magic Dusts of the same tier → one dust of the next tier with `percent = avg` (Legendary cannot be upgraded).

**Story Points**: 3 · **Priority**: Should Have · **Dependencies**: E3-S5

---

#### Epic 4: Personal Mines

**Description**: Prisons-style instanced mines gated by rank. Solo-safe, timed reset, sell output.

##### E4-S1: Mine world and instancing

**Story**: As a player, I want `/mine` to teleport me to my own mine, so that I can grind safely.

**Acceptance Criteria**:

- [ ] A dedicated `mines` world (void, no mob spawning, no PvP via WorldGuard flag) holds a grid of 64×64 plots; plots are allocated on first `/mine` and released after 24h of inactivity.
- [ ] Each tier has a schematic (`mines/tier1.schem` etc.) pasted with FAWE on allocation and on reset.
- [ ] Block composition per tier (config): T1 stone 70 / coal 25 / iron 5; T2 stone 40 / iron 35 / gold 20 / diamond 5; T3 iron 30 / gold 30 / diamond 30 / emerald 10.
- [ ] Reset every 10 minutes or when 70% mined, whichever first; players inside are teleported to the plot spawn point before the paste.
- [ ] Other players cannot enter your plot (teleport denied; region is per-owner).

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E2-S2, E0-S3

##### E4-S2: Selling and rewards

**Story**: As a player, I want to sell what I mine, so that mining produces money and rank XP.

**Acceptance Criteria**:

- [ ] `/sell` (and a sell sign/NPC at the plot spawn) sells all sellable blocks in inventory at `prices.yml` values; shows an itemised receipt.
- [ ] Mining a block awards rank XP per E2-S1 and increments contract counters.
- [ ] Sell multiplier = 1.0 in Phase 1 (prestige and outpost bonuses later); expose `SellMultiplierService` now so later phases plug in.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E4-S1, E1-S4

---

#### Epic 5: Spawners

**Description**: The F-Top backbone. Virtual yield — no live mob ticking — with melee kills for vanilla XP.

##### E5-S1: Spawner placement, stacking, and pickup

**Story**: As a faction member, I want to place and stack spawners in my claims, so that I can build value.

**Acceptance Criteria**:

- [ ] Spawner items carry `mob_type` and registry `item_id`; shop sells them (E5-S3).
- [ ] Placing a spawner on an existing spawner of the same type stacks it (cap 40 per block); hologram above shows `<type> Spawner x<count>`.
- [ ] Spawners can only be placed in the placer's faction claims; placement elsewhere is refused.
- [ ] Silk Touch pickaxe mining returns the full stack as items to the miner's inventory (or drops if full); non-silk mining does nothing. Only faction members with the `SPAWNERS` role permission can mine them.
- [ ] `placed_spawners` row per block with faction_id, type, count, world/x/y/z.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E6-S2, E1-S5

##### E5-S2: Virtual yield

**Story**: As a grinder, I want spawners to produce a stacked mob I can kill for loot and XP, so that grinding feels like Cosmic without lagging the server.

**Acceptance Criteria**:

- [ ] A spawner block with a player within 5 chunks accumulates `pending` at `rate_per_second × count` (config per type; default 0.2/s, so a 40-stack yields 8 mobs/s).
- [ ] One representative entity per spawner stack is kept alive with a name tag `<type> x<pending>`; it does not move, burn, or despawn; AI disabled.
- [ ] A melee hit on the entity kills one "unit": drops that mob's loot table (config) and vanilla XP orbs per type; `pending` decrements. Drops and XP are batched every tick to at most 1 item stack + 1 XP orb per player per spawner.
- [ ] Non-melee damage (arrows, potions, TNT) does nothing.
- [ ] `pending` caps at `count × 60`; no accumulation while no player is nearby.
- [ ] Load test: 500 stacked spawner blocks with 20 players grinding keeps TPS ≥ 19.5.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E5-S1

##### E5-S3: Shop and spawner values

**Story**: As a player, I want a `/shop` that buys and sells spawners and blocks, so that money has somewhere to go.

**Acceptance Criteria**:

- [ ] `/shop` GUI with categories: Spawners, Blocks, Food/Potions, Tools.
- [ ] Spawner buy/value table (config; Phase 1): Zombie $50,000 / Skeleton $50,000 / Cow $75,000 / Creeper $250,000 / Blaze $250,000 / Enderman $250,000 / Iron Golem $1,000,000. Sell-back 50% of buy.
- [ ] Buying tagged items writes registry rows; buying is blocked while combat-tagged.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S4, E1-S5

---

#### Epic 6: Factions

**Description**: Home-built factions: membership, roles, claims, power, overclaim, upkeep, home, chat, fly, F-Top. No TNT in claims in Phase 1.

##### E6-S1: Faction lifecycle and membership

**Story**: As a player, I want to create, join, and leave factions, so that I can play as a team.

**Acceptance Criteria**:

- [ ] `/f create <name>` (3–16 chars, alphanumeric, unique, profanity filter, cost $10,000); creator becomes LEADER.
- [ ] `/f invite`, `/f join`, `/f leave`, `/f kick`, `/f disband` (leader only, requires confirmation), `/f promote|demote`, `/f leader <player>`.
- [ ] Roles: LEADER, OFFICER, MEMBER, RECRUIT. Member cap 15 (config).
- [ ] `/f info [faction]`, `/f list`, `/f who <player>`.
- [ ] Leaving or being kicked while combat-tagged is blocked.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S3

##### E6-S2: Claims and power

**Story**: As a faction member, I want to claim land protected by power, so that our base is ours.

**Acceptance Criteria**:

- [ ] Per-player power: max 150, +1/min online or offline, −10 on death outside safezone, floor −20. Faction power = sum of members. Claims allowed = floor(faction power / 10) (config).
- [ ] `/f claim` claims the current chunk if adjacent to an existing claim (first claim = core, any location outside warzone/safezone and ≥ 200 blocks from spawn). `/f unclaim`, `/f unclaimall` (leader).
- [ ] Overclaim: if a faction's claim count exceeds its allowed claims, any other faction may `/f claim` its non-core chunks. Core can only be lost when it is the last chunk.
- [ ] Protection in claims: non-members cannot break/place/interact with containers or doors; buttons/levers configurable; explosions do no block damage in claims (Phase 1 rule).
- [ ] `/f map` shows a 21×9 chunk map with colors; `/f sethome` (core chunk only), `/f home` (3s warmup, cancelled by damage or movement, blocked while combat-tagged).
- [ ] Claim upkeep: $500/chunk/day deducted from the faction vault at 00:00 server time; if unpaid for 3 consecutive days all claims are released and the faction is notified daily.
- [ ] `/f vault` deposit/withdraw money; `/f perms` GUI: per-role toggles for BUILD, CONTAINERS, CLAIM, SPAWNERS, INVITE, VAULT_WITHDRAW, SETHOME, FLY.

**Story Points**: 13 → split: E6-S2a claims/power/overclaim (8), E6-S2b protection + perms (5), E6-S2c upkeep + vault (3) · **Priority**: Must Have · **Dependencies**: E6-S1

##### E6-S3: Faction chat, fly, and relations

**Story**: As a faction member, I want faction chat and fly in our land, so that coordinating and building is comfortable.

**Acceptance Criteria**:

- [ ] `/f chat` toggles FACTION / ALLY / PUBLIC; `/f ally <faction>`, `/f enemy`, `/f neutral`; max 2 allies (config). Ally members cannot damage each other; allies can enter but not build.
- [ ] `/f fly` enables flight only inside own claims; disabled instantly (with 3s fall protection) on leaving claims, taking damage, or an enemy entering within 32 blocks.
- [ ] Chat prefix shows relation color: green own faction, purple ally, red enemy, white neutral.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E6-S2

##### E6-S4: F-Top

**Story**: As a faction leader, I want `/f top`, so that we have something to climb.

**Acceptance Criteria**:

- [ ] Faction value = Σ (placed spawner count × spawner value) + faction vault balance × 0.1 (config weights). Recalculated every 5 min into `ftop_snapshots` and a Redis sorted set.
- [ ] `/f top` paginated GUI: rank, name, value, member count, leader; top 3 highlighted.
- [ ] Weekly payout job (Sunday 00:00) records the top 10 into `ftop_payouts` for staff to fulfil manually in Phase 1 (store credit / cosmetics later).

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E5-S1, E6-S2

---

#### Epic 7: Daily Contracts

##### E7-S1: Contract pool and assignment

**Story**: As a player, I want three daily contracts spanning playstyles, so that I always have a short goal.

**Acceptance Criteria**:

- [ ] `contracts.yml` pool with `id`, `archetype` (GRINDER|FIGHTER|GAMBLER|SOCIAL), `objective` (type + target, e.g. `MINE_BLOCKS 3000`, `SPAWNER_KILLS 500`, `APPLY_BOOKS 5`, `KOTH_PARTICIPATE 1`, `ENVOY_CRATES 3`, `PVP_KILLS 3`, `JOIN_FACTION 1`, `SELL_VALUE 50000`), `min_rank`, `rewards` (rank xp, money, book tier).
- [ ] At 00:00 server time (or first login after), each player receives 3 contracts: one GRINDER, one from FIGHTER∪GAMBLER, one random, all with `min_rank ≤ rank`; never the same id two days running.
- [ ] `/contracts` GUI shows progress bars; completion pays instantly with a toast.
- [ ] Objectives are counted through a single `ProgressBus` that other modules publish to (`ProgressBus.publish(player, MINE_BLOCKS, 1)`); no module knows about contracts directly.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E2-S1, E3-S3, E4-S2, E5-S2, E6-S1

---

#### Epic 8: KOTH and Envoys

##### E8-S1: Warzone and safezone regions

**Story**: As a player, I want a clear spawn safezone and a warzone ring, so that I know where PvP is on.

**Acceptance Criteria**:

- [ ] WorldGuard regions `spawn` (no PvP, no damage, no build) and `warzone` (PvP on, no claiming, no build except placing/breaking within event areas) defined by radius from spawn (safezone 100, warzone 100–400; config).
- [ ] Entering/leaving shows an action-bar message; `combat.yml` zone rules (E9) read from the same region service.

**Story Points**: 2 · **Priority**: Must Have · **Dependencies**: E0-S3

##### E8-S2: KOTH

**Story**: As a fighter, I want a scheduled King of the Hill, so that there's a fight worth showing up for.

**Acceptance Criteria**:

- [ ] Schedule in `events.yml` (default every 6h, 15-min warning broadcast). Capture zone is a WorldGuard region in the warzone.
- [ ] A player standing alone in the zone accrues capture time; leaving or another faction's player entering resets it. Capture requires 15 continuous minutes (config).
- [ ] Winner gets a KOTH Lootbag (GUI opened by right-click) with a weighted table: Ultimate book 40%, Legendary book 25%, Magic Dust Elite+ 20%, White Scroll 10%, Iron Golem spawner 5%. Winner and their faction get rank XP; all participants who dealt damage in the zone get participation XP.
- [ ] Winner is combat-tagged 15s after capture; scoreboard shows capture timer and current holder.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E8-S1, E3-S2, E6-S1

##### E8-S3: Envoys

**Story**: As a player, I want envoy crates to drop in the warzone, so that there's a reason to leave base.

**Acceptance Criteria**:

- [ ] Every 2h (config), 10 crates (Common ×6, Rare ×3, Legendary ×1) land at random warzone coordinates on solid ground; a 5-min countdown is broadcast; holograms mark tiers; a falling-block animation on spawn.
- [ ] First player to right-click a crate gets its loot table roll; crates despawn after 10 min if unclaimed.
- [ ] Loot tables in `events.yml`; all loot is registry-tagged.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E8-S1

---

#### Epic 9: Combat

##### E9-S1: Combat tag and logger NPC

**Story**: As a fighter, I want combat logging to be punished, so that fights end fairly.

**Acceptance Criteria**:

- [ ] Damaging or being damaged by another player applies a 15s tag (refreshes on hit); action bar shows the countdown.
- [ ] While tagged: `/f home`, `/spawn`, `/mine`, `/kit`, `/shop`, `/enchanter`, `/tinkerer`, `/alchemist`, `/trade`, ender chests, and `/f fly` are blocked; entering a safezone is blocked by a soft barrier (velocity push-back).
- [ ] Logging out while tagged spawns a Citizens-free NPC (a `Zombie`/`Player` entity via Paper's `PlayerProfile`) with the player's skin, health, armor, and inventory for 30s. Killing it drops the inventory and counts as a PvP kill on the logged-out player (power loss, contract progress). If it survives, the player's state is untouched.
- [ ] Tag ends on death, on timeout, or when both parties are in safezones.

**Story Points**: 8 · **Priority**: Must Have · **Dependencies**: E8-S1, E1-S3

##### E9-S2: Gear gating and combat rules

**Story**: As the owner, I want modern-version gear that breaks the 1.8 meta disabled, so that fights feel right.

**Acceptance Criteria**:

- [ ] Crafting, shop, and pickup of shields, elytra, tridents, crossbows, netherite items, totems, and end crystals are blocked; existing ones are deleted on inventory open (with a message).
- [ ] Golden apples: OCM old values; god apples usable with a 60s cooldown (config); enderpearls 16s cooldown; splash potions unchanged.
- [ ] World height locked 0–256 in the main world (world generator settings) and bedrock at y=0.
- [ ] Death drops full inventory outside safezones (classic); a `death_log` row records killer, victim, location, and item IDs dropped.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E0-S3

---

#### Epic 10: Anti-abuse and Operations

##### E10-S1: Anti-cheat and moderation tooling

**Acceptance Criteria**:

- [ ] GrimAC configured with alerts to staff channel; violations logged; auto-kick thresholds for fly/speed, manual review for combat.
- [ ] `/staff` mode (vanish, inspect inventory, freeze), `/ban|/mute|/warn` via LiteBans or LuckPerms + own table; all actions logged to `admin_actions`.
- [ ] Discord webhooks: player reports, dupe alerts, GrimAC alerts, F-Top weekly.

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S5

##### E10-S2: Backups and restore

**Acceptance Criteria**:

- [ ] Nightly `mariadb-dump` + world tarball to object storage (S3/R2) with 14-day retention; hourly incremental DB dumps kept 48h.
- [ ] A documented restore drill performed once before beta; `RESTORE.md` records the steps and time taken.
- [ ] Rollback tooling: `/ascent rollback item <uuid>` (remove all instances), `/ascent rollback player <name> <timestamp>` (restore inventory snapshot from `inventory_snapshots` taken every 30 min).

**Story Points**: 5 · **Priority**: Must Have · **Dependencies**: E1-S2

---

#### Epic 11: Presentation

##### E11-S1: Scoreboard and tab

**Acceptance Criteria**:

- [ ] Sidebar: rank + progress bar, balance, faction + F-Top position, combat tag / event timer line when active, online count. Updates ≤ 1/s, no flicker (use Paper's scoreboard API with per-player boards).
- [ ] Tab list: header/footer with server name, TPS/ping; rank prefix from PlaceholderAPI.

**Story Points**: 3 · **Priority**: Should Have · **Dependencies**: E2-S1, E6-S4

##### E11-S2: Chat format and join messages

**Acceptance Criteria**:

- [ ] `[Rank] [Faction] Name: message` with relation coloring; rank-up and first-join broadcasts; `[item]` in chat shows the held item on hover.

**Story Points**: 2 · **Priority**: Should Have · **Dependencies**: E6-S3

##### E11-S3: Lore renderer

**Acceptance Criteria**:

- [ ] Single `LoreRenderer` builds all custom item lore: enchants sorted by tier then name, one line each `<tier_color>Name Level`, then `<white>PROTECTED` if scrolled, then item-kind footer. Enchant count `[n]` appended to the display name when ≥ 1 enchant.
- [ ] Renderer is idempotent (re-rendering never duplicates lines) and is the only code that writes lore on tagged items.

**Story Points**: 3 · **Priority**: Must Have · **Dependencies**: E3-S3

##### E11-S4: Spawn hub

**Acceptance Criteria**:

- [ ] Spawn build with NPCs (Enchanter, Tinkerer, Alchemist, Shop, Kits) that open the GUIs on click; a "How it works" NPC opens a book explaining the three layers; `/spawn` with 3s warmup; `/wild` teleports to a random unclaimed location 500–2,000 blocks out.

**Story Points**: 5 · **Priority**: Should Have · **Dependencies**: E3-S6, E3-S7, E3-S8, E5-S3

---

#### Epic 12: Player Trade

##### E12-S1: `/trade`

**Acceptance Criteria**:

- [ ] `/trade <player>` → request/accept; two-sided GUI with money field; both must confirm; items and money swap atomically; registry logs TRADED for tagged items. Blocked while combat-tagged or > 10 blocks apart.

**Story Points**: 5 · **Priority**: Should Have · **Dependencies**: E1-S4, E1-S5

### 4.3 User Interface Requirements

**Design Principles**: Every GUI is a chest inventory (Paper `Inventory` API) with consistent slot conventions — close button bottom-right, back button bottom-left, filler panes dark gray. Item lore is the primary information surface; keep the Cosmic "feel": bold tier-colored names, one fact per line, gray helper text.

**Key screens**: Enchanter, Tinkerer, Alchemist, Shop (4 categories), Kits, Contracts, `/f top`, `/f perms`, `/trade`, KOTH Lootbag.

**Navigation**: Spawn NPCs and slash commands open the same GUIs; no GUI requires more than two clicks to reach from spawn.

---

## 5. Non-Functional Requirements

### 5.1 Performance

- TPS ≥ 19.5 sustained with 200 online, 500 spawner blocks active, 2 events running; alert at < 18.
- Main-thread time per tick from Ascent code ≤ 5 ms at 200 players (measured with Spark).
- No synchronous DB or HTTP calls on the main thread — enforced by a startup check that wraps the pool with a thread assertion in dev mode.
- Player join data load ≤ 150 ms p95; F-Top recalculation ≤ 500 ms off-thread.
- Memory: server heap 12 GB; Ascent caches (Caffeine) bounded and evicted 10 min after quit.

### 5.2 Security

- Authentication: Mojang `online-mode=true`; no offline/cracked support.
- Authorization: LuckPerms groups (`default`, `helper`, `mod`, `admin`, `owner`); every command declares a permission node `ascent.<module>.<command>`.
- All admin actions logged with actor, target, args, timestamp.
- Input validation on every command argument (length, charset, numeric ranges); faction names and item names pass a profanity filter.
- Secrets only in `.env`; never committed. DB user has no DROP privilege in production.
- Item integrity: registry IDs are UUIDv7 generated server-side; any tagged item whose ID is not in `items` is deleted on interaction and logged.

### 5.3 Scalability

- Phase 1: one Paper instance. Architecture keeps all cross-server-sensitive state (leaderboards, economy, player profiles) in MariaDB/Redis so Phase 2 can add a Velocity proxy and more planets without data migration.
- Spawner yield, F-Top, and contracts are computed in batch jobs, not per-tick per-player loops.

### 5.4 Reliability and Availability

- Uptime target 99% during beta (planned daily restart at 05:00 with 5-min warning).
- RPO 1 hour (hourly DB dumps), RTO 2 hours (documented restore).
- Autosave every 60s; graceful shutdown flushes all dirty profiles before the world saves.

### 5.5 Usability

- All player-facing text in `messages.yml`; English only in Phase 1.
- Every failed action returns a message saying *why* (e.g., "You need Rank 15 to buy Elite books").
- Server time and daily resets in UTC; displayed countdowns are relative ("resets in 3h 12m").

### 5.6 Maintainability

- Google Java Format via Spotless; build fails on format violations.
- Unit tests (JUnit 5 + MockBukkit) for enchant effects, roll logic, power math, claim adjacency, upkeep; target 70% line coverage in `ascent-plugin` core packages.
- Every module exposes a service interface in `ascent-api`; modules never call each other's implementation classes.
- One `CHANGELOG.md`; version bump per sprint.

---

## 6. Technical Architecture

### 6.1 System Architecture Overview

**Architecture Pattern**: Modular monolith — a single Paper plugin (`Ascent`) composed of internal modules behind service interfaces, backed by MariaDB and Redis, with third-party plugins for cross-cutting concerns (permissions, anti-cheat, combat emulation, protocol compat).

**Justification**: One plugin avoids load-order and API-versioning problems between our own plugins, keeps a solo developer's mental model small, and still allows extraction into separate plugins later because modules only talk through `ascent-api`.

```
                     ┌───────────────────────────────┐
  1.8.9 / 1.21 ───▶  │  Paper 1.21.x                 │
  clients            │  ┌──────── Ascent plugin ────┐│
                     │  │ core  rank  enchants mines││
                     │  │ spawners factions contracts││
                     │  │ events combat ui  ops     ││
                     │  └─────────┬──────────────────┘│
                     │  LuckPerms Vault PAPI OCM Via  │
                     │  GrimAC Spark FAWE WorldGuard  │
                     └────────────┬───────────────────┘
                                  │ JDBC / Jedis
                     ┌────────────┴───────────────────┐
                     │  MariaDB 11        Redis 7     │
                     │  (Docker Compose, same host)   │
                     └────────────────────────────────┘
                                  │ nightly
                     ┌────────────┴───────────────────┐
                     │  Object storage (S3/R2) backups │
                     └────────────────────────────────┘
```

**Components**:

- `ascent-api`: interfaces (`RankService`, `EnchantService`, `EconomyService`, `ItemRegistry`, `FactionService`, `ClaimService`, `MineService`, `SpawnerService`, `ContractService`, `EventService`, `CombatService`, `UnlockService`, `SellMultiplierService`, `ProgressBus`) and custom Bukkit events.
- `ascent-plugin`: implementations, listeners, commands, GUIs, DB, migrations, config loaders.
- Third-party: LuckPerms (permissions), Vault (economy bridge), PlaceholderAPI (scoreboard/tab), OldCombatMechanics (1.8 combat), ViaVersion/Backwards/Rewind (client compat), GrimAC (anti-cheat), Spark (profiling), FastAsyncWorldEdit (mine resets), WorldGuard (spawn/warzone regions only).

### 6.2 Technology Stack

- **Server**: Paper 1.21.x (latest stable at sprint start; pin the exact build in `gradle.properties`).
- **Language/Build**: Java 21, Gradle 8 (Kotlin DSL), Shadow plugin for shading, Spotless.
- **Libraries**: HikariCP, Flyway, Jedis, Caffeine, Adventure/MiniMessage (provided), JUnit 5, MockBukkit, Mockito.
- **Database**: MariaDB 11 (InnoDB, utf8mb4). Redis 7 for leaderboards and short-lived caches.
- **Infrastructure**: Ubuntu 24.04 host, systemd unit for the server, Docker Compose for MariaDB/Redis, GitHub Actions CI (build + tests), rclone/`aws s3` for backups, UptimeRobot-style ping monitoring, Discord webhooks for alerts.
- **Dev tooling**: Claude Code in the repo; `dev.sh` rebuild loop; a second "beta" server instance on the same host for staging.

### 6.3 Database Design

**Conventions**: `utf8mb4`, InnoDB; UUIDs as `CHAR(36)`; money as `BIGINT` (whole dollars); timestamps `DATETIME(3)` UTC with `created_at`/`updated_at`; soft delete only where noted. All IDs generated server-side (UUIDv7 for items, auto-increment elsewhere).

```sql
-- V1__init.sql (abridged: types, keys, indexes; full DDL generated from this spec)
CREATE TABLE players (
  uuid            CHAR(36) PRIMARY KEY,
  name            VARCHAR(16) NOT NULL,
  rank            SMALLINT NOT NULL DEFAULT 1,
  xp              BIGINT NOT NULL DEFAULT 0,        -- progress toward next rank
  xp_total        BIGINT NOT NULL DEFAULT 0,
  xp_banked       BIGINT NOT NULL DEFAULT 0,        -- post-100, for prestige
  balance         BIGINT NOT NULL DEFAULT 0,
  power           DECIMAL(6,2) NOT NULL DEFAULT 0,
  power_updated   DATETIME(3) NOT NULL,
  faction_id      INT NULL,
  first_seen      DATETIME(3) NOT NULL,
  last_seen       DATETIME(3) NOT NULL,
  play_seconds    BIGINT NOT NULL DEFAULT 0,
  INDEX idx_players_name (name),
  INDEX idx_players_faction (faction_id),
  INDEX idx_players_rank (rank)
);
CREATE TABLE xp_log (            -- rank XP events (aggregated per minute per source)
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid CHAR(36) NOT NULL, source VARCHAR(32) NOT NULL, amount INT NOT NULL,
  minute DATETIME NOT NULL,
  UNIQUE KEY uq_xp (player_uuid, source, minute)
);
CREATE TABLE money_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid CHAR(36) NULL, faction_id INT NULL,
  amount BIGINT NOT NULL, balance_after BIGINT NOT NULL,
  reason ENUM('PAY','SELL','SHOP_BUY','SHOP_SELL','KIT','CONTRACT','EVENT','UPKEEP',
              'VAULT_DEPOSIT','VAULT_WITHDRAW','ADMIN','TRADE','FACTION_CREATE') NOT NULL,
  ref VARCHAR(64) NULL, created_at DATETIME(3) NOT NULL,
  INDEX idx_mt_player_time (player_uuid, created_at)
);
CREATE TABLE items (             -- registry of tagged items
  item_id CHAR(36) PRIMARY KEY,  -- UUIDv7
  kind ENUM('BOOK_UNOPENED','BOOK_OPENED','WHITE_SCROLL','MAGIC_DUST','SPAWNER','GEAR','KIT_ITEM','XP_BOTTLE','LOOTBAG') NOT NULL,
  data JSON NOT NULL,            -- enchant_id, level, success, destroy, tier, mob_type, percent...
  quantity_created INT NOT NULL DEFAULT 1,
  created_by CHAR(36) NULL, created_reason VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL, destroyed_at DATETIME(3) NULL,
  INDEX idx_items_kind_created (kind, created_at)
);
CREATE TABLE item_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id CHAR(36) NOT NULL,
  event ENUM('CREATED','APPLIED','CONSUMED','DESTROYED','TRADED','DROPPED','PICKED_UP','SOLD','ADMIN_GIVE','ROLLBACK') NOT NULL,
  actor_uuid CHAR(36) NULL, counterparty_uuid CHAR(36) NULL,
  context JSON NULL, created_at DATETIME(3) NOT NULL,
  INDEX idx_ie_item (item_id, created_at), INDEX idx_ie_actor (actor_uuid, created_at),
  FOREIGN KEY (item_id) REFERENCES items(item_id)
);
CREATE TABLE dupe_alerts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, item_id CHAR(36) NOT NULL,
  observed_count INT NOT NULL, holders JSON NOT NULL, created_at DATETIME(3) NOT NULL,
  resolved_at DATETIME(3) NULL, resolved_by CHAR(36) NULL, resolution VARCHAR(255) NULL
);
CREATE TABLE enchant_rolls (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid CHAR(36) NOT NULL, book_item_id CHAR(36) NOT NULL, target_item_id CHAR(36) NOT NULL,
  enchant_id VARCHAR(32) NOT NULL, level TINYINT NOT NULL,
  success TINYINT NOT NULL, destroy TINYINT NOT NULL,
  outcome ENUM('SUCCESS','DESTROYED','SCROLL_SAVED','FAILED') NOT NULL,
  created_at DATETIME(3) NOT NULL, INDEX idx_er_player (player_uuid, created_at)
);
CREATE TABLE factions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(16) NOT NULL UNIQUE, leader_uuid CHAR(36) NOT NULL,
  vault_balance BIGINT NOT NULL DEFAULT 0, home_world VARCHAR(32) NULL,
  home_x DOUBLE NULL, home_y DOUBLE NULL, home_z DOUBLE NULL, home_yaw FLOAT NULL,
  upkeep_unpaid_days TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL, disbanded_at DATETIME(3) NULL
);
CREATE TABLE faction_members (
  faction_id INT NOT NULL, player_uuid CHAR(36) NOT NULL,
  role ENUM('LEADER','OFFICER','MEMBER','RECRUIT') NOT NULL, joined_at DATETIME(3) NOT NULL,
  PRIMARY KEY (faction_id, player_uuid), UNIQUE KEY uq_member (player_uuid),
  FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
);
CREATE TABLE faction_relations (
  faction_a INT NOT NULL, faction_b INT NOT NULL, relation ENUM('ALLY','ENEMY') NOT NULL,
  created_at DATETIME(3) NOT NULL, PRIMARY KEY (faction_a, faction_b)
);
CREATE TABLE faction_perms (
  faction_id INT NOT NULL, role ENUM('OFFICER','MEMBER','RECRUIT') NOT NULL,
  perm VARCHAR(24) NOT NULL, allowed BOOLEAN NOT NULL, PRIMARY KEY (faction_id, role, perm)
);
CREATE TABLE claims (
  world VARCHAR(32) NOT NULL, chunk_x INT NOT NULL, chunk_z INT NOT NULL,
  faction_id INT NOT NULL, is_core BOOLEAN NOT NULL DEFAULT FALSE, claimed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (world, chunk_x, chunk_z), INDEX idx_claims_faction (faction_id),
  FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
);
CREATE TABLE placed_spawners (
  world VARCHAR(32) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
  faction_id INT NOT NULL, mob_type VARCHAR(24) NOT NULL, count SMALLINT NOT NULL,
  item_id CHAR(36) NOT NULL, placed_by CHAR(36) NOT NULL, placed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (world, x, y, z), INDEX idx_ps_faction (faction_id)
);
CREATE TABLE mine_plots (
  plot_index INT PRIMARY KEY, owner_uuid CHAR(36) NULL UNIQUE, tier TINYINT NULL,
  allocated_at DATETIME(3) NULL, last_used DATETIME(3) NULL, mined_blocks INT NOT NULL DEFAULT 0
);
CREATE TABLE contracts_active (
  player_uuid CHAR(36) NOT NULL, slot TINYINT NOT NULL, contract_id VARCHAR(32) NOT NULL,
  progress INT NOT NULL DEFAULT 0, target INT NOT NULL, completed_at DATETIME(3) NULL,
  assigned_date DATE NOT NULL, PRIMARY KEY (player_uuid, slot)
);
CREATE TABLE contracts_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, player_uuid CHAR(36) NOT NULL,
  contract_id VARCHAR(32) NOT NULL, completed_at DATETIME(3) NOT NULL,
  INDEX idx_ch_player (player_uuid, completed_at)
);
CREATE TABLE kit_cooldowns (
  player_uuid CHAR(36) NOT NULL, kit_id VARCHAR(32) NOT NULL, next_at DATETIME(3) NOT NULL,
  PRIMARY KEY (player_uuid, kit_id)
);
CREATE TABLE event_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type ENUM('KOTH','ENVOY') NOT NULL,
  started_at DATETIME(3) NOT NULL, ended_at DATETIME(3) NULL,
  winner_uuid CHAR(36) NULL, winner_faction_id INT NULL, details JSON NULL
);
CREATE TABLE ftop_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, taken_at DATETIME(3) NOT NULL,
  faction_id INT NOT NULL, value BIGINT NOT NULL, position SMALLINT NOT NULL,
  INDEX idx_ftop_time (taken_at, position)
);
CREATE TABLE ftop_payouts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, week_start DATE NOT NULL, faction_id INT NOT NULL,
  position SMALLINT NOT NULL, value BIGINT NOT NULL, fulfilled_at DATETIME(3) NULL
);
CREATE TABLE death_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, victim_uuid CHAR(36) NOT NULL, killer_uuid CHAR(36) NULL,
  world VARCHAR(32) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
  dropped_item_ids JSON NULL, created_at DATETIME(3) NOT NULL,
  INDEX idx_dl_victim (victim_uuid, created_at)
);
CREATE TABLE inventory_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, player_uuid CHAR(36) NOT NULL,
  taken_at DATETIME(3) NOT NULL, inventory BLOB NOT NULL, enderchest BLOB NOT NULL,
  INDEX idx_is_player (player_uuid, taken_at)
);
CREATE TABLE admin_actions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_uuid CHAR(36) NOT NULL, action VARCHAR(64) NOT NULL,
  target VARCHAR(64) NULL, args JSON NULL, created_at DATETIME(3) NOT NULL
);
CREATE TABLE sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, player_uuid CHAR(36) NOT NULL,
  joined_at DATETIME(3) NOT NULL, quit_at DATETIME(3) NULL, INDEX idx_s_player (player_uuid)
);
```

**Relationships**: `players.faction_id` → `factions.id` (SET NULL on disband); `faction_members`, `claims`, `placed_spawners`, `faction_perms` cascade on faction delete (disband soft-deletes `factions.disbanded_at` first and cascades explicitly in code so history survives). `item_events.item_id` → `items.item_id`.

**Retention**: `xp_log`, `item_events`, `money_transactions`, `inventory_snapshots` pruned to 90 days by a nightly job; `items` kept for the season.

### 6.4 Interfaces: Commands, Services, Events, Config

There is no HTTP API in Phase 1. The "API" is three things: player commands, the `ascent-api` service interfaces, and custom Bukkit events. Claude Code should implement against these signatures.

#### 6.4.1 Command reference

| Command | Permission | Description |
|---|---|---|
| `/rank`, `/rank top`, `/rank unlocks` | `ascent.rank.use` | Progress, leaderboard, unlock list |
| `/kit [name]` | `ascent.kit.use` | Kit GUI / claim kit |
| `/enchanter`, `/tinkerer`, `/alchemist` | `ascent.enchants.use` | Station GUIs |
| `/mine` | `ascent.mines.use` | Teleport to own mine plot |
| `/sell` | `ascent.economy.sell` | Sell inventory |
| `/shop` | `ascent.economy.shop` | Shop GUI |
| `/bal [player]`, `/pay <player> <amount>`, `/baltop` | `ascent.economy.use` | Economy |
| `/f <create|invite|join|leave|kick|disband|promote|demote|leader|info|list|who|claim|unclaim|unclaimall|map|sethome|home|vault|perms|chat|ally|enemy|neutral|fly|top>` | `ascent.factions.use` (+ role checks) | Factions |
| `/contracts` | `ascent.contracts.use` | Daily contracts GUI |
| `/koth`, `/envoy` | `ascent.events.use` | Event status / next time |
| `/trade <player>` | `ascent.trade.use` | Player trade |
| `/spawn`, `/wild` | `ascent.tp.use` | Teleports |
| `/ascent <give|rank|reload|debug|item lookup|rollback>` | `ascent.admin` | Admin |
| `/staff`, `/ban`, `/mute`, `/warn` | `ascent.staff` | Moderation |

Every command: async-safe argument parsing, tab completion, and a `messages.yml` key for every failure path.

#### 6.4.2 Service interfaces (`ascent-api`)

```java
public interface RankService {
  int getRank(UUID player);
  long getXp(UUID player);                       // progress toward next rank
  long xpToNext(int rank);                       // round(400 * rank^1.4)
  void addXp(UUID player, XpSource source, long amount);   // fires RankUpEvent on threshold
}
public interface UnlockService {
  int enchantSlotCap(int rank);                  // 6 + rank/10, max 16
  Tier maxBookTier(int rank);
  int mineTier(int rank);
  List<String> availableKits(int rank);
}
public interface EnchantService {
  ItemStack createUnopenedBook(Tier tier, UUID creator, String reason);
  ItemStack reveal(ItemStack unopened, UUID player);       // rolls enchant/level/success/destroy
  ApplyResult apply(ItemStack book, ItemStack target, Player player); // SUCCESS | DESTROYED | SCROLL_SAVED | FAILED | INCOMPATIBLE
  Map<String,Integer> getEnchants(ItemStack item);
  Optional<EnchantDefinition> definition(String enchantId);
}
public interface EconomyService {
  long getBalance(UUID player);
  boolean withdraw(UUID player, long amount, TxReason reason, String ref);
  void deposit(UUID player, long amount, TxReason reason, String ref);
  boolean transfer(UUID from, UUID to, long amount, TxReason reason);
}
public interface ItemRegistry {
  ItemStack tag(ItemStack item, ItemKind kind, Map<String,Object> data, UUID creator, String reason);
  Optional<UUID> idOf(ItemStack item);
  void record(UUID itemId, ItemEvent event, UUID actor, @Nullable UUID counterparty, @Nullable Map<String,Object> ctx);
}
public interface FactionService {
  Optional<Faction> byPlayer(UUID player);
  Optional<Faction> byName(String name);
  Relation relation(UUID a, UUID b);             // OWN | ALLY | ENEMY | NEUTRAL
  boolean hasPerm(UUID player, FactionPerm perm);
}
public interface ClaimService {
  Optional<Integer> ownerOf(World world, int chunkX, int chunkZ);
  ClaimResult claim(Faction f, Chunk c, UUID by);   // OK | NOT_ADJACENT | NO_POWER | PROTECTED_ZONE | OVERCLAIM_OK
  boolean canBuild(UUID player, Location at);
  int allowedClaims(Faction f);                  // floor(power / 10)
}
public interface SpawnerService {
  void place(Block block, String mobType, int count, Faction f, UUID by, UUID itemId);
  void tickYield();                              // scheduled every second
  long factionSpawnerValue(int factionId);
}
public interface ProgressBus {                   // contracts and future quests listen here
  void publish(UUID player, ObjectiveType type, int amount, @Nullable String qualifier);
}
public interface CombatService {
  boolean isTagged(UUID player);
  void tag(UUID a, UUID b);
  boolean isSafezone(Location l);
  boolean isWarzone(Location l);
}
```

#### 6.4.3 Custom events (Bukkit)

`RankUpEvent(player, oldRank, newRank)` · `BookRevealedEvent` · `EnchantApplyEvent(player, book, target, result)` (cancellable before roll) · `FactionCreateEvent` · `ClaimEvent` / `UnclaimEvent` · `SpawnerPlaceEvent` / `SpawnerYieldKillEvent` · `ContractCompleteEvent` · `KothCaptureEvent` · `EnvoyClaimEvent` · `CombatTagEvent` · `ItemRegisteredEvent`.

#### 6.4.4 Config schema examples

```yaml
# enchants.yml (excerpt)
tiers:
  SIMPLE:    { color: "<white>",  xp_levels: 10, success: [60,100], destroy: [0,20],  min_rank: 1 }
  UNIQUE:    { color: "<green>",  xp_levels: 20, success: [50,95],  destroy: [5,35],  min_rank: 5 }
  ELITE:     { color: "<aqua>",   xp_levels: 35, success: [40,90],  destroy: [10,50], min_rank: 15 }
  ULTIMATE:  { color: "<yellow>", xp_levels: 55, success: [30,80],  destroy: [20,70], min_rank: 25 }
  LEGENDARY: { color: "<gold>",   xp_levels: 90, success: [20,70],  destroy: [30,90], min_rank: 40 }
enchants:
  lifesteal:
    display: "Lifesteal"
    tier: LEGENDARY
    applies_to: [SWORD]
    max_level: 5
    description:
      1: "Heals <heal> hearts on hit (<chance>% chance)"
    effect:
      type: LIFESTEAL
      per_level: { heal: [0.5, 1.0, 1.5, 2.0, 2.5], chance: [6, 9, 12, 15, 18] }
    cooldown_ticks: 20
```

```yaml
# ranks.yml (excerpt)
xp_curve: { base: 400, exponent: 1.4, max_rank: 100 }
sources:
  MINE_BLOCK: { tier1: 2, tier2: 5, tier3: 12 }
  SPAWNER_KILL: { ZOMBIE: 8, SKELETON: 8, COW: 6, CREEPER: 15, BLAZE: 15, ENDERMAN: 15, IRON_GOLEM: 40 }
  PVP_KILL: { base: 500, repeat_victim_window_min: 10, repeat_multiplier: 0.1 }
  KOTH_WIN: 2000
  ENVOY_CRATE: 300
```

### 6.5 Authentication and Authorization

- Mojang authentication via `online-mode=true`.
- LuckPerms groups: `default` → `ascent.*.use`; `helper` adds `ascent.staff.vanish|inspect`; `mod` adds `ascent.staff.*`; `admin` adds `ascent.admin`; `owner` adds `*`.
- Faction-level authorization is role-based (`faction_perms`) and checked through `FactionService.hasPerm` — never by comparing role enums inline.

### 6.6 External Integrations

- **Discord webhooks** (outgoing only): dupe alerts, GrimAC alerts, F-Top weekly, restart notices. Failure is logged and ignored; never blocks gameplay.
- **Object storage (S3/R2)**: backup uploads via `rclone` cron; credentials in host env, not in the plugin.
- **PaperMC Fill v3 API**: only used by `update-paper.sh` to fetch pinned builds.
- No webstore in Phase 1. When added (Phase 2), it will call an HTTP endpoint on a small sidecar service, not the plugin directly.

### 6.7 Deployment Architecture

- **Environments**: `dev` (owner's box or the same host on port 25566, MariaDB db `ascent_dev`), `beta` (same host, port 25567, db `ascent_beta`, real players, wiped per test), `prod` (port 25565, db `ascent`). Each has its own `.env`.
- **Infrastructure**: one dedicated host, 8 vCPU / 32 GB / NVMe; Paper heap 12 GB prod, 4 GB beta; MariaDB 4 GB buffer pool; Redis 512 MB; UFW allows 25565–25567 and SSH only; DDoS protection from the host provider.
- **Deploy process**: `git tag` → GitHub Actions builds jar → `deploy.sh <env>` copies jar to `plugins/`, warns players 5 min, restarts via systemd, tails log until "Done", runs `/ascent debug db` smoke check.
- **Rollback**: previous jar kept as `Ascent-<prev>.jar.bak`; `deploy.sh rollback <env>` swaps and restarts; DB migrations are forward-only, so any migration must be backward-compatible with the previous jar for one release.
- **Monitoring/alerts**: TPS < 18 for 60s → Discord; MariaDB unreachable → Discord + plugin disables writes; disk > 85% → Discord; nightly backup missing → Discord.

### 6.8 Performance Optimization

- **Caching**: `PlayerProfile` in memory while online (+10 min); claim ownership in a `Long2ObjectMap` keyed by chunk key, invalidated on claim change; enchant definitions immutable after load; F-Top and baltop in Redis sorted sets, refreshed by jobs.
- **DB**: HikariCP pool; batched inserts for `xp_log`, `item_events`, `money_transactions` (flush every 5s or 500 rows); covering indexes as in §6.3.
- **Ticks**: spawner yield on a 1s repeating task over only "active" spawners (player within 5 chunks); enchant passive tick on a 1s task; scoreboard updates batched per second; no per-move listeners without a distance threshold.

---

## 7. Data Flow and Business Logic

### 7.1 Key Workflows

#### Workflow 1: Book apply roll

**Trigger**: `InventoryClickEvent` where cursor is an opened book and clicked item is gear.

**Steps**:

1. Read book PDC (`enchant_id`, `level`, `success`, `destroy`, `item_id`) and target PDC (`enchants` map, `white_scroll`, `item_id`).
2. Compatibility checks (type, slot cap via `UnlockService`, upgrade-only rule). On failure: cancel event, message, return.
3. Fire `EnchantApplyEvent` (cancellable).
4. Roll: `r1 = random(0,100)`. If `r1 < success` → set enchant, outcome SUCCESS. Else `r2 = random(0,100)`; if `r2 < destroy` → if `white_scroll` then clear flag, outcome SCROLL_SAVED else remove item, outcome DESTROYED. Else outcome FAILED.
5. Remove book from cursor. If target survived, re-render lore via `LoreRenderer`.
6. Write `enchant_rolls`; `ItemRegistry.record` for book (CONSUMED) and target (APPLIED/DESTROYED); play sound + message; `ProgressBus.publish(APPLY_BOOKS, 1)`.

**Business Rules**: one roll only; success and destroy are independent; the book is always consumed; lower/equal level is rejected before any roll.

**Error Handling**: any exception after step 4 must not lose the item — wrap in try/finally that restores the pre-roll target stack if the write failed; log at ERROR with both item IDs.

#### Workflow 2: Spawner yield tick (every second)

1. Iterate active spawner blocks (those with a player within 5 chunks, maintained by a chunk-load/player-move index).
2. `pending += rate × count`, capped at `count × 60`.
3. Ensure the representative entity exists and its name shows `pending`.
4. On melee hit (listener): decrement `pending`, roll loot table, spawn one merged item stack and one XP orb sized to the batch, publish `SPAWNER_KILLS`, add rank XP.

#### Workflow 3: Claim upkeep (daily 00:00 UTC)

1. For each faction: `due = claims × 500`. If `vault_balance ≥ due` → withdraw, `upkeep_unpaid_days = 0`. Else `upkeep_unpaid_days += 1`, notify members.
2. If `upkeep_unpaid_days ≥ 3` → release all claims, log, notify, reset counter.

#### Workflow 4: Daily contract assignment

1. On first login of the UTC day (or at 00:00 for online players): archive yesterday's `contracts_active` into history (completed or not), draw three per E7-S1 rules, insert.

#### Workflow 5: Player join / quit

1. `AsyncPlayerPreLoginEvent`: load `players` row (or create), faction, contracts, kit cooldowns into `PlayerProfile`. Failure → kick "Data could not be loaded, try again."
2. `PlayerJoinEvent`: apply scoreboard, tab, permissions, `sessions` insert, welcome/rank-up-pending messages.
3. `PlayerQuitEvent`: if combat-tagged → spawn logger NPC; save profile; close session; schedule cache eviction in 10 min.

#### Workflow 6: F-Top recalculation (every 5 min)

1. Off-thread: `SELECT faction_id, SUM(count * value)` over `placed_spawners` joined to config values; add `vault_balance × 0.1`.
2. Write `ftop_snapshots` (positions), update Redis sorted set, fire scoreboard refresh.

### 7.2 State Management

- **Server-side authoritative**: all progression, balances, claims, and item identity live in the DB and are cached in memory only through the services above.
- **In-memory only**: combat tags, spawner `pending`, KOTH capture timers, GUI sessions — rebuilt safely on restart (pending resets to 0; a restart during KOTH cancels it with a broadcast).
- **Client**: nothing is trusted from the client beyond vanilla packets; anti-cheat sits in front.

### 7.3 Business Rules and Validation

- Faction name `^[A-Za-z0-9]{3,16}$`, not in profanity list, unique case-insensitive.
- Money amounts: integers, `1 ≤ amount ≤ 9_007_199_254_740_991`; balances never negative; upkeep can't be paid from personal balances automatically.
- Rank XP amounts per event are capped at `10 × source_default` to contain bugs; anything above logs a WARN and is clamped.
- Claims must be outside `spawn` and `warzone` regions and ≥ 200 blocks from spawn.
- Spawner placement only in own claims; max stack 40; only `SPAWNERS` perm holders can place/mine.
- Safezone: no PvP, no enchant procs, no item drops on death.
- All player-facing timers use server UTC; contract day boundary is 00:00 UTC.

---

## 8. Testing Strategy

### 8.1 Approach

- **Unit (MockBukkit + JUnit 5)**: roll logic (10,000-iteration distribution tests: SUCCESS rate within ±2% of `success`; DESTROYED rate ≈ (1−success)×destroy), XP curve, slot cap, power regen/loss, claim adjacency and overclaim, upkeep state machine, Tinkerer exchange math, contract drawing rules.
- **Integration (dev server)**: each story's acceptance criteria run manually by the owner with two test accounts (one 1.8.9, one 1.21).
- **Load**: a bot swarm (e.g., a headless client tool) with 100 bots idling in claims + 20 real testers grinding 500 spawner blocks; pass if TPS ≥ 19.5 for 30 min.
- **Exploit/dupe pass** (before every beta and every release): a written checklist — inventory-close during roll, stack-split during trade, quit during `/trade`, chest-open in unloaded chunk, shop double-click, kit spam, `/sell` with cursor item, death during `/f home` warmup, logger NPC + relog race. Each has a test and a pass/fail record in `EXPLOITS.md`.
- **Closed beta**: 30–50 players, two weeks, dupe bounty (store credit), daily `dupe_alerts` review.

### 8.2 Test Cases (samples)

- **Given** an opened Legendary book 20/90 **When** applied 10,000 times in test **Then** SUCCESS ≈ 20%, DESTROYED ≈ 72%, FAILED ≈ 8% (±2%).
- **Given** a scrolled item and a failed destroy roll **When** the roll resolves **Then** the item survives, `white_scroll` is cleared, outcome = SCROLL_SAVED.
- **Given** a faction with power 42 **When** it holds 5 claims **Then** `/f claim` fails with NO_POWER and an enemy can overclaim a non-core chunk.
- **Given** a player combat-tagged **When** they run `/f home` **Then** it is blocked with the tag message and the tag is unchanged.
- **Given** 40 stacked Iron Golem spawners and a player 3 chunks away **When** 10 s pass **Then** `pending` = 80 and TPS unaffected.

### 8.3 Quality Assurance

- CI runs build + unit tests on every push; merge to `main` only from green branches.
- Owner reviews every Claude Code diff before merge (read the listener registrations and the DB writes at minimum).
- Manual test evidence (screenshot or log line) attached to each story before it's closed.
- Spark profile captured at end of every sprint on the beta server.

---

## 9. Definition of Done

A story is done when:

**Code**

- [ ] Builds clean with Spotless; no NMS; no main-thread DB/HTTP.
- [ ] Service interface in `ascent-api` updated if the public surface changed.
- [ ] All new strings in `messages.yml`; all new numbers in the module's YAML.

**Testing**

- [ ] Unit tests for logic; ≥ 70% coverage on the touched package.
- [ ] Every acceptance criterion verified on the dev server with a 1.8.9 and a 1.21 client (where relevant).
- [ ] Relevant `EXPLOITS.md` items re-run if the story touches inventories, money, or items.

**Docs**

- [ ] `CHANGELOG.md` entry; `TESTING.md` updated with any new admin command.

**Deploy**

- [ ] Merged to `main`; deployed to `beta`; smoke test (`/ascent debug db`, join with both clients) passed.

**Acceptance**

- [ ] Owner has played the feature for 15 minutes and approved it.
- [ ] No open critical bug against the story.

---

## 10. Sprint Planning and Timeline

### 10.1 Approach

**Methodology**: SCRUM, 2-week sprints. **Team**: owner (PO + tester) + Claude Code. **Velocity assumption**: ~35–40 points/sprint at 15–20 focused hours/week. Epic 0 precedes Sprint 1.

### 10.2 Release Plan

| Sprint | Goal | Stories | Points |
|---|---|---|---|
| 0 (1 week) | Environment | E0-S1, E0-S2, E0-S3, E0-S4 | 10 |
| 1 | Platform | E1-S1, E1-S2, E1-S3, E1-S4, E1-S6, E11-S3 | 24 |
| 2 | Progress + registry | E1-S5, E2-S1, E2-S2, E2-S3, E4-S1, E4-S2 | 30 |
| 3 | The lottery | E3-S1, E3-S2, E3-S3, E3-S5, E3-S6 | 27 |
| 4 | Effects + stations | E3-S4, E3-S7, E3-S8, E8-S1, E9-S2 | 23 |
| 5 | Factions | E6-S1, E6-S2a, E6-S2b, E6-S2c, E6-S3 | 26 |
| 6 | Value + combat | E5-S1, E5-S2, E5-S3, E6-S4, E9-S1 | 31 |
| 7 | Loop closers | E7-S1, E8-S2, E8-S3, E11-S1, E11-S2 | 26 |
| 8 | Ops + polish | E10-S1, E10-S2, E11-S4, E12-S1, exploit pass, load test | 20 + QA |
| 9–10 | Closed beta | Bug fixes, balance tuning from data, second exploit pass | — |

**Deliverable**: Public Season 1 launch candidate at the end of Sprint 10 (~5 months from Epic 0 including beta).

### 10.3 Milestones

| Milestone | Target | Description |
|---|---|---|
| M1 "It runs" | End Sprint 1 | Server + plugin + DB + economy on dev |
| M2 "It progresses" | End Sprint 2 | Rank, mines, kits, item registry live |
| M3 "It gambles" | End Sprint 4 | Full enchant loop with stations and effects |
| M4 "It's a faction server" | End Sprint 6 | Claims, spawners, F-Top, combat tag |
| M5 "It's a game" | End Sprint 7 | Contracts, KOTH, Envoys, scoreboard |
| M6 Beta | End Sprint 8 | Ops, backups, exploit pass; invite testers |
| M7 Season 1 | End Sprint 10 | Public launch |

### 10.4 Dependencies and Risks

- **Paper API changes** between minor versions — pin the build; upgrade only at sprint boundaries.
- **OCM feel not matching expectations** (high impact) — validate in Sprint 4 with 1.8 veterans; fallback is tuning OCM modules, not switching server versions.
- **Spawner virtual-yield perceived as "fake"** (medium) — the representative entity with a live counter and vanilla XP orbs preserves the grind feel; test in Sprint 6.
- **Dupes** (high) — registry from Sprint 2, exploit checklist from Sprint 8, bounty in beta.
- **Solo burnout** (high) — sprints are sized at ~15–20 h/week; cut Should-Haves before slipping Must-Haves.
- **Scope creep from Phases 2–4** — everything not in §4.1 goes to the §12 backlog, no exceptions until Season 1 ships.

---

## 11. Maintenance and Support

- **Monitoring**: Spark TPS (< 18 alert), MariaDB availability, disk, backup freshness, GrimAC alert volume, dupe_alerts count (any > 0 pages the owner).
- **Logging**: SLF4J via Paper logger; levels DEBUG (dev only), INFO (lifecycle, jobs), WARN (clamped values, config oddities), ERROR (item-loss risk, DB failures). Logs rotated daily, kept 30 days.
- **Support**: Discord `#support` + in-game `/report`; issues tracked in GitHub Issues with labels `bug`, `exploit`, `balance`, `feature`; exploit reports triaged within 24h, P1 (item/money loss or dupe) hotfixed same day.

---

## 12. Future Enhancements (Backlog Epics)

**Phase 2 — Stakes**: Sieges (declare/schedule/defense hours/siege claim/60-min window/Siege Flag/25% take/snapshot regen/limits) · Faction Level + perks + faction quests · New-faction shield · Outposts · World bosses · Dungeons (2 tiers) · Weekly contract chain · F-Top faction points + store-credit payouts · Bounty board · Auction House · Velocity proxy + second planet · Webstore sidecar.

**Phase 3 — Depth**: Prestige (+5%/prestige, cap 50%) · Gear XP · Black/Randomization/Transmog scrolls + Orbs · Soul tier + Souls + Soul Gems · Faction mine · Arena/duels · Mine tiers 4–6, dungeon tiers 3–4 · Concentric-ring map zoning · Points-based F-Top option.

**Phase 4 — Late-era layer**: Heroic and Mastery tiers · Armor sets and crystals · Masks · Trinkets · Pets · Seasonal events · Optional Bedrock crossplay via Geyser.

---

## 13. Appendices

### Appendix A: Glossary

| Term | Definition |
|---|---|
| Rank XP | Ascent's personal progression currency (not vanilla XP) |
| Vanilla XP | Minecraft XP levels; the Enchanter's currency |
| Unopened / opened book | Tier voucher / rolled enchant book with success% and destroy% |
| Slot cap | Max custom enchants on an item; rank-based |
| Registry ID | UUIDv7 in PDC `ascent:item_id` on every valuable item |
| Virtual yield | Spawner output computed by counter, one representative entity per stack |
| Core claim | First claim; faction home; last chunk to be lost |
| Upkeep | Daily per-chunk cost from the faction vault |
| Overclaim | Claiming another faction's chunk when it exceeds its power-allowed claims |
| Combat tag | 15s state blocking teleports/GUIs; logout spawns a logger NPC |
| ProgressBus | Internal event bus contracts subscribe to |

### Appendix B: References

1. `ascent-factions-concept.md` — game concept and three-layer model.
2. CosmicPvP Design Bible (this project) — reference mechanics, enchant catalog, apply logic, station behavior.
3. PaperMC docs — `https://docs.papermc.io`; Fill v3 download API.
4. OldCombatMechanics, ViaVersion, GrimAC, LuckPerms, FastAsyncWorldEdit, WorldGuard project pages for configuration references.

### Appendix C: Change Log

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2026-09-03 | PO + Claude | Initial Phase 1 PRD |
