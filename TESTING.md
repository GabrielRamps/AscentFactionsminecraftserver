# Testing on the dev server

## The loop

```bash
./dev.sh                 # build, copy jar, restart, wait for "Done" (target: < 30s boot)
./dev.sh --no-build      # restart only
./dev.sh --attach        # same as ./dev.sh, then attach to the console
tmux attach -t ascent    # console; Ctrl+B then D to detach
tail -f ~/ascent-server/logs/latest.log
```

If `dev.sh` says the server exited during startup, the cause is always in `logs/latest.log`.
Search it for `[Ascent]` first, then for `SEVERE` and `Exception`.

## Reading `latest.log`

- `Ascent <version> enabled on Paper <mc> (Java 21)` - our plugin loaded. If it's missing, look
  above it for the stack trace that prevented it.
- `Done (x.xxxs)! For help, type "help"` - the server is up.
- `Can't keep up!` - the main thread fell behind. Run `/spark tps` and `/spark profiler start`.
- Every module's config validation error names the file and key; fix the YAML and `/ascent reload`.

## Performance

```
/spark tps                  # TPS over 5s / 10s / 1m / 5m / 15m and MSPT
/spark profiler start       # sample for ~30s of the thing you're testing
/spark profiler stop        # prints a link to the flame graph
/spark healthreport
```

Target from the PRD: TPS >= 19.5 sustained, <= 5 ms/tick from Ascent code at 200 players.

## Giving yourself things

Admin commands arrive with their stories and are listed here as they land. Until then, use
vanilla `/op`, `/give`, and `/xp`.

| Command | Story | Purpose |
|---------|-------|---------|
| `/ascent reload` | E1-S1 | Reload all YAML without restart |
| `/ascent debug tps\|db\|items` | E1-S6 | Health checks |
| `/ascent give <player> <kind> [args]` | E1-S6 | Books, dust, scrolls, spawners, money, xp |
| `/ascent rank set\|add <player> <value>` | E1-S6 | Jump to a rank for testing unlocks |
| `/ascent item lookup <uuid>` | E1-S5 | Full event history of a tagged item |

## Two clients

Every player-facing story is verified on both a 1.8.9 client (Lunar/Badlion) and a 1.21 client.
Both should hit-register on each other with no attack cooldown (OldCombatMechanics). If the 1.8.9
client can't connect, check that ViaVersion, ViaBackwards, and ViaRewind all loaded in the log.

## Database

```bash
docker compose exec mariadb mariadb -u ascent -p ascent_dev     # SQL shell
docker compose exec redis redis-cli -a "$REDIS_PASSWORD"         # Redis shell
docker compose logs -f mariadb
```
