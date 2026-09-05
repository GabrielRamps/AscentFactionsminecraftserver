# Testing Ascent locally

This is the E0-S4 test loop. Everything here assumes the test server lives in `~/ascent-server`
(override with `ASCENT_SERVER_DIR`).

## One-time setup

```bash
brew install openjdk@25 gradle tmux colima docker docker-compose   # toolchain
server/bootstrap.sh          # downloads pinned Paper, writes eula + server.properties + start.sh
cd ~/ascent-server && ./start.sh   # first boot generates the world; type `op <you>` then `stop`
server/install-plugins.py    # LuckPerms, Vault, PAPI, OCM, Via*, GrimAC, FAWE, WorldGuard
./dev.sh                     # boot once with plugins so OCM writes its default config
server/configure-ocm.py      # apply the 1.8 combat settings, then ./dev.sh --no-build
cp .env.example .env         # then edit passwords
colima start && docker compose up -d   # MariaDB 11 + Redis 7
```

spark is bundled with Paper, so it is not installed separately.

Add this to `~/.zshrc` so `java` and `./gradlew` work in any shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
```

## The loop

```bash
./dev.sh            # gradle build -> copy jar -> restart server in tmux -> wait for "Done"
./dev.sh --attach   # same, then open the console (detach with Ctrl-B then D)
./dev.sh --logs     # tail ~/ascent-server/logs/latest.log
./dev.sh --stop     # graceful stop
```

Target: server reaches `Done` in under 30 seconds with all plugins. Check the number printed
by `dev.sh`. The very first boot is much slower because it generates the world and every plugin
writes its default config.

## Reading the log

`~/ascent-server/logs/latest.log` is the current session. Older sessions are gzipped next to it.

- Startup problems: search for `[Ascent]` lines and for `Could not load` / `Error occurred while enabling`.
- A stack trace mentioning `gg.ascent` is ours. One mentioning `gg.ascent.libs` is a shaded library.
- `Can't keep up!` means the main thread is behind. Use spark to find out why.

## Performance checks

spark is built into Paper:

- `/spark tps` shows TPS and MSPT. Healthy is 20 TPS and under 25 ms MSPT.
- `/spark profiler start` then `/spark profiler stop` after a minute gives a flame graph link.
- `/spark healthreport` summarizes memory and GC.

## Giving yourself things to test with

Admin commands arrive with their stories (E1 economy, E2 ranks and XP, E4 books). Until then:

- Vanilla: `/give @s diamond_sword`, `/gamemode creative`, `/tp`.
- Ranks and permissions: `/lp user <you> parent set <group>` (LuckPerms).
- Money, XP and books will use `/ascent admin ...` subcommands. Each story that adds one must add it
  to this section.

## Combat versions

E0-S3 requires that a 1.8.9 client and a current client can both join and land hits. Test with two
accounts or a friend: join on each version, hit each other, confirm the damage and knockback feel
match the 1.8 style OldCombatMechanics is configured for.

## Unit tests

```bash
./gradlew test                 # everything
./gradlew :ascent-plugin:test --tests 'gg.ascent.plugin.SomeTest'
```

Coverage target from the PRD is 70% on touched packages. Reports land in
`ascent-plugin/build/reports/tests/test/index.html`.
