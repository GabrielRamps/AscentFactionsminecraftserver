-- Ascent Factions, Phase 1 schema (PRD §6.3).
--
-- Conventions: utf8mb4, InnoDB; UUIDs as CHAR(36); money as BIGINT whole
-- dollars; DATETIME(3) columns hold UTC; created_at/updated_at where a row is
-- ever changed in place. Every ID is generated server-side: UUIDv7 for items,
-- AUTO_INCREMENT elsewhere.
--
-- Migrations are append-only. Never edit this file once it has run anywhere;
-- add V2__*.sql instead.

-- Factions come first so players.faction_id can reference them.
CREATE TABLE factions (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  name                VARCHAR(16)  NOT NULL,
  leader_uuid         CHAR(36)     NOT NULL,
  vault_balance       BIGINT       NOT NULL DEFAULT 0,
  home_world          VARCHAR(32)  NULL,
  home_x              DOUBLE       NULL,
  home_y              DOUBLE       NULL,
  home_z              DOUBLE       NULL,
  home_yaw            FLOAT        NULL,
  upkeep_unpaid_days  TINYINT      NOT NULL DEFAULT 0,
  created_at          DATETIME(3)  NOT NULL,
  updated_at          DATETIME(3)  NOT NULL,
  disbanded_at        DATETIME(3)  NULL,
  UNIQUE KEY uq_factions_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE players (
  uuid                 CHAR(36)      PRIMARY KEY,
  name                 VARCHAR(16)   NOT NULL,
  `rank`               SMALLINT      NOT NULL DEFAULT 1,
  xp                   BIGINT        NOT NULL DEFAULT 0,   -- progress toward next rank
  xp_total             BIGINT        NOT NULL DEFAULT 0,
  xp_banked            BIGINT        NOT NULL DEFAULT 0,   -- post-100, for prestige
  balance              BIGINT        NOT NULL DEFAULT 0,
  power                DECIMAL(6,2)  NOT NULL DEFAULT 0,
  power_updated        DATETIME(3)   NOT NULL,
  faction_id           INT           NULL,
  starter_kit_claimed  BOOLEAN       NOT NULL DEFAULT FALSE,
  first_seen           DATETIME(3)   NOT NULL,
  last_seen            DATETIME(3)   NOT NULL,
  play_seconds         BIGINT        NOT NULL DEFAULT 0,
  updated_at           DATETIME(3)   NOT NULL,
  INDEX idx_players_name (name),
  INDEX idx_players_faction (faction_id),
  INDEX idx_players_rank (`rank`),
  INDEX idx_players_balance (balance),
  CONSTRAINT fk_players_faction FOREIGN KEY (faction_id) REFERENCES factions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rank XP events, aggregated per minute per source.
CREATE TABLE xp_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid  CHAR(36)     NOT NULL,
  source       VARCHAR(32)  NOT NULL,
  amount       INT          NOT NULL,
  minute       DATETIME     NOT NULL,
  UNIQUE KEY uq_xp (player_uuid, source, minute)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE money_transactions (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid    CHAR(36)     NULL,
  faction_id     INT          NULL,
  amount         BIGINT       NOT NULL,
  balance_after  BIGINT       NOT NULL,
  reason         ENUM('PAY','SELL','SHOP_BUY','SHOP_SELL','KIT','CONTRACT','EVENT','UPKEEP',
                      'VAULT_DEPOSIT','VAULT_WITHDRAW','ADMIN','TRADE','FACTION_CREATE') NOT NULL,
  ref            VARCHAR(64)  NULL,
  created_at     DATETIME(3)  NOT NULL,
  INDEX idx_mt_player_time (player_uuid, created_at),
  INDEX idx_mt_faction_time (faction_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Registry of tagged items.
CREATE TABLE items (
  item_id           CHAR(36)     PRIMARY KEY,   -- UUIDv7
  kind              ENUM('BOOK_UNOPENED','BOOK_OPENED','WHITE_SCROLL','MAGIC_DUST','SPAWNER',
                         'GEAR','KIT_ITEM','XP_BOTTLE','LOOTBAG') NOT NULL,
  data              JSON         NOT NULL,      -- enchant_id, level, success, destroy, tier, mob_type, percent...
  quantity_created  INT          NOT NULL DEFAULT 1,
  created_by        CHAR(36)     NULL,
  created_reason    VARCHAR(32)  NOT NULL,
  created_at        DATETIME(3)  NOT NULL,
  destroyed_at      DATETIME(3)  NULL,
  INDEX idx_items_kind_created (kind, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE item_events (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id            CHAR(36)     NOT NULL,
  event              ENUM('CREATED','APPLIED','CONSUMED','DESTROYED','TRADED','DROPPED',
                          'PICKED_UP','SOLD','ADMIN_GIVE','ROLLBACK') NOT NULL,
  actor_uuid         CHAR(36)     NULL,
  counterparty_uuid  CHAR(36)     NULL,
  context            JSON         NULL,
  created_at         DATETIME(3)  NOT NULL,
  INDEX idx_ie_item (item_id, created_at),
  INDEX idx_ie_actor (actor_uuid, created_at),
  CONSTRAINT fk_item_events_item FOREIGN KEY (item_id) REFERENCES items (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dupe_alerts (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_id         CHAR(36)      NOT NULL,
  observed_count  INT           NOT NULL,
  holders         JSON          NOT NULL,
  created_at      DATETIME(3)   NOT NULL,
  resolved_at     DATETIME(3)   NULL,
  resolved_by     CHAR(36)      NULL,
  resolution      VARCHAR(255)  NULL,
  INDEX idx_da_item (item_id),
  INDEX idx_da_open (resolved_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE enchant_rolls (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid     CHAR(36)     NOT NULL,
  book_item_id    CHAR(36)     NOT NULL,
  target_item_id  CHAR(36)     NOT NULL,
  enchant_id      VARCHAR(32)  NOT NULL,
  level           TINYINT      NOT NULL,
  success         TINYINT      NOT NULL,
  destroy         TINYINT      NOT NULL,
  outcome         ENUM('SUCCESS','DESTROYED','SCROLL_SAVED','FAILED') NOT NULL,
  created_at      DATETIME(3)  NOT NULL,
  INDEX idx_er_player (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE faction_members (
  faction_id   INT          NOT NULL,
  player_uuid  CHAR(36)     NOT NULL,
  role         ENUM('LEADER','OFFICER','MEMBER','RECRUIT') NOT NULL,
  joined_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (faction_id, player_uuid),
  UNIQUE KEY uq_member (player_uuid),
  CONSTRAINT fk_faction_members_faction FOREIGN KEY (faction_id) REFERENCES factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE faction_relations (
  faction_a   INT          NOT NULL,
  faction_b   INT          NOT NULL,
  relation    ENUM('ALLY','ENEMY') NOT NULL,
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (faction_a, faction_b),
  INDEX idx_fr_b (faction_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE faction_perms (
  faction_id  INT          NOT NULL,
  role        ENUM('OFFICER','MEMBER','RECRUIT') NOT NULL,
  perm        VARCHAR(24)  NOT NULL,
  allowed     BOOLEAN      NOT NULL,
  PRIMARY KEY (faction_id, role, perm),
  CONSTRAINT fk_faction_perms_faction FOREIGN KEY (faction_id) REFERENCES factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE claims (
  world       VARCHAR(32)  NOT NULL,
  chunk_x     INT          NOT NULL,
  chunk_z     INT          NOT NULL,
  faction_id  INT          NOT NULL,
  is_core     BOOLEAN      NOT NULL DEFAULT FALSE,
  claimed_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (world, chunk_x, chunk_z),
  INDEX idx_claims_faction (faction_id),
  CONSTRAINT fk_claims_faction FOREIGN KEY (faction_id) REFERENCES factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE placed_spawners (
  world       VARCHAR(32)  NOT NULL,
  x           INT          NOT NULL,
  y           INT          NOT NULL,
  z           INT          NOT NULL,
  faction_id  INT          NOT NULL,
  mob_type    VARCHAR(24)  NOT NULL,
  count       SMALLINT     NOT NULL,
  item_id     CHAR(36)     NOT NULL,
  placed_by   CHAR(36)     NOT NULL,
  placed_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (world, x, y, z),
  INDEX idx_ps_faction (faction_id),
  CONSTRAINT fk_placed_spawners_faction FOREIGN KEY (faction_id) REFERENCES factions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mine_plots (
  plot_index    INT          PRIMARY KEY,
  owner_uuid    CHAR(36)     NULL,
  tier          TINYINT      NULL,
  allocated_at  DATETIME(3)  NULL,
  last_used     DATETIME(3)  NULL,
  mined_blocks  INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uq_mine_plots_owner (owner_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contracts_active (
  player_uuid    CHAR(36)     NOT NULL,
  slot           TINYINT      NOT NULL,
  contract_id    VARCHAR(32)  NOT NULL,
  progress       INT          NOT NULL DEFAULT 0,
  target         INT          NOT NULL,
  completed_at   DATETIME(3)  NULL,
  assigned_date  DATE         NOT NULL,
  PRIMARY KEY (player_uuid, slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contracts_history (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid   CHAR(36)     NOT NULL,
  contract_id   VARCHAR(32)  NOT NULL,
  completed_at  DATETIME(3)  NOT NULL,
  INDEX idx_ch_player (player_uuid, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kit_cooldowns (
  player_uuid  CHAR(36)     NOT NULL,
  kit_id       VARCHAR(32)  NOT NULL,
  next_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (player_uuid, kit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE event_results (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type         ENUM('KOTH','ENVOY') NOT NULL,
  started_at         DATETIME(3)  NOT NULL,
  ended_at           DATETIME(3)  NULL,
  winner_uuid        CHAR(36)     NULL,
  winner_faction_id  INT          NULL,
  details            JSON         NULL,
  INDEX idx_evr_type_time (event_type, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ftop_snapshots (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  taken_at    DATETIME(3)  NOT NULL,
  faction_id  INT          NOT NULL,
  value       BIGINT       NOT NULL,
  position    SMALLINT     NOT NULL,
  INDEX idx_ftop_time (taken_at, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ftop_payouts (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  week_start    DATE         NOT NULL,
  faction_id    INT          NOT NULL,
  position      SMALLINT     NOT NULL,
  value         BIGINT       NOT NULL,
  fulfilled_at  DATETIME(3)  NULL,
  UNIQUE KEY uq_ftop_payout (week_start, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE death_log (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  victim_uuid       CHAR(36)     NOT NULL,
  killer_uuid       CHAR(36)     NULL,
  world             VARCHAR(32)  NOT NULL,
  x                 INT          NOT NULL,
  y                 INT          NOT NULL,
  z                 INT          NOT NULL,
  dropped_item_ids  JSON         NULL,
  created_at        DATETIME(3)  NOT NULL,
  INDEX idx_dl_victim (victim_uuid, created_at),
  INDEX idx_dl_killer (killer_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_snapshots (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid  CHAR(36)     NOT NULL,
  taken_at     DATETIME(3)  NOT NULL,
  inventory    BLOB         NOT NULL,
  enderchest   BLOB         NOT NULL,
  INDEX idx_is_player (player_uuid, taken_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_actions (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_uuid  CHAR(36)     NOT NULL,
  action      VARCHAR(64)  NOT NULL,
  target      VARCHAR(64)  NULL,
  args        JSON         NULL,
  created_at  DATETIME(3)  NOT NULL,
  INDEX idx_aa_actor (actor_uuid, created_at),
  INDEX idx_aa_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sessions (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  player_uuid  CHAR(36)     NOT NULL,
  joined_at    DATETIME(3)  NOT NULL,
  quit_at      DATETIME(3)  NULL,
  INDEX idx_s_player (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
