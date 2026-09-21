package gg.ascent.api.player;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The single in-memory object for an online player (PRD E1-S3).
 *
 * <p>Every module reads and writes player state here and nowhere else; no module keeps its own
 * player map. Any setter marks the profile dirty, and the player service writes dirty profiles to
 * the database every autosave interval, on quit and at shutdown.
 *
 * <p>Main thread only. A profile is never shared with a database worker; the service takes a {@link
 * #snapshot()} on the main thread and hands that to the worker instead.
 */
public final class PlayerProfile {

  private final UUID uuid;
  private final Instant firstSeen;
  private String name;
  private int rank;
  private long xp;
  private long xpTotal;
  private long xpBanked;
  private long balance;
  private double power;
  private Instant powerUpdated;
  private @Nullable Integer factionId;
  private boolean starterKitClaimed;
  private Instant lastSeen;
  private long playSeconds;
  private boolean dirty;

  public PlayerProfile(PlayerSnapshot row) {
    this.uuid = row.uuid();
    this.firstSeen = row.firstSeen();
    this.name = row.name();
    this.rank = row.rank();
    this.xp = row.xp();
    this.xpTotal = row.xpTotal();
    this.xpBanked = row.xpBanked();
    this.balance = row.balance();
    this.power = row.power();
    this.powerUpdated = row.powerUpdated();
    this.factionId = row.factionId();
    this.starterKitClaimed = row.starterKitClaimed();
    this.lastSeen = row.lastSeen();
    this.playSeconds = row.playSeconds();
  }

  /** An immutable copy of the current state. */
  public PlayerSnapshot snapshot() {
    return new PlayerSnapshot(
        uuid,
        name,
        rank,
        xp,
        xpTotal,
        xpBanked,
        balance,
        power,
        powerUpdated,
        factionId,
        starterKitClaimed,
        firstSeen,
        lastSeen,
        playSeconds);
  }

  public UUID uuid() {
    return uuid;
  }

  public Instant firstSeen() {
    return firstSeen;
  }

  public String name() {
    return name;
  }

  public void setName(String name) {
    if (!this.name.equals(name)) {
      this.name = name;
      dirty = true;
    }
  }

  public int rank() {
    return rank;
  }

  public void setRank(int rank) {
    if (rank < 1) {
      throw new IllegalArgumentException("rank must be at least 1, got " + rank);
    }
    this.rank = rank;
    dirty = true;
  }

  public long xp() {
    return xp;
  }

  public void setXp(long xp) {
    if (xp < 0) {
      throw new IllegalArgumentException("xp cannot be negative, got " + xp);
    }
    this.xp = xp;
    dirty = true;
  }

  public long xpTotal() {
    return xpTotal;
  }

  public void setXpTotal(long xpTotal) {
    this.xpTotal = xpTotal;
    dirty = true;
  }

  public long xpBanked() {
    return xpBanked;
  }

  public void setXpBanked(long xpBanked) {
    this.xpBanked = xpBanked;
    dirty = true;
  }

  public long balance() {
    return balance;
  }

  /** Sets the balance outright. The economy service is the only intended caller. */
  public void setBalance(long balance) {
    if (balance < 0) {
      throw new IllegalArgumentException("balance cannot be negative, got " + balance);
    }
    this.balance = balance;
    dirty = true;
  }

  public double power() {
    return power;
  }

  public void setPower(double power, Instant at) {
    this.power = power;
    this.powerUpdated = at;
    dirty = true;
  }

  public Instant powerUpdated() {
    return powerUpdated;
  }

  public @Nullable Integer factionId() {
    return factionId;
  }

  public void setFactionId(@Nullable Integer factionId) {
    this.factionId = factionId;
    dirty = true;
  }

  public boolean starterKitClaimed() {
    return starterKitClaimed;
  }

  public void setStarterKitClaimed(boolean claimed) {
    this.starterKitClaimed = claimed;
    dirty = true;
  }

  public Instant lastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Instant lastSeen) {
    this.lastSeen = lastSeen;
    dirty = true;
  }

  public long playSeconds() {
    return playSeconds;
  }

  public void addPlaySeconds(long seconds) {
    if (seconds > 0) {
      this.playSeconds += seconds;
      dirty = true;
    }
  }

  /** Whether there are changes the database has not seen. */
  public boolean isDirty() {
    return dirty;
  }

  /** Forces a write on the next autosave. */
  public void markDirty() {
    dirty = true;
  }

  /** Called by the player service once a snapshot has been handed to the database. */
  public void clearDirty() {
    dirty = false;
  }

  @Override
  public String toString() {
    return "PlayerProfile[" + name + " " + uuid + " rank=" + rank + " balance=" + balance + "]";
  }
}
