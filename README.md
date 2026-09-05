# Ascent Factions

A Minecraft Java server that combines the team stakes and enchant-lottery gear
economy of classic OP Factions with a prisons-style personal progression spine.
Your rank is yours forever; your faction's empire is contestable, but only in
bounded, scheduled ways.

## Documents

| Document | What it is |
|---|---|
| `docs/ascent-factions-concept.md` | Game design: pillars, the three-layer model, systems |
| `docs/ascent-factions-phase-1-prd.md` | Phase 1 requirements, stories, schema, sprint plan |
| `docs/cosmicpvp-design-bible.md` | Reference mechanics from the genre it descends from |
| `SETUP.md` | First-time setup, from an empty box to a running server |
| `TESTING.md` | How to run the server and test a change |
| `server/PLUGINS.md` | Third-party plugins and the 1.8-combat checklist |

The PRD is the operating document. Work stories in the order given in its
release plan, and run its Definition of Done before closing one.

## Layout

```
ascent-api/      interfaces, custom events and value types; no implementation
ascent-plugin/   the Paper plugin: services, listeners, commands, GUIs, storage
docs/            design and requirements
scripts/         bootstrap.sh and the setup scripts it drives
server/          server config templates and third-party plugin notes
dev.sh           build, deploy and restart the dev server
docker-compose.yml   MariaDB 11 and Redis 7 for one environment
```

Modules talk to each other only through `ascent-api`. Nothing in `ascent-plugin`
should be imported across module boundaries.

## Quick start

```bash
cp .env.example .env      # then fill in passwords and your server directory
docker compose up -d      # MariaDB + Redis
scripts/bootstrap.sh      # Paper, EULA, server.properties, all 11 plugins
./dev.sh                  # build, deploy, restart, wait for "Done"
```

You need Java 21, Docker, tmux, curl and jq on the box. **`SETUP.md` is the
full walkthrough**, from an empty machine to standing in the server; start
there if this is your first time.

## Stack

Paper 1.21.x on Java 21, built with Gradle (Kotlin DSL) and shaded into one jar.
MariaDB 11 for durable state, Redis 7 for leaderboards and short-lived caches.
1.8-style combat through OldCombatMechanics, with 1.8 clients supported by the
ViaVersion family. Formatting is Google Java Format, enforced by Spotless on
every build.

The Paper version is pinned in `gradle.properties` and upgraded only at sprint
boundaries, using `scripts/update-paper.sh`.
