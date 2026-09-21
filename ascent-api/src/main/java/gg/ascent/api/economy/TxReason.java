package gg.ascent.api.economy;

/** Why money moved. Mirrors the {@code money_transactions.reason} enum in PRD §6.3. */
public enum TxReason {
  PAY,
  SELL,
  SHOP_BUY,
  SHOP_SELL,
  KIT,
  CONTRACT,
  EVENT,
  UPKEEP,
  VAULT_DEPOSIT,
  VAULT_WITHDRAW,
  ADMIN,
  TRADE,
  FACTION_CREATE
}
