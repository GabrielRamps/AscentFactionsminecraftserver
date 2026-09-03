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
