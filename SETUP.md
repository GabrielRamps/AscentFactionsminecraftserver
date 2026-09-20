# Setup, start to finish

The complete Epic 0 runbook: from an empty machine to standing in your own
Minecraft server with the Ascent plugin loaded. Follow it in order. Every step
says what you should see, so you know whether it worked before moving on.

Budget an afternoon. Most of it is downloads.

## Before you start

You need three things:

- **A Linux environment.** Ubuntu 24.04. On Windows that means WSL2; see the
  next section. For development anything with 4 GB of RAM works; the PRD sizes
  production at 8 vCPU and 32 GB.
- **A Minecraft Java account.** The server runs with `online-mode=true`, so
  cracked clients cannot join.
- **Two clients to test with.** A 1.8.9 client (Lunar or Badlion) and a current
  1.21.x vanilla client. You need both, because the whole point of the combat
  setup is that both work.

---

## 0. Windows only: install WSL2

Every script in this repo is bash and uses `tmux`, GNU `find` and GNU `stat`.
None of that exists in PowerShell, and Git Bash has no `tmux`. Run the server
inside WSL2, and keep playing Minecraft on Windows as normal.

In an **Administrator** PowerShell:

```powershell
wsl --install -d Ubuntu-24.04
```

Reboot when it asks, then open Ubuntu from the Start menu and create your
username and password. Everything from step 1 onward runs in that Ubuntu shell,
not in PowerShell.

Docker itself is handled in step 1 below — Ubuntu 24.04 on WSL2 ships with
systemd enabled, so the normal Linux Docker install just works there, and
there's no separate Windows app to install. (Docker Desktop with WSL
integration is the other common route; if you already have Docker Desktop
installed, use that instead and skip the Docker install in step 1 — never run
both, they fight over the same socket.)

Two things that will bite you if you skip them:

- **Keep the repo in the Linux filesystem.** Clone into `~/` inside Ubuntu, not
  into `/mnt/c/...`. Gradle across the Windows filesystem boundary is slow
  enough to turn a twenty-second build into minutes.
- **Connect to `localhost`.** WSL2 forwards localhost, so your Windows
  Minecraft client joins the server at `localhost:25565` with no extra setup.
  You can skip the firewall step entirely.

---

## 1. Install the tools

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git tmux curl jq unzip
```

Then Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

The installer prints "WSL DETECTED: We recommend using Docker Desktop" and
pauses for 20 seconds — that's a heads-up, not an error, and only matters if
you already have Docker Desktop installed. If you don't, let it run; on
Ubuntu 24.04 it installs Docker Engine directly via systemd and the daemon
starts immediately. **If you do already have Docker Desktop, Ctrl-C instead**
and enable WSL integration for Ubuntu-24.04 in its settings (Settings →
Resources → WSL integration) rather than installing a second engine.

Either way, close this terminal and reopen Ubuntu (or `wsl --shutdown` from
PowerShell and relaunch) so your shell picks up the new `docker` group.

**Check it worked:**

```bash
java -version        # must say 21
docker compose version
```

If `java -version` reports anything other than 21, fix it before continuing.
Nothing else in this guide will work on the wrong Java.

## 2. Get the code

```bash
git clone https://github.com/GabrielRamps/AscentFactionsminecraftserver.git
cd AscentFactionsminecraftserver
git checkout claude/minecraft-server-setup-48sjxa
```

## 3. Set your passwords

```bash
cp .env.example .env
```

Generate three passwords and paste them in:

```bash
openssl rand -base64 24    # run once per password
```

Open `.env` and set `MARIADB_ROOT_PASSWORD`, `MARIADB_PASSWORD`,
`REDIS_PASSWORD`, and `ASCENT_SERVER_DIR`. The server directory should be an
absolute path, for example `/home/youruser/ascent-server`.

`.env` is git-ignored. It must never be committed.

## 4. Start the databases

```bash
docker compose up -d
docker compose ps
```

**You should see** two containers, `mariadb` and `redis`, both `healthy`. If
either says `starting`, wait thirty seconds and check again. If either says
`unhealthy`, your `.env` passwords are probably empty.

Nothing uses these until Epic 1, but getting them up now means one less thing
to debug later.

## 5. Bootstrap the server

One command downloads Paper, accepts the EULA, writes the server settings and
installs all eleven third-party plugins:

```bash
scripts/bootstrap.sh
```

**You should see** a list of installed plugin jars at the end. Warnings about
an individual plugin are survivable: install that one by hand from
`server/PLUGINS.md` and re-run. An error about the Paper API is not survivable,
because nothing else works without a server jar.

Running this means you accept the [Minecraft EULA](https://aka.ms/MinecraftEULA).

## 6. Open the firewall

Skip this on WSL2, and on any machine you connect to locally.

```bash
sudo ufw allow 25565/tcp
sudo ufw allow OpenSSH
sudo ufw enable
```

Only open the Minecraft port and SSH. The databases bind to localhost on
purpose and must not be exposed.

## 7. Build and start

```bash
./dev.sh
```

This builds the plugin, copies it into the server, starts the server and waits
until it reports `Done`.

**You should see** `==> Server up in Ns.` The first run takes longer than
later ones, because Gradle is downloading dependencies. Under thirty seconds is
the target from then on.

## 8. Make yourself an operator

```bash
tmux attach -t ascent
```

You are now at the server console. Type:

```
op YourMinecraftName
```

Press `Ctrl-b` then `d` to detach, leaving the server running. Do not press
`Ctrl-c`: that kills the server.

## 9. Join and check the plugin

Connect from your 1.21 client on port 25565: use `localhost` on WSL2 or a
local machine, or the box's IP if the server is remote. Once in, run:

```
/ascent version
```

**You should see** the Ascent name and version. If the command is unknown, the
plugin did not load. Check the log:

```bash
grep -i ascent ~/ascent-server/logs/latest.log
```

That is stories E0-S1, E0-S2 and E0-S4 done.

## 10. Configure the combat feel

This is the most important step in Epic 0, and the only one that needs your
judgement rather than a command.

Open `~/ascent-server/plugins/OldCombatMechanics/config.yml` and work through
the checklist in `server/PLUGINS.md`: attack cooldown off, 1.8 armour, old
golden apples, old regeneration, sword blocking, 1.8 knockback, and crafting
denied for shields, elytra, tridents, crossbows and netherite.

Then `./dev.sh` to restart, and test with both clients joined at once:

- Hits register on both, with no cooldown delay and no sweep attack.
- Right-clicking with a sword blocks.
- Crafting a shield is denied.

If combat does not feel like 1.8 to you, stop and tune it. The PRD flags this
as a high-impact risk, and every later story assumes it is right.

That is story E0-S3 done, and all of Epic 0 with it.

## 11. Commit the Paper pin

`scripts/bootstrap.sh` rewrote `gradle.properties` with the exact Paper build
you installed. Commit it so the build and the server stay in step:

```bash
git add gradle.properties
git commit -m "Pin Paper to the build installed on dev"
git push
```

---

## When something breaks

| Symptom | Cause |
|---|---|
| `java -version` is not 21 | Another JDK is ahead on your PATH. Use `sudo update-alternatives --config java`. |
| `permission denied` from docker | You did not log out after `usermod -aG docker`. |
| Server exits right after starting | Read `~/ascent-server/logs/latest.log`. Usually a plugin built for the wrong Minecraft version. |
| `/ascent version` unknown | The plugin failed to load. `grep -i 'could not load' ~/ascent-server/logs/latest.log`. |
| 1.8 client cannot join | ViaVersion, ViaBackwards and ViaRewind must all three be installed. |
| Cannot connect at all | Firewall, or the server bound to a different port. Check `server-port` in `~/ascent-server/server.properties`. |
| WSL2: build is painfully slow | The repo is on `/mnt/c`. Move it into `~/` inside Ubuntu. |
| WSL2: `docker` not found | Docker Desktop's WSL integration is off for this distro. Settings → Resources → WSL integration. |

Day-to-day commands live in `TESTING.md`. What to build next is in
`docs/ascent-factions-phase-1-prd.md`, section 10.2.
