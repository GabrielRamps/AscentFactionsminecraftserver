# CosmicPvP OP Factions — Design Bible & Rebuild Roadmap

## TL;DR

- CosmicPvP's addictive loop was **grind spawners → buy XP-book enchants at the Enchanter → gamble them onto gear (success%/destroy%) → build a "god set" → raid/PvP/KOTH → climb F-Top for real prizes**, refreshed by seasonal planet resets; the single most important system to nail is the **custom-enchant book-application gamble** (Section 1) because every other system feeds it.
- The five primary tiers were **Simple → Unique → Elite → Ultimate → Legendary**, later joined by three special tiers **Soul → Heroic → Mastery**; a book applies as a single roll where success%, destroy%, and "neither" (book consumed, item intact) are three distinct outcomes, and destroy is prevented by White Scrolls.
- Build it in phases: **Phase 1 = classic 2014–2016 core** (5 tiers, Enchanter/Tinkerer, GKits, planets, Envoys/KOTH, spawner economy, F-Top); later phases add **Soul/Heroic/Mastery enchants, Outposts, Conquest, Masks, Trinkets, Pets, Armor Crystals** — exactly the order Cosmic itself grew.

> **Sourcing note.** CosmicPvP (Java) was founded in late 2015 by YouTubers Preston Arsement (PrestonPlayz) and Woody/"MrWoofless" (per Grokipedia: *"founded in late 2015 by popular YouTubers Preston Arsement… and Woody… based in the United States"*), peaked at **over 6,000 concurrent players in late 2016** (Grokipedia: *"At its height in late 2016, CosmicPvP achieved over 6,000 concurrent players"*), generated an estimated **$25M over ~8 years**, and **closed its factions mode on February 5, 2023** (Grokipedia: *"The factions mode closed on February 5, 2023, followed by Cosmic Prisons in late 2023"*). Its official wiki/forum are now largely offline. The highest-fidelity surviving structured mirror of the *same mechanics* is the **CosmicPE wiki** (the company's Bedrock sister server, wiki.cosmicpe.me), plus the **AdvancedEnchantments "Cosmic Enchants (200+)"** community replica. Where a number comes from CosmicPE or a replica rather than the original Java server, it is tagged. Confidence tags: **[Confirmed]** = 2+ independent/official-archive sources; **[Likely]** = one good source; **[Unverified]** = community memory/inference.

---

## Key Findings

1. **The enchant gamble is the core dependency.** Books are bought with XP at the Enchanter, redeemed to roll a random enchant+level+success%+destroy%, then dragged onto gear. This one mechanic drives the XP economy, the spawner economy, the Tinkerer, the Alchemist, dusts, and scrolls. Build it first and correctly.
2. **Five primary tiers in the classic era, three special tiers added later.** Per the CosmicPE wiki: *"There are 5 primary tiers—Simple, Unique, Elite, Ultimate, and Legendary, and 3 special tiers—Soul, Heroic, and Mastery."* Simple→Legendary were the 2014–2016 core; Soul/Heroic/Mastery came later.
3. **Planets are shards, not just cosmetic.** Each planet had its own themed top rank, its own map/reset cadence, and (per era) its own tweaks. 400-slot planets were advertised.
4. **Economy is spawner-driven and F-Top pays real money.** Spawners are the primary F-Top value source; players grind them overnight into sell chests, and top factions won real-cash/store prizes each season.
5. **1.8 combat with heavy custom-enchant layering.** No 1.9 attack cooldown; the meta was defined by defensive/offensive enchant stacks, gapples, and debuff-heavy potion play, later complicated by Soul actives, Masks, and Trinkets.

---

## Details

## Section 1 — Custom Enchantments (the core)

### 1.1 Tier structure

Five **primary tiers** plus three **special tiers** [Confirmed, CosmicPE wiki + AdvancedEnchantments replica + Cosmic forum threads]:

| Order | Tier | Class | Notes |
|---|---|---|---|
| 1 | Simple | Primary | Cheapest books; utility/low-impact |
| 2 | Unique | Primary | |
| 3 | Elite | Primary | |
| 4 | Ultimate | Primary | |
| 5 | Legendary | Primary | Strongest directly-appliable tier |
| 6 | Soul | Special | Applies instantly but **consumes souls per activation**; needs a Soul Gem in inventory to be active |
| 7 | Heroic | Special | **Upgraded variant** of a specific primary enchant; the item must already have the **max level** of the base enchant; applying it **removes** the base enchant |
| 8 | Mastery | Special | No special applicability rules, but **Armorset Crystals cannot be applied** to an item that has a Mastery enchant; books need a **Mastery Shard** to apply to Mastery kits |

- **Display color-coding:** books and lore lines are color-coded by rarity (simple→legendary, then soul/heroic/mastery). The exact hex/§-codes per tier are **[Unverified]** from surviving text; the user should pick a palette (common convention: Simple = gray/white, Unique = green, Elite = blue/aqua, Ultimate = purple, Legendary = gold/orange, Soul = dark aqua, Heroic = red, Mastery = light purple/pink). *Flagged as open question.*
- **Obtaining books:** primarily the **Enchanter** (XP); also crates, GKits/VKits, Envoys, KOTH, Outposts, bosses, and other events. Special-tier books came from **events** specifically. [Confirmed]

### 1.2 Book identification / "unopened book" mechanic

- Enchanter sells an **unopened (tier) book**. Right-clicking **redeems** it into a **randomized opened book** with: a random **enchantment** from that tier's pool, a random **level**, a random **success rate %**, and a random **destroy rate %**. [Confirmed, CosmicPE wiki]
- **Quick Redeem:** sneak-redeem opens a whole stack at once (later-era QoL, added 2020-07-31 on CosmicPE). [Confirmed]
- Ranges per tier (the exact min–max success/destroy numbers per tier) are **[Unverified]** in surviving text — this is a build-critical open question. Community memory: higher tiers roll lower typical success and higher destroy, making "primal"/magic dust and randomization scrolls valuable. *Flag prominently.*

### 1.3 Application logic (verified precisely)

This is the mechanic the user most needs correct. From the CosmicPE wiki (same system): [Confirmed]

- When a book is dragged onto a legal item, it is a **single roll** against the book's stated **success rate**.
  - **Success:** enchant applies; book consumed.
  - **Failure:** the book is consumed, and then the **destroy rate** determines whether the item is destroyed. So the outcomes are effectively: **(a) success**, **(b) failure + item destroyed**, **(c) failure + item survives (book lost, item intact)**. Outcome (c) — "neither success nor destroy" — **does exist**.
  - Because success% and destroy% are **independent numbers**, they can **each** be anything 0–100 and their sum can exceed 100 or fall below 100; they are not required to total 100. [Confirmed — they are two separate stats on the book]
- **"Destroy" deletes the item** (it is not converted to dust). Per the CosmicPE wiki verbatim: *"In the event an item having masks or item mods attached is destroyed, they will be retained and given back to player in their /collect inventory."* [Confirmed]
- **White Scroll** protects against the *next* failed destroy (consumed instead of the item). [Confirmed]
- **Rank perk mitigation (later era):** high ranks had e.g. "10% chance to not consume book on apply" and "10% chance to not destroy item" [Confirmed, CosmicPE Tier-5/Heroic perks].
- **Upgrading:** applying a book of the same enchant at a **higher level** upgrades it. Enchants **cannot be downgraded or doubled** on one item. [Confirmed]

### 1.4 Enchant slot limits & Orbs

- Items have a cap on the number of custom enchants. **Enchantment Orbs** (Armor Orb / Weapon Orb) raise the cap. Orbs range **[10] – [16]** enchant slots; each orb has a % success rate to apply. Lower ranks have a lower applicable/usable enchant limit. [Confirmed, CosmicPE]
- Later era added **"14 Lore Enchantment Orbs"** and lore-count display via Transmog. [Confirmed, 2020-07-31]

### 1.5 Full enchant catalog

The definitive surviving list is the **AdvancedEnchantments "Cosmic Enchants (200+)"** replica and the CosmicPE enchant table. Below is the **classic-era-relevant primary catalog** grouped by tier with applicable item, max level, and effect. **[Likely]** for exact per-level numbers (replica may use its own defaults), **[Confirmed]** for names/tiers/effects where both sources agree.

**Simple** — Aquatic (Helmet, permanent water breathing), Auto Smelt (Pick, ores smelt on mine), Confusion (Axe, nausea), Etheral (Sword, haste on mob kill), Experience (Tools, mining XP), Haste (Tools, faster swing), Insomnia (Sword L7, slowness+confusion), Lightning (Bow, strike lightning), Oxygenate (Pick, refill air underwater), Thundering Blow (Sword, smite), Glowing (Helmet, night vision), Decapitation/Headless (Axe/Sword, head drop), Obliterate (Sword L5, extreme KB), Epicness (Sword, particles/sound), Shuffle (Armor, shuffle enemy hotbar).

**Unique** — Lifebloom (Legs L5, heal allies on death), Famine (Weapon L4, hunger), Berserk (Axe L5, strength+fatigue), Ward (Armor L4, absorb+heal), Curse (Chest L5, buffs at low HP), EnderShift (Helmet/Boots L3, speed/health at low HP), Explosive (Bow L5, explosive arrows), Featherweight (Sword, haste burst), Molten (Armor L4, ignite attacker), Ravenous (Axe L4, regain hunger in combat), Commander (Armor L5, ally haste), Self Destruct (Armor L3, TNT at near-death), Telepathy (Tools L4, auto-pickup), Sustain (Armor L4, hunger on hit), Skill Swipe (Sword L5, steal EXP), Plague Carrier (Legs L8, creepers+debuffs at near-death), Virus (Bow L4, amplify wither/poison).

**Elite** — Anti Gravity (Boots, super jump), Ender/Nether Slayer (Weapons L5), Reaper (Axe L4, wither+blindness), Blind (Sword L3), Shackle (Sword L3, no KB to mobs), Cactus (Armor L2, thorns w/o durability loss), Execute (Sword L7, low-HP damage buff), Frozen (Armor L3, slow attacker), Paralyze (Sword L4), Poison/Poisoned (Weapon/Armor), Reforged (Weapon L10, durability protect), Snare (Bow L4), Springs (Boots L3, jump boost), Stormcaller (Armor L4, lightning on attacker), Demonforged (Sword L4, extra enemy-armor durability damage), Trap (Sword L3, buffed slow), Undead Ruse (Boots L10, zombie hordes when hit), Venom (Bow L3), Voodoo (Armor L6, weakness), Wither (Armor L5), Smoke Bomb (Helmet L8), Infernal (Bow L3, explosive fire), Pummel (Axe L3, slow), Shockwave (Chest L5, push attacker at low HP), Vampire (Sword L3, heal after strike), Farcast (Bow L5, KB melee attackers), Greatsword (Sword L5, vs bow users), Hardened (Armor L3, less durability damage), Rocket Escape (Boots L3, launch at low HP), Trickster (Armor L8, teleport behind attacker), Hijack (Bow L4, convert enemy guardians).

**Ultimate** — Pickpocket (Weapon L3, steal money), Distance (Weapon L4), Reinforced (Armor L4, less damage from behind), Cleave (Axe L7, AoE), Angelic (Armor L5, heal over time when damaged), Arrow Lifesteal (Bow L5), Arrow Deflect (Armor L4), Arrow Break (Axe L6), Assassin (Sword L5, up to 1.25x close-range, less past 2 blocks), Blessed (Axe L4, remove debuffs), Corrupt (Axe L4, DoT), Ragdoll (Armor L4, pushed back when damaged), Block (Sword L3, redirect attack), Detonate (Pick L9, 3×3 break), Dodge (Armor L5), Enrage (Sword L3, more damage low HP), Guardians (Armor L10, iron golems), Ice Aspect (Sword L3), Implants (Helmet L3, passive heal+hunger), Obsidianshield (Armor, permanent fire res), Demonic (Armor L3, remove enemy fire res), Piercing (Bow L5), Marksman (Armor L4, bow damage), Disappear (Armor L4, invis at low HP), Disintegrate (Sword L4, double armor durability damage), Dominate (Sword L4, weaken enemy), Arsonist (Sword L3, more damage while on fire), Ender Walker (Boots L5), Eagle Eye (Bow L5, armor durability damage), Annihilate (Axe L6), Heavy (Armor L5, −2%/lvl bow damage, stackable), Hellfire (Bow L5, fireballs), Longbow (Bow L4), Tank (Armor L4, −1.85%/lvl axe damage, stackable), Unfocus (Bow L5, −50% enemy bow damage), Valor (Armor L5, −22.5% sword damage while holding sword, stackable), Pacify (Bow L4), Metaphysical (Boots L4, slowness immunity ~90%), Creeper Armor (Armor L3, explosion immunity), Spirits (Armor L10, healing blazes), Bleed (Axe L6, slow stacks).

**Legendary** — Kill Aura (Sword L5), Lava Walker (Boots), Impale (Bow L4, huge damage + Slowness V), Protection (Armor L5, heal/buff allies), Torrent (Boots L4, more damage in water), Judgement (Armor L5), Surprise (Armor L4, teleport behind), Stun (Weapon L3), Unholy (Armor L5), Quiver (Boots L6, fling attackers), Fat (Chest L6, damage negation + absorption), Hex (Axe L4, reflect % outgoing damage), Barbarian (Axe L4, vs axe users), Clarity (Armor L3, blindness immunity), Deathbringer (Armor L3, chance double damage), Double Strike (Sword L3, strike twice — all enchants can re-proc), Drunk (Helmet L4), Enlighted (Armor L3, heal while damaged), Gears (Boots L3, speed), Inquisitive (Sword L4, mob EXP), Inversion (Sword L4, block+heal 1–5 HP), Lifesteal (Sword L5), Overload (Armor L3, permanent extra hearts), Rage (Weapon L6, combo damage), Silence (Sword L4, stop enemy enchant activation), Armored (Armor L4, −1.85%/lvl sword damage, stackable), Exterminator (Legs L3, disable enemy Undead Ruse/Guardians/Spirits), Blacksmith (Axe L5, heal armor durability but −50% that hit), Abiding (Tools, unbreakable), Devour (Axe L4, vs bleeding targets), Diminish (Chest L6, cap next hit), Disarmor (Sword L8, remove enemy armor piece at low HP), Death God (Helmet L3, heal instead of dying), Insanity (Axe L8, vs sword users), Sniper (Bow L5, headshots up to 3.5x), Destruction (Helmet L5, AoE debuff).

**Soul (later era)** — Rogue (Axe L3, up to 2.0x from behind), Sabotage (Sword L5, block enemy Rocket Escape), Natures Wrath (Armor L4, AoE freeze — **75 souls/use**), Phoenix (Armor L3, lethal hit heals to full — **500 souls/use**, minutes-long cooldown), Teleblock (Bow L5, block enderpearls up to 20s + strip up to 15 pearls), Soul Trap (Weapon L3, disable enemy soul enchants for level×4s — **2 souls/sec**), Immortal (Armor L4, no armor durability loss — **5 souls/use**), Divine Immolation (Sword L4, AoE fire — **75 souls/use**), Paradox (Sword L5, AoE ally heal). [Confirmed names/mechanics; some soul costs [Likely]]

**Heroic (later era)** — each requires the max-level base enchant, then removes it: Heroic Insanity (Insanity VIII), Mega Heavy (Heavy V), Bewitched Hex (Hex IV), Mighty Cleave (Cleave VII), Mighty Cactus (Cactus II), Guided Rocket Escape (Rocket Escape III), Soul Hardened (Hardened III), Polymorphic Metaphysical (Metaphysical IV), Soulbound (keep item on death), Reinforced Tank (Tank IV), Epidemic Carrier (Plague Carrier VIII), Godly Overload (Overload III), Reflective Block (Block III), Master Inquisitive (Inquisitive IV), Planetary Deathbringer (Deathbringer III, up to 2.5x), Divine Enlighted (Enlighted III), Lethal Sniper (Sniper V, up to 4.5x), Atomic Detonate (Detonate IX, 7×7), Titan Trap (Trap III), Bidirectional Teleportation, Master Blacksmith (Blacksmith V), Vengeful Diminish (Diminish VI), Alien Implants (Implants III), Ethereal Dodge (Dodge V), Paladin Armored (Armored IV), Demonic Lifesteal (Lifesteal V), Deep Bleed (Bleed VI), Shadow Assassin (Assassin V, up to 1.875x). [Confirmed]

**Mastery (latest era)** — SoulGrind, Neutralize, Halloweenify, Mark of the Beast, Horrify, Chain Lifesteal, Poltergeist (immune to Nature's Wrath snare), Feign Death, Soul Siphon, Death Pact, Web Walker. [Confirmed]

*(A far larger modern catalog — Absolute Domination, Aegis, Anti Gank, Blackout, Demonic Gateway, Rot and Decay, Tombstone, Soul Tether, etc. — appears in the current CosmicPE list; these are **post-classic** and belong to late phases. Full effect text is preserved in the CosmicPE enchant table for reference.)*

### 1.6 Interactions / counters / meta

Confirmed counter-relationships to implement (these define the meta): [Confirmed]

- **Silence / Neutralize** stop enemy enchant activations; **Solitude/Perfect Solitude** amplify Silence.
- **Metaphysical / Polymorphic Metaphysical / Poltergeist** counter slow effects (Trap, Snare, Pummel, Ice Aspect, Nature's Wrath).
- **Sticky** counters **Disarmor / Deadly Disarmor**.
- **Bleed → Devour → Blood Lust / Deep Bleed** is a combo chain.
- **Tank/Reinforced Tank** (axe), **Armored/Paladin Armored** (sword), **Heavy/Mega Heavy** (bow), **Valor/Martyr Valor** (sword while holding sword) are the **stackable** damage-reduction enchants — the backbone of a defensive "god set."
- **Exterminator** disables summon enchants (Undead Ruse, Guardians, Spirits).
- **Soul Trap** disables enemy soul enchants; **Soul Hardened** resists it.
- **Enchant Reflect / Heroic Enchant Reflect** bounce offensive procs back.

**Set bonuses / armor sets:** the classic era had no set-bonus system; **Armor Sets/Crystals** (God sets, specialty sets like Phantom, Yeti, Ranger, Supreme) are a **later-era** addition layered on top of enchants. *(Build in a late phase.)*

---

## Section 2 — Item Modifiers & Consumables

[Confirmed from CosmicPE Utility Items unless tagged]

| Item | Effect | Notes / numbers |
|---|---|---|
| **Magic Dust** (a.k.a. **Primal Dust** if ≥10%) | Applied to a book to raise its **success rate** by a pre-set % | "Primal" is just the ≥10% visual variant |
| **Secret Dust** | Right-click to reveal into Magic Dust of a random % | From tinkering gems |
| **White Scroll** | Protects item from the next failed destroy (consumed instead) | |
| **Holy White Scroll** | 100% keep-on-death; **Semi-Corrupts** item; max **3** before item is **Corrupted** (no more HWS) | Negated by Phantom set, Monopoly mask, some pets/mods |
| **Holy Water** | Increases how many Holy White Scrolls a semi-corrupted item accepts | |
| **Black Scroll** | Removes a **random** enchant → converts to a book with the scroll's success% and **100% destroy** | Won't extract Heroic/Mastery |
| **Heroic Black Scroll** | Same, but can extract Heroic; success% is a **range x–y** on the item | |
| **Godly Black Scroll** | Removes a **specific** enchant; success% is a range | |
| **Transmog Scroll** | Sorts enchants by rarity, adds lore count `[12]` to name | |
| **Godly Transmog Scroll** | Lets you **custom-order** enchants | Added 2020-07-31 |
| **Randomization Scroll** (per tier) | Rerolls a book's success/destroy rates | Godly version works on all tiers |
| **Enchantment Orb** (Armor/Weapon) | +enchant-slot cap, **[10]–[16]** | % success to apply |
| **Item Nametag / Item Lore Crystal** | Custom name / custom lore line (colored variant exists) | |
| **Soul Gem** | Enables Soul mode (needed for Soul enchants/trinkets); `/splitsouls`, stackable | |
| **Soul Tracker** (per tier) | Harvests souls per kill: Simple 5, Unique 10, Elite 15, Ultimate 20, Legendary 25, Godly 40 souls/kill | `/withdrawsouls` |
| **Alchemy Shard** | Lets Soul/Heroic/Mastery books be combined at Alchemist; on other books, −50% Alchemist XP cost | |
| **Mastery Shard** | Lets a book apply to a Mastery kit | |
| **Armor Socket / Multi-Armor Socket** | Adds 1–3 mod slots to chest/legs for Amulet/Backpack/Belt Item Mods | Later era |
| **Omni Crystal** | Converts gear to Omni (usable with any armor set) | Later era |
| **Heroic Upgrade** | Converts diamond set piece → heroic counterpart | Later era |
| **Titan Attribute Booster / Extractor** | +1 level to a weapon's Titan Attribute / extract it (100% success) | 1 booster / 24h; later era |

**Souls** are a currency for Soul enchants/trinkets, harvested via Soul Trackers or Soul Siphon, stored in Soul Gems.

---

## Section 3 — NPCs / Stations / Shops

[Confirmed from CosmicPE unless tagged]

- **Enchanter** (`/enchanter`, at spawn): sells unopened tier books for **XP**, in **1× / 8× / 16×** bulk. Per the CosmicPE wiki verbatim: *"He offers Simple, Unique, Elite, Ultimate, and Legendary custom enchantment books in exchange for player XP… Players can exchange XP for enchantment books in increments of 1x, 8x, and 16x."* Confirmed single-book prices (CosmicPE; treat classic Java as [Likely] similar-ratio): Simple **400 XP**, Unique **800**, Elite **2,500**, Ultimate **5,000**, Legendary **25,000**; bulk tiers: 8× Legendary = 200,000, 16× Legendary = 400,000 (roughly linear with a discount at Elite+). *An in-spawn enchant table may add a 1.5× spawn tax (confirmed on Cosmic Sky; [Likely] on Java).*
- **Tinkerer** (`/tinkerer`): trade unwanted **books → XP**, and **gems → Secret Dust** (which can reveal into Magic Dust). This is the primary "recycle bad rolls" sink and a noted money-maker. Exact per-book XP yields are **[Unverified]**.
- **Alchemist** (`/alchemist`): combine **two identical books (same enchant+level) → +1 level** book (costs XP; not past max level; primary-tier only unless an Alchemy Shard is applied). Also **combine two Magic Dusts of the same tier → next-tier dust** (primary tiers only, excluding Legendary). [Confirmed]
- **Blacksmith / repair:** `/fix`, `/fix all` were **rank perks** (with cooldowns, e.g. Interstellar `/fix all` 5m). Soul Anvil trinket heals 10% armor durability for 100 souls (later era). [Confirmed]
- **Slot machine / Cosmo-Slot Bot:** a gambling NPC/GUI existed (later formalized as Slot Bot Tickets on Cosmic Sky). Exact prize tables **[Unverified]** for Java. *(This is also the system flagged in 2024 community "rigged crates/slots" controversy — implement transparently.)*
- **Shop** (`/shop`): buy/sell blocks, gear, and **spawners**. Mystery Monster Spawner = **$3,000,000** for a random spawner; high-demand spawners locked behind loot pools. (Full spawner price/value table in Section 5.) [Confirmed CosmicPE]
- **Auction House** (`/ah`): player listings; listing **count is a rank perk** (e.g., 6→8→12 slots up the ladder). A **Black AH** exists in later era. [Confirmed]

---

## Section 4 — Kits & Gear

- **Starter kit** (`/kit starter`): daily; food, building blocks, a special chest (small spawner chance). Every player starts with a small cash grant (CosmicPE: $1,000) and default rank. [Confirmed CosmicPE]
- **Rank kits** (`/kit <rank>`): each donor tier has a daily/weekly kit; e.g. weekly **Heroic** kit "boasts OP loot + one bonus item." [Confirmed]
- **Grandmaster Kits (GKits):** permanent unlocks with a **global cooldown** (community cites 3–4 days; the global-across-planets behavior is [Confirmed], the exact 3–4 day figure is [Unverified]). Confirmed GKit **names**: **Cosmo, Galactical, Spawner, Grinder, Ironman, Endgame, Enchantment/Enchanter, Paladin, Guardian, Destroyer, Butcher, Spooky** (seasonal). GKits scaled in level/tier with map age, and GKit/VKit "Gear Bundles" were added to loot tables/Envoys. Account listings cite "16 GKits on Jungle," implying ~a dozen+ per planet. GKit **contents** (exact item/enchant lists) are **[Unverified]** — best recovered from the SpigotMC "ALL Cosmic Kits" replica config (resource 77407, `gkits/*.yml`) or a Wayback capture of the Cosmic forum "ALL /gkits Re-released" thread. **[Confirmed names; contents unverified]**
- **VKits** (voucher kits): per-planet, non-global cooldown, lower tier than GKits. [Likely]
- **Mastery armor sets / God sets:** later-era endgame gear assembled with Crystals/Orbs/Heroic upgrades.

---

## Section 5 — Economy & Progression

[Confirmed CosmicPE spawner economy; classic Java [Likely] similar]

**Spawner mechanics:** activate at ~5-chunk render distance; mobs **stack per type per chunk**; killing depletes the stack by 1 and respawns; melee kills give player XP, all kills drop loot. Spawners stack up to **40 per block** (later era; classic cap was **10**), free to mine with Silk Touch for 5 min then a 10% valuation fine.

**Spawner value / price table (CosmicPE, representative):**

| Mob | Shop value | Loot |
|---|---|---|
| Bat | $3,000,000 | — |
| Blaze | $390,000 | Blaze Rod 50% |
| Creeper | $390,000 | Gunpowder |
| Enderman | $390,000 | Ender Pearl 50% |
| Zombie Pigman | $390,000 | Gold |
| Iron Golem | $2,000,000 | Iron 3–5 |
| Magma Cube | $4,000,000 | Diamond 1–3 |
| Ghast | $5,000,000 | Emerald 1–3 |
| Slime | $1,300,000 | Slimeball |
| Cow | $162,000 | Beef/steak/leather |
| Skeleton/Zombie | $115,000 | — |
| Snowman | $3,000,000 | Snowman Soul 5% ($3,600 each) |

**F-Top:** `/f top` ranked factions by **spawner-based value** (value-based /f top). Top factions (usually **#1–#3** or #1–#10) won **real-money/store prizes** each season; payout schedule was per-map/weekly plus an end-of-map reward. Exact CosmicPvP payout amounts are **[Unverified]** (varied by season). Money sinks: spawners, ranks, claims/premium claims, banners, Alchemist/Tinkerer XP, gambling. Money sources: spawner grinding, selling loot, raiding, events, PvP (Pickpocket).

**Season structure:** planets **reset** on their own cadence (new "maps," e.g. "Jungle Map #12"), with reset hype driving the loop. XP is farmed from spawners and is the **enchant currency**, tying combat progression to the grind.

---

## Section 6 — Factions System

[Confirmed CosmicPE; classic Java [Likely] except where noted]

- **Creation:** `/f create`, `/f invite`, `/f join`. Member caps and ally caps varied **by planet** (see Planet Variance). Classic Cosmic launched allowing **50 `/pv`** (player vaults) — later reduced. [Confirmed via Quizlet flashcard sourced from Cosmic]
- **Power system:** power is per-member; max **150** per player, regen **+1/min** (even offline), **−10 on wilderness death**, floor **−20**. Faction power = sum of members; a "Faction Power Boost" upgrade adds up to **+100**. Power determines claimable chunks; being over-power-on-land enables **overclaiming**. [Confirmed CosmicPE]
- **Claims:** four types — **core** (first claim, sets base, can't be unclaimed alone), **base** (connected), **raid** (disconnected, auto-unclaims after **8h**), **premium** (≤3 chunks from warzone, weekly paid rental). [Confirmed CosmicPE]
- **Roles/permissions:** granular `/f label` permission system (allow/deny/default per chunk/role/player). [Confirmed CosmicPE]
- **Faction fly, `/f home`, faction chat, `/f focus`, `/f assist` (banners):** all present. **Faction Banner** = limited-time faction TP: `/f banner` costs $100,000 + 500 souls, lasts 10s after placing, `/f assist` to travel. [Confirmed later era]
- **Warzone/safezone & world border:** planets advertised **400 slots**; warzone is a central PvP ring around spawn; classic buffer around bases was "320 blocks in each direction from the exterior wall." [Confirmed later era]
- **Raiding rules (cannons):** the surviving CosmicPvP raiding-rules text is offline, but the enforced model (mirrored across Cosmic and its clones) included: a defined **buffer** of claims around the base; **regen-wall limits**; **no patching during an active raid** (raid = from first cannon shot until 10 min after last shot); **no gen-bucket/printer/water patching mid-raid**; **1 wall per button press** (no multi-wall one-shots); **no TNT-array/auto cannons**; **minimum spacing between bases**; **no spreading F-Top value across multiple bases**. Exact CosmicPvP numbers are **[Unverified]** — treat clone rulesets (CosmicReborn, LemonCloud, Minecadia) as *pattern, not confirmed Cosmic values.* **Custom TNT** (Chemical/Lethal/Gigantic) existed for cannoning. [Confirmed TNT types via community wiki]

---

## Section 7 — World Events

[Confirmed CosmicPE unless tagged]

- **Envoys (Cosmic Envoys):** periodic crate drops across the warzone (~every 30 min in classic era per community memory); players fight over loot crates of varying tiers containing gear, dusts, books, GKit/VKit bundles, ranks. Broadcast in chat; capturing envoys/LMS gives **rank quests**. [Confirmed]
- **KOTH (King of the Hill):** daily; hold a control point ~**15 minutes**; leaving resets capture; on completion the controller is **combat-tagged 15s** and enemies can still steal it; reward = **KOTH Lootbag** (`/koth loot`). Sieges every 3 min spawn buffed "KOTH Space Demon" zombies. In-arena rules: Ragdoll/Shockwave/Rocket Escape disabled, knockback halved, explosive snowballs banned, 3-min death-lockout, banned after 2 deaths. [Confirmed]
- **Outposts:** 24/7 contestable KOTH-style PvP/PvE station (`/warp outpost`); capture at **+0.2% per 2s per capturer**; **8-minute deathban** on death in/around it; a faction holds **1 outpost at a time**; winners get a sell room and passive rewards while held. [Confirmed]
- **Conquest:** later-era multi-point territorial event. Exact mechanics **[Unverified]** for Java.
- **Bosses / Boss Eggs:** custom boss mobs with HP/abilities/drop tables; **Boss Slayer** enchant deals bonus boss damage. Exact classic boss roster/HP **[Unverified]**.
- **Others:** LMS (Last Man Standing), Strongholds, The End event, Meteors, Fallen Heroes (kit event), Supply Crates, Lucky Blocks, seasonal (Halloween "Spooky," Christmas "Interstellar/Evil Elves") events. [Confirmed existence; details vary by era]

---

## Section 8 — Combat & PvP Rules

- **1.8-style combat:** no 1.9 attack cooldown (use OldCombatMechanics or a 1.8 server). CosmicPvP ran **Minecraft 1.8** (later allowed 1.7–1.8.x clients). [Confirmed]
- **Combat tag:** logging out in combat spawns an **NPC stand-in** mimicking your alignment; kill it before you relog and it takes your place; survive the timer and it vanishes. NPCs can be healed by splash health potions. **Resilience** enchant buffs NPC HP. [Confirmed later era; classic era = instant-death on combat log, changed later]
- **Gapples/potions:** debuff-heavy 1.8 pot meta; **Nutrition** enchant lets you eat more gapples before Golden Apple Sickness. God-apple/gapple cooldown specifics **[Unverified]** for classic Java.
- **Enderpearls:** ~cooldown per 1.8 norms; **Teleblock** strips/blocks pearls; **Soul/Escapist Pearls** are soul-powered later-era variants.
- **Client rules (later era):** Cosmic/Lunar/Badlion clients allowed; fly-boost must resemble Cosmic Client's 3× fly; no minimaps with entities, no health/armor indicators, no X-ray. [Confirmed from clone ruleset mirroring Cosmic]
- **Spawn/warzone:** spawn is safezone (no PvP); warzone is open PvP with no claiming.

---

## Section 9 — Commands, Homes & Rank Perks

- **Rank ladder (structure):** **Trainee (default)** → **Tier 1 → 2 → 3 → 4 → 5** → **Heroic** → **Interstellar** (rare global top rank). Only Tiers 2, 4, 5 were webstore-purchasable in the base model; others via in-game rank quests (post-rewrite). Ranks are **per-account, permanent, non-transferable**. [Confirmed structure via CosmicPE]
- **Per-planet top-rank names** (cosmetic rank-5 names) [Confirmed via Cosmic flashcards]: Ice=**Glacier**, Jungle=**Tropico**, Void=**Executioner**, Dungeon=**Hero**, Pirate=**Captain**, Magic=**Archmage**, Dragon=**Dragon**, Cosmic=**General**, Castle=**King**, Forgotten=**Pharoh**, Dream=**Fantasy**, Castaway=**Survivor**, Demon=**Demonlord**, Monster=**Witcher**, Spirit=**Guardian**, Fallout=**Vault**, Goodness=**FlowerKing**, Pleb=**CactusKing**.
- **Representative high-rank perks** (CosmicPE Tier-5/Heroic, [Confirmed] for that server, [Likely] pattern for Java): `/echest`, `/pv 1–18`, 8 `/ah` listings, Skeleton Horse `/disguise`, weekly Heroic `/kit`, **4.0× mcMMO multiplier**, larger `/f map`, `/feed` others, 5-min outpost deathban, **+5% sell**, **10% chance not to consume book / 10% chance not to destroy item** on enchant apply, **+25% spawner XP**, permanent 1.2× vanilla XP. Interstellar adds `/fix all` (5m cd), +10% book success, 12 `/ah` listings, `/warp interstellar` (tax-free `/cf` zone). [Confirmed CosmicPE 2020 changelog]
- **Prices:** CosmicPE store shows Tier 2 = **$9.99**, Tier 4 = **$19.99**, Tier 5 = **$29.99** — [Confirmed for CosmicPE only; NOT CosmicPvP Java]. CosmicPvP Java official USD prices are **[Unverified]**; Interstellar resold on secondary market at **~$150+**. *Flag as open question.*
- **Cosmic-specific commands:** `/enchanter`, `/tinkerer`, `/alchemist`, `/gkit`, `/vkit`, `/kit`, `/f top`, `/ah`, `/pv`, `/collect`, `/wild`, `/fix`, `/bless`, `/ces` (enchant search), `/smite`, `/withdrawsouls`, `/splitsouls`, `/f banner`, `/f assist`.
- **Crates/keys:** monthly crates + event keys (Overlord Lootbox, Envoy crates, KOTH Lootbag, Slot Bot tickets). Exact contents **[Unverified]**.

---

## Section 10 — UX & Presentation

- **Custom lore format** is central to the "feel": items show custom enchants as **color-coded lore lines sorted by rarity**, with a **`[n]` lore-count** in the item name (via Transmog). Books display **enchant name + level + Success% + Destroy%** and (later) **Max Level** in lore. Reproduce this exactly.
- **Scoreboard:** faction map, balance, F-Top position, XP, and customizable stats (TPS/ping/durability in later era).
- **NametagClick** system (a Cosmic-origin plugin) for click-to-interact nametags. [Confirmed — replicated on SpigotMC]
- **Planet selector / hub:** proxy hub with a GUI planet selector; each planet a separate backend server.
- **Chat:** rank-colored prefixes; global chat color perks (e.g., Aqua for Interstellar); item-linking (`[ITEM]`, `/senditem`).

---

## Section 11 — Server Infrastructure (publicly known)

- **Architecture:** BungeeCord-style proxy fronting **per-planet backend servers** (each a shard), advertised at **400 slots/planet**; network peak **over 6,000 concurrent** (late 2016). [Confirmed]
- **Lag mitigations:** aggressive **mob-stacking** (one entity per type per chunk, stacking to 32,767), spawner activation gated on player render distance, `/smite` to clear stacks, entity/hopper limits. These are essential at 1,000+ players. [Confirmed CosmicPE]
- **Custom client:** an optional "Cosmic Client" (Lunar/Badlion-style) with a 3× fly boost. [Confirmed]
- **Scale guidance:** at **~500 concurrent** a single beefy box per 1–2 planets suffices; at **~1,000–2,000** split each planet onto its own instance and separate the proxy, DB (MySQL/Redis for cross-server AH/F-Top), and event servers, with async spawner/economy processing. [Opinion — see Modernization]

---

## Dependency Map

- **Enchant Books** ← Enchanter (XP) ← Spawners (XP+money) ← Factions claims (safe grinding)
- **Book application gamble** → needs White Scroll (destroy protection), Magic Dust (success%), Randomization Scroll (reroll), Orbs (slots)
- **Tinkerer** depends on Enchant tiers + gems → outputs XP + Secret Dust → Magic Dust
- **Alchemist** depends on duplicate books/dusts + Alchemy Shard → higher levels/tiers
- **Soul enchants** depend on Souls ← Soul Trackers/Soul Siphon ← PvP kills; require Soul Gem
- **Heroic enchants** depend on max-level primary enchant + Heroic Upgrade/Black Scroll
- **Mastery enchants** depend on Mastery Shard; block Armor Crystals
- **Combat tag** depends on Factions claims + NPC logger + Commands
- **F-Top** depends on Spawner valuation → real-money prize schedule
- **Events (Envoy/KOTH/Outpost)** depend on warzone geometry + loot tables + combat-tag rules → feed rank quests + books + gear
- **Masks/Trinkets/Pets/Crystals** sit on top of the enchant/armor layer (late phases)

---

## Era Roadmap (suggested build order)

**Phase 1 — Classic Core (2014–2016):**
5 primary tiers (Simple→Legendary), Enchanter (XP books), book gamble (success/destroy/neither), White Scroll, basic Magic Dust, Tinkerer, Alchemist, Enchant Orbs; Factions (power/claims/overclaim), spawner economy + value-based F-Top with real prizes, `/f top`; planets with per-planet top ranks; Envoys + KOTH; GKits (Cosmo, Grinder, Spawner, Enchanter, etc.); rank ladder Tier 1–5; 1.8 combat + basic combat tag; custom TNT + cannon/raid rules; AH; custom lore/scoreboard/nametag UX.

**Phase 2 — Soul & Events layer (~2016–2018):**
Soul tier + Souls/Soul Gems/Soul Trackers; Outposts; Conquest; Bosses/Boss Eggs; Black Scroll, Holy White Scroll, Transmog, Randomization Scrolls; NPC combat-logger; Faction Banners; Heroic kit; Slot Bot.

**Phase 3 — Heroic & Meta gear (~2018–2020):**
Heroic tier (+ Heroic Upgrade/Heroic Black Scroll); Armor Sets/Crystals + specialty sets (Phantom, Yeti, Ranger, Supreme); Masks; enchant stacking; expanded Orbs [10]–[16]; larger enchant catalog.

**Phase 4 — Mastery & modern systems (2020+):**
Mastery tier + Mastery/Alchemy Shards + Godly Transmog; Trinkets (EMP Pulse, Haunted Tombstone, Soul Anvil, etc.); Inventory Pets; Item Mods (Amulet/Backpack/Belt + Armor Sockets); Titan Attributes; Omni Crystals; Quests/Milestones/Duels/Arcade; stackable spawners to 40; points-based F-Top option.

---

## Planet Variance Log

- **Existence:** classic launch had **Alien, Volcano, Ice, Jungle** planets (2014); the roster grew to many themed planets over time (Ice, Jungle, Void, Dungeon, Pirate, Magic, Dragon, Cosmic, Castle, Forgotten, Dream, Castaway, Demon, Monster, Spirit, Fallout, Goodness, Pleb, Lava, and more). **Important:** the user's requested list (Blaze/Titan/Jupiter/Pluto/Mars/Saturn/Neptune/Venus/Mercury/Uranus/Earth/Atlantis/Sun/Moon) does **not** match confirmed CosmicPvP planet names, which were themed (Ice/Jungle/Magic/etc.), not the solar-system list. Treat the solar-system names as **[Unverified]** / likely from a different server or a mis-recollection. *Flag as open question.*
- **Per-planet top rank names:** see Section 9 (Glacier/Tropico/Witcher/Archmage/etc.) [Confirmed].
- **Member/ally caps, TNT strength, spawner rates, map size:** varied per planet and per map/season; specific per-planet numbers are **[Unverified]**. Classic launch allowed **50 `/pv`** network-wide, later reduced. Some planets were "legacy/throwback" style. GKit cooldowns were **global across planets**; VKits were per-planet.
- **Reset cadence differed by planet** (staggered map launches like "Jungle Map #12").

---

## Modernization Notes (opinion — NOT Cosmic facts)

1. **Enchant gamble:** *Opinion* — keep the success/destroy/neither thrill but add a visible **pity/floor** (guaranteed protection after N destroys) and cap Legendary destroy% lower than classic; sell White Scrolls in-game cheaply so newer players don't rage-quit. Show odds transparently (the 2024 "rigged crates" controversy is a cautionary tale — avoid opaque gambling, especially for minors, and keep it EULA-compliant).
2. **Economy/F-Top:** *Opinion* — offer a **points-based F-Top** option (CosmicPE moved to this) to reduce lag from millions of stacked spawners; cap spawner stacks (10–40) and auto-sell chests server-side to cut hopper/entity load.
3. **Raiding time-sink:** *Opinion* — provide **built-in wall-regen and a raid timer plugin** with clear "active raid" windows so players don't need to no-life patch; shorter, scheduled raid windows respect adults with jobs.
4. **Early progression:** *Opinion* — faster ramp to a usable set (rank quests via Envoy/LMS, as Cosmic later did, instead of pay-only ranks) widens the top of the funnel.
5. **Anti-grief/QoL:** *Opinion* — `/collect` for destroyed-item mods, combat-logger NPCs, and `/f focus` are all worth copying from later Cosmic; add faction analytics dashboards.
6. **Infra at 1k–2k:** *Opinion* — one backend per planet, Redis-backed cross-server AH/F-Top, async everything, Paper + regionised ticking; a custom optional client only if you can maintain EULA compliance.

---

## Open Questions (ranked by build impact)

1. **Exact per-tier success%/destroy% ranges** on freshly-rolled books (highest impact — defines the whole gamble). [Unverified]
2. **Tinkerer exact XP/dust yields** per book tier, and **Alchemist exact XP costs** per combine. [Unverified]
3. **GKit contents** (exact item/enchant lists per kit) and confirmed full GKit roster. [Names confirmed; contents unverified]
4. **Magic Dust % tiers** and how much each raises success. [Unverified — only "pre-set %, ≥10% = Primal" confirmed]
5. **Classic-era tier color codes / exact lore formatting strings.** [Unverified]
6. **CosmicPvP Java rank USD prices** and exact per-planet member/ally caps. [Unverified]
7. **F-Top real-money prize amounts and schedule** by season. [Unverified]
8. **Classic boss roster** (types, HP, drops) and Conquest mechanics. [Unverified]
9. **Confirmed planet list & solar-system-name discrepancy.** [Unverified]
10. **Classic gapple/enderpearl cooldowns and potion tuning.** [Unverified]

## Videos worth transcribing

- **"The Cosmic Tinkerer and Enchanter, BEST MONEY MAKER IN FACTIONS"** (YouTube, `_4RrXAlbSJU`) — Tinkerer/Enchanter XP-to-money rates.
- **PrestonPlayz "Cosmic Faction" series** (2014–2016, official; the **299-video PrestonPlayz playlist that amassed over 10 million views** per Grokipedia) — classic-era enchant tiers, GKits, planet UX hour-1/day-1 loop.
- **"PRIMAL DUST! CosmicPvP Pleb Planet Ep 17"** (`6dUpMlVqHtY`) — Magic/Primal Dust behavior.
- **"How TO Make Millions On CosmicPvP After Reset"** (`YbZ8Xbb4e1c`) and **"FASTEST WAY TO MAKE MONEY ON RESET S3 Ep.1"** (`40HjY3wnRE4`) — reset-day economy loop.
- Any **"CosmicPvP ALL ENCHANTS EXPLAINED"** guide video — per-level enchant numbers.
- **"ENCHANTMENT GKIT + OP BLAZE GRINDER"** (`0NOfnic1Cyc`) — GKit contents on camera.

## Source Index

- **CosmicPE Wiki** (wiki.cosmicpe.me): Enchanting, Utility Items, Monster Spawner, Cosmic Enchanter, Cosmic Alchemist, Factions Overview, Base Building, Outpost, KOTH, Player ranks, Getting Started — primary structured mirror of the mechanics.
- **AdvancedEnchantments "Cosmic Enchants (200+)"** (ae.advancedplugins.net) — full enchant names/tiers/effects replica.
- **Grokipedia "CosmicPvP"** — history, founders, peak concurrency (6,000+), shutdown date (Feb 5, 2023), $25M revenue, controversies.
- **CosmicPvP Forums** (forum.cosmicpvp.com, via search snippets) — enchant tiers threads, spawner value/F-Top threads, Paladin GKit, "ALL /gkits re-released."
- **@cosmicpvp Twitter/X** — GKit/VKit gear bundles, Interstellar rank, Masks, seasonal events.
- **Quizlet "Cosmic pvp" & "CosmicPvP Enchantment Quiz"** decks — per-planet top ranks, "50 /pv at launch," legendary enchant level/tier facts.
- **SpigotMC** — "GKits | EliteEnchantments … ALL Cosmic Kits & Fallen Heroes" (resource 77407), NametagClick (CosmicPvP), OldCombatMechanics.
- **4mula leaked gkit config** — GKit names (Cosmo/Galactical/Spawner/Grinder/Ironman/Endgame), global cooldown.
- **EpicNPC** account listings — per-planet ranks (Witcher/Archmage), GKit counts, Butcher/Spooky GKits, resale prices.
- **Clone rulesets** (CosmicReborn, LemonCloud, Minecadia, Mineage) — raiding/cannon rule *patterns* (labeled not-confirmed-on-Cosmic).

## Glossary

- **Book (unopened/opened):** tier voucher → rolled enchant with success/destroy%.
- **Success% / Destroy%:** independent roll stats; "neither" = book lost, item safe.
- **Primal/Magic Dust:** raises a book's success%.
- **White / Holy White / Black Scroll:** destroy-protect / keep-on-death / extract-enchant.
- **Orb:** raises enchant-slot cap.
- **Soul / Soul Gem / Soul mode:** currency + activation for Soul enchants/trinkets.
- **Heroic / Mastery:** special upgraded/endgame enchant tiers.
- **GKit / VKit:** grandmaster (global cooldown) / voucher (per-planet) kits.
- **F-Top:** faction leaderboard by spawner value; real prizes.
- **Envoy / KOTH / Outpost / Conquest:** world events.
- **Overclaim:** seizing land from an under-powered faction.
- **Corrupt / Semi-Corrupt:** Holy White Scroll states.
- **Transmog:** sorts/labels enchant lore.
