# Changelog

All notable changes to Ascent are recorded here. Version bumps happen once per sprint.

## [Unreleased]

### Sprint 0 - Environment (Epic 0)

- Gradle multi-module build (`ascent-api`, `ascent-plugin`), Java 21 toolchain, Shadow, Spotless.
- Plugin skeleton that loads on Paper 1.21.11 and logs its version.
- `copyToServer` task, `dev.sh` rebuild-and-restart loop, GitHub Actions build with jar artifact.
- `server/` scripts: Paper download via Fill v3 with checksum, third-party plugins via Modrinth,
  Aikar's flags start script, one-shot bootstrap.
- Docker Compose for MariaDB 11 and Redis 7; `.env.example`.
- `TESTING.md`, `README.md` runbook, OldCombatMechanics configuration notes.
