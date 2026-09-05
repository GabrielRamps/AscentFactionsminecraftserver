# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the version is bumped
once per sprint (PRD §5.6).

## [Unreleased]

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
  server, with a check after every step.

### Fixed

- Google Java Format violation in the `AscentCommand` javadoc that failed CI.
