# Setup, start to finish

The complete Epic 0 runbook: from an empty Linux box to standing in your own
Minecraft server with the Ascent plugin loaded. Follow it in order. Every step
says what you should see, so you know whether it worked before moving on.

Budget an afternoon. Most of it is downloads.

## Before you start

You need three things:

- **A Linux box.** Ubuntu 24.04. For development anything with 4 GB of RAM
  works; the PRD sizes production at 8 vCPU and 32 GB.
- **A Minecraft Java account.** The server runs with `online-mode=true`, so
  cracked clients cannot join.
- **Two clients to test with.** A 1.8.9 client (Lunar or Badlion) and a current
  1.21.x vanilla client. You need both, because the whole point of the combat
  setup is that both work.

---

## 1. Install the tools

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git tmux curl jq unzip
```

Then Docker, for the databases:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

Log out and back in, so your shell picks up the `docker` group.

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

Skip this if you are running on a machine you connect to locally.

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

Connect from your 1.21 client to your box's IP on port 25565. Once in, run:

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

Day-to-day commands live in `TESTING.md`. What to build next is in
`docs/ascent-factions-phase-1-prd.md`, section 10.2.
