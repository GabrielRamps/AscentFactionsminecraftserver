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

Epic 0 ships `/ascent version` only. Later stories add commands here as they
land; each row should say what the command does and which story added it.

| Command | Purpose | Story |
|---|---|---|
| `/ascent version` | Print the running plugin version | E0-S4 |

## Client compatibility

Every story with player-visible behaviour is verified on both clients before it
is done:

- a 1.8.9 client (Lunar or Badlion) through ViaRewind, and
- a current 1.21.x vanilla client.

Combat feel is checked against the OldCombatMechanics checklist in
`server/PLUGINS.md`.

## Unit tests

```bash
./gradlew test                       # everything
./gradlew :ascent-api:test           # one module
./gradlew test --tests '*Provider*'  # one class
```

Reports land in `<module>/build/reports/tests/test/index.html`. The PRD targets
70% line coverage on touched packages in `ascent-plugin` core.
