# Testing Ascent

How to run the server, get a change in front of yourself, and read what went
wrong. Story E0-S4 owns this file; every later story that adds an admin command
adds it here too (PRD §9, Definition of Done).

## The loop

```bash
./dev.sh
```

That builds the plugin, copies the jar into the dev server, restarts the server
and waits until it reports `Done`. It prints the boot time; the target is under
30 seconds with all plugins loaded.

| Command | What it does |
|---|---|
| `./dev.sh` | Build, copy, restart, wait for `Done` |
| `./dev.sh --console` | Same, then attach to the server console |
| `./dev.sh --no-restart` | Build and copy only |
| `./gradlew build` | Build and run unit tests plus format checks |
| `./gradlew spotlessApply` | Fix formatting the build is complaining about |
| `./gradlew copyToServer` | Copy the current jar without restarting |

The server runs inside a tmux session named `ascent`.

```bash
tmux attach -t ascent    # attach to the console
# Ctrl-b then d          # detach, leaving the server running
tmux kill-session -t ascent   # hard stop, only if the console is wedged
```

Prefer typing `stop` in the console over killing the session: it flushes worlds
and player data.

## First-time setup

See `SETUP.md` for the full walkthrough. The short version, once Java 21,
Docker, tmux, curl and jq are installed:

```bash
cp .env.example .env      # passwords and ASCENT_SERVER_DIR
docker compose up -d      # MariaDB + Redis
scripts/bootstrap.sh      # Paper, EULA, server.properties, plugins
./dev.sh                  # build, deploy, start
```

Pull a newer Paper build with `scripts/update-paper.sh --check` to see what is
available, then without `--check` to install it and update the pin in
`gradle.properties`. Do this at sprint boundaries only (PRD §10.4).

## Reading the logs

```bash
tail -f ~/ascent-server/logs/latest.log             # follow
grep -i ascent ~/ascent-server/logs/latest.log      # just our plugin
grep -iE 'error|exception|caused by' ~/ascent-server/logs/latest.log
```

Older logs are rotated into `logs/<date>-<n>.log.gz`; read them with `zgrep`.

A stack trace naming `gg.ascent` is ours. A trace that names only Paper or
another plugin usually means a bad config or a version mismatch, not our code.

## Performance

```
/spark tps          # current TPS and MSPT
/spark profiler start --timeout 60
/spark profiler stop
/spark health
```

The PRD's target is 19.5 TPS sustained at 200 players, with an alert below 18.
Capture a Spark profile at the end of every sprint on the beta server (PRD §8.3).

## Checking the plugin is alive

```
/ascent version
```

It replies with the running version. If the command is unknown, the plugin
failed to load: check the log for `Could not load 'plugins/Ascent`.

## Admin commands

Every `/ascent` command needs the `ascent.admin` permission (ops have it;
give it to staff through LuckPerms) and writes an `admin_actions` row. Each row
below says what the command does and which story added it.

| Command | Purpose | Story |
|---|---|---|
| `/ascent version` | Print the running plugin version | E0-S4 |
| `/ascent reload` | Re-read every YAML file without a restart; reports any file that failed and kept its old values | E1-S1 |
| `/ascent give <player> money <amount>` | Add money; works for offline players. `k`, `m` and `b` suffixes are accepted (`2k`, `3m`); whole numbers only, so write `1500k` rather than `1.5m` | E1-S6 |
| `/ascent give <player> xp <amount>` | Add rank XP to an online player. Until E2-S1 this only moves the counters; no rank-up fires | E1-S6 |
| `/ascent give <player> books\|dust\|scrolls\|spawners` | Reserved; answers "not yet" until Epics 3 and 4 | E1-S6 |
| `/ascent rank set <player> <1-100>` | Set an online player's rank (XP toward next resets to 0) | E1-S6 |
| `/ascent rank add <player> <n>` | Move an online player's rank by `n`, negative allowed, clamped to 1..100 | E1-S6 |
| `/ascent debug tps` | TPS over 1/5/15 minutes and ms per tick | E1-S6 |
| `/ascent debug db` | Pool usage, queued and failed tasks, Redis state, players online and logging in, then a database round-trip time | E1-S6 |
| `/ascent debug items` | Items tagged and events logged since startup, what the last dupe scan saw, open alerts, whether Discord is wired | E1-S5 |
| `/ascent item lookup <item-id>` | An item's row and full event history. The id is in the item's persistent data; dupe alerts print it | E1-S5 |

Player commands you can use to check economy stories (permission
`ascent.economy.use`, granted to everyone by default):

| Command | Purpose | Story |
|---|---|---|
| `/bal [player]` | Your balance, or anyone's who has ever joined | E1-S4 |
| `/pay <player> <amount>` | Send money; the target may be offline | E1-S4 |
| `/baltop` | Top 10 balances, refreshed every 60 seconds | E1-S4 |
| `/rank` | Your rank, a progress bar, and the next unlock | E2-S1 |
| `/rank top` | Top 10 ranks, refreshed every 60 seconds | E2-S1 |
| `/rank unlocks` | Every threshold on the ladder with ✔/✘ for you | E2-S2 |
| `/kit` | The kit menu: click a kit to claim it; locked kits show their rank, claimed ones their cooldown | E2-S3 |
| `/kit <name>` | Claim a kit directly, for example `/kit starter` | E2-S3 |
| `/mine` | Teleport to your personal mine; builds it on first use, upgrades the tier when your rank allows | E4-S1 |
| `/sell` | Sell every sellable item in your inventory at `prices.yml` values, with a receipt | E4-S2 |

**Verified 2026-09-22** on Paper 1.21.11 build 132 by the owner: first join
created the row with $1,000; `/ascent give money`, `/ascent rank set`,
`/baltop` and `/ascent debug db` answered as documented; the balance survived
a rejoin and a full server restart. Closes E1-S2, E1-S3, E1-S4 and E1-S6.

**Verified 2026-09-22** by the owner: `/ascent debug items` and
`/ascent item lookup` answer as documented, and the Discord webhook from
`.env` reports as configured. Closes E1-S5.

## Rank ladder

Rank XP is separate from vanilla XP. Sources and the curve live in
`ranks.yml`; `xp_log` records every grant aggregated per minute and source.
The fastest way to see the ladder work is `/ascent give <you> xp 400`, which
is exactly rank 2 on the default curve: expect the chat line, a title and the
level-up sound. PvP kills grant XP today; mining, spawners, events and
contracts plug in as their epics land.

```sql
SELECT source, SUM(amount) FROM xp_log WHERE player_uuid = '<uuid>' GROUP BY source;
```

**Verified 2026-09-22** by the owner: `/rank`, `/rank unlocks`, `/rank top`
and a rank-up from `/ascent give xp` with chat line, title and sound. Closes
E2-S1 and E2-S2.

## Enchant catalog

Every custom enchant is data in `enchants.yml`: 41 MVP enchants across the five
tiers, each with the gear it fits, its max level, a lore line per level and an
effect type with per-level numbers. Numbers are ours to tune. Change one, save,
`/ascent reload`. A malformed entry is reported with the enchant id and the
field, keeps the previous catalog on reload, and stops the plugin from
enabling at boot: the catalog is not something to run half-loaded. Books that
use the catalog arrive with E3-S2; effects with E3-S4.

## Personal mines

`/mine` puts you in a dedicated void world called `mines`, in your own 64x64
plot: a bedrock slab with a 48x48 pit of ore 24 blocks deep and glass walls.
The pit refills every 10 minutes or once 70% is mined (`mines.yml`), and you
are moved to the plot spawn first. Nobody else can walk or teleport into your
plot, nothing spawns, nothing hurts you, and nothing can be placed. Each block
mined pays rank XP by tier (`ranks.yml`), and `/sell` turns the ore into money.

Plots are built with FastAsyncWorldEdit when it is installed and through the
Bukkit API a few thousand blocks per tick otherwise, so a first visit takes a
few seconds. Drop a `.schem` at `plugins/Ascent/mines/tier1.schem` (and tier2,
tier3) to replace the generated layout; its corner goes at the plot origin and
the pit region must line up with `layout` in `mines.yml`.

A plot unused for 24 hours is handed to the next player who needs one.

## Item registry and dupe alerts

Every valuable item gets a permanent id when it is created (books, scrolls,
dust, spawners, enchanted gear, kit items). Every five minutes
(`items.dupe-scan-interval` in `config.yml`) the plugin counts each id across
online inventories and ender chests; an id seen more times than it was
created raises a `dupe_alerts` row, a red message to everyone with
`ascent.staff.alerts` (ops have it), and a Discord post when
`DISCORD_WEBHOOK_URL` is set in `.env`. Splitting a stack does not trigger it.

Nothing creates tagged items until Epic 3, so until then the scan reports zero.
To see the machinery work before that, watch `/ascent debug items` and the
`items`, `item_events` and `dupe_alerts` tables.

## Database

The plugin refuses to start without MariaDB. `docker compose up -d` in the repo
starts it; `scripts/start.sh` (which `dev.sh` uses) hands the plugin the
credentials from `.env` as environment variables, so there is nothing to
configure in the server directory. If you start Paper any other way, export
`MARIADB_USER` and `MARIADB_PASSWORD` yourself first.

What the log tells you on boot:

| Line | Meaning |
|---|---|
| `Applied N database migration(s)` | A new schema version was created. Expected on first boot and after pulling a build that adds a `V<n>__*.sql` file |
| `Database schema is up to date` | Normal |
| `DATABASE UNAVAILABLE: ...` then `disabling` | The plugin is off. The line says why: MariaDB not running, wrong password in `.env`, or the migration failed |
| `Redis at ... is unavailable` | Not fatal. `/baltop` reads the database until Redis answers |

Poke at the data directly:

```bash
docker compose exec mariadb mariadb -u ascent -p ascent_dev   # password from .env
```

```sql
SHOW TABLES;
SELECT name, `rank`, balance, last_seen FROM players ORDER BY last_seen DESC LIMIT 10;
SELECT * FROM money_transactions ORDER BY id DESC LIMIT 10;
SELECT * FROM admin_actions ORDER BY id DESC LIMIT 10;
SELECT * FROM flyway_schema_history;
```

Profiles are written every 60 seconds (`players.autosave-interval` in
`config.yml`), on quit, and all at once when the server stops. Killing the
server process instead of typing `stop` loses up to 60 seconds of progress.

The database integration tests run in CI against a MariaDB service. To run
them locally against the dev database (it is **wiped** by the tests, so never
point this at anything you care about):

```bash
export ASCENT_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3306/ascent_dev
export ASCENT_TEST_DB_USER=ascent
export ASCENT_TEST_DB_PASSWORD='the MARIADB_PASSWORD from .env'
./gradlew test
```

## Editing configuration

Every tunable number lives in `~/ascent-server/plugins/Ascent/*.yml`, one file
per module, and every player-facing string in `messages.yml` there. Edit, then
`/ascent reload`. A file with a mistake is reported in chat and in the console
with the file name, the path inside it and what was expected, and keeps its
previous values until fixed. Delete a file to get the bundled default back on
the next reload.

## Client compatibility

Every story with player-visible behaviour is verified on both clients before it
is done:

- a 1.8.9 client (Lunar or Badlion) through ViaRewind, and
- a current 1.21.x vanilla client.

Combat feel is checked against the OldCombatMechanics checklist in
`server/PLUGINS.md`.

**Verified 2026-09-21** on Paper 1.21.11 build 132, OldCombatMechanics 2.6.0,
by the owner: a 1.21.11 client and a 1.8.9 client each join through the
ViaVersion chain; on both, every sword swing lands with no cooldown and no
sweep, a sword blocks on right-click, and crafting a shield is refused. Offhand
disabled, `/ocm mode` reports `old`, `/spark tps` answers. Closes E0-S3.

## Unit tests

```bash
./gradlew test                       # everything
./gradlew :ascent-api:test           # one module
./gradlew test --tests '*Provider*'  # one class
```

Reports land in `<module>/build/reports/tests/test/index.html`. The PRD targets
70% line coverage on touched packages in `ascent-plugin` core.
