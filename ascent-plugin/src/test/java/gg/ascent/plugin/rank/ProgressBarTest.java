package gg.ascent.plugin.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProgressBarTest {

  @Test
  void fillsProportionally() {
    assertEquals("##--------", ProgressBar.render(0.2, 10, '#', '-'));
    assertEquals("----------", ProgressBar.render(0.0, 10, '#', '-'));
    assertEquals("##########", ProgressBar.render(1.0, 10, '#', '-'));
    assertEquals("#####-----", ProgressBar.render(0.5, 10, '#', '-'));
  }

  @Test
  void clampsOutOfRangeFractions() {
    assertEquals("##########", ProgressBar.render(7.0, 10, '#', '-'));
    assertEquals("----------", ProgressBar.render(-1.0, 10, '#', '-'));
  }
}
