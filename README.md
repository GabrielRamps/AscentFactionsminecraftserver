# Ascent Factions

OP Factions with a prisons progression spine: a Paper plugin (`Ascent`) plus the server, database,
and tooling around it. Design and scope live in `docs/`:

- `docs/ascent-factions-concept.md` - the game concept and three-layer model
- `docs/ascent-factions-phase-1-prd.md` - Phase 1 PRD; every story in `§4.2` is a Claude Code task
- `docs/cosmicpvp-design-bible.md` - reference mechanics

## Repository layout

```
ascent-api/        service interfaces, DTOs, custom events (modules only talk through these)
ascent-plugin/     the Paper plugin: implementations, listeners, commands, GUIs, DB, config
server/            scripts that install and run the local Paper test server
server/config/     deliberate overrides for third-party plugin configs
docker-compose.yml MariaDB 11 + Redis 7 for local/dev
dev.sh             build -> copy jar -> restart server -> wait for "Done"
TESTING.md         how to test on the dev server
gradle/libs.versions.toml   every dependency version, including the Paper API pin
```

## Version pins

| What            | Where                                   | Value              |
|-----------------|-----------------------------------------|--------------------|
| Minecraft/Paper | `gradle.properties` -> `mcVersion`      | 1.21.11            |
| Paper build     | `gradle.properties` -> `paperBuild`     | latest             |
| paper-api       | `gradle/libs.versions.toml` -> `paper`  | 1.21.11-R0.1-SNAPSHOT |
| Java            | Gradle toolchain                        | 21                 |

Bump `mcVersion` and `paper` together. Paper 1.21.11 is the last release of the 1.21 line; Mojang's
newer year-numbered releases (26.1, 26.2, ...) are supported by ViaVersion for *clients*, so players
on those versions can still join. Move the server itself to 26.x only at a sprint boundary and only
once OldCombatMechanics, GrimAC, and ViaRewind have confirmed support.

## Epic 0 runbook (do this by hand, once)

Requirements on the box: Java 21 (Temurin), Docker with the compose plugin, `tmux`, `jq`, `curl`.

```bash
# 0. clone and enter the repo
git clone https://github.com/GabrielRamps/AscentFactionsminecraftserver.git ascent && cd ascent

# 1. secrets and local settings
cp .env.example .env            # edit passwords, ASCENT_PORT, ASCENT_HEAP

# 2. database + cache
docker compose up -d && docker compose ps

# 3. Paper + third-party plugins into ~/ascent-server (or $ASCENT_SERVER_DIR)
server/bootstrap.sh

# 4. first boot so every plugin writes its defaults, then stop it (Ctrl+C)
server/start.sh
#    -> apply the OldCombatMechanics settings from server/config/README.md

# 5. build the Ascent plugin, install it, restart, wait for "Done"
./dev.sh

# 6. open the console, op yourself, join from a 1.8.9 client and a 1.21 client
tmux attach -t ascent            # Ctrl+B, D detaches
```

`./gradlew build` on its own produces `ascent-plugin/build/libs/Ascent-<version>.jar`.
GitHub Actions builds every push and attaches the jar to the run as an artifact.

## Working the PRD

Work stories in the sprint order of PRD `§10.2`. Each story is self-contained: paste it, with its
acceptance criteria and the relevant `§6` schema/config sections, into Claude Code as the task.
Run the Definition of Done (`§9`) before closing a story, and never skip the dev-server check.
