package gg.ascent.plugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.ascent.api.economy.EconomyService;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void formatsWithGrouping() {
    assertEquals("$0", Money.format(0));
    assertEquals("$1,234,567", Money.format(1_234_567));
  }

  @Test
  void parsesPlainCommaDollarAndSuffixForms() {
    assertEquals(OptionalLong.of(500), Money.parse("500"));
    assertEquals(OptionalLong.of(1_234_567), Money.parse("1,234,567"));
    assertEquals(OptionalLong.of(42), Money.parse("$42"));
    assertEquals(OptionalLong.of(2_000), Money.parse("2k"));
    assertEquals(OptionalLong.of(3_000_000), Money.parse("3M"));
    assertEquals(OptionalLong.of(1_000_000_000), Money.parse("1b"));
  }

  @Test
  void rejectsZeroNegativesDecimalsAndOverflow() {
    assertTrue(Money.parse("0").isEmpty());
    assertTrue(Money.parse("-5").isEmpty());
    assertTrue(Money.parse("2.5").isEmpty());
    assertTrue(Money.parse("abc").isEmpty());
    assertTrue(Money.parse("").isEmpty());
    assertTrue(Money.parse("k").isEmpty());
    assertTrue(Money.parse("99999999999999999999").isEmpty());
    assertTrue(Money.parse(String.valueOf(EconomyService.MAX_AMOUNT + 1)).isEmpty());
    assertEquals(OptionalLong.of(EconomyService.MAX_AMOUNT), Money.parse("9007199254740992"));
  }

  @Test
  void vaultAmountsRoundDownToWholeDollars() {
    assertEquals(9, VaultEconomy.whole(9.99));
    assertEquals(0, VaultEconomy.whole(0.5));
    assertEquals(-1, VaultEconomy.whole(-1));
    assertEquals(-1, VaultEconomy.whole(Double.NaN));
    assertEquals(EconomyService.MAX_AMOUNT, VaultEconomy.whole(1e300));
  }
}
