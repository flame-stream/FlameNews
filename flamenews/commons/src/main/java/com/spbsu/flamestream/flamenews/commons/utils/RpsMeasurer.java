package com.spbsu.flamestream.flamenews.commons.utils;

import java.util.LongSummaryStatistics;

public class RpsMeasurer {
  private final LongSummaryStatistics statistics = new LongSummaryStatistics();
  private long prevRequestTs = -1;

  public void logRequest() {
    if (prevRequestTs != -1) {
      statistics.accept(System.nanoTime() - prevRequestTs);
    }
    prevRequestTs = System.nanoTime();
  }

  public double currentAverageRps() {
    if (statistics.getCount() >= 1) {
      return 1_000_000_000.0 / statistics.getAverage();
    } else {
      return Double.NaN;
    }
  }
}
