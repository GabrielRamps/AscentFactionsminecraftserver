package gg.ascent.plugin.enchant;

import gg.ascent.api.enchant.ApplyResult;
import java.util.random.RandomGenerator;

/**
 * The one honest roll (PRD E3-S3): {@code r = random(0,100)}; {@code r < success} applies;
 * otherwise {@code random(0,100) < destroy} destroys (or spends the scroll); otherwise the book is
 * simply lost. Pure.
 */
public final class ApplyRoller {

  private ApplyRoller() {}

  public static ApplyResult roll(
      int success, int destroy, boolean protectedByScroll, RandomGenerator random) {
    if (random.nextInt(100) < success) {
      return ApplyResult.SUCCESS;
    }
    if (random.nextInt(100) < destroy) {
      return protectedByScroll ? ApplyResult.SCROLL_SAVED : ApplyResult.DESTROYED;
    }
    return ApplyResult.FAILED;
  }
}
