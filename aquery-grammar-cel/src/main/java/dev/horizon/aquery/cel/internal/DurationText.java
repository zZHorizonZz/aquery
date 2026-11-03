package dev.horizon.aquery.cel.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DurationText(String text) {

  private static final Pattern PART = Pattern.compile("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(ns|us|µs|ms|s|m|h)");
  private static final Map<String, Long> NANOS = Map.of("h", 3_600_000_000_000L, "m", 60_000_000_000L, "s",
      1_000_000_000L, "ms", 1_000_000L, "us", 1_000L, "µs", 1_000L, "ns", 1L);

  public Duration duration() {
    boolean negative = text.startsWith("-");
    String magnitude = negative || text.startsWith("+") ? text.substring(1) : text;
    long nanos = magnitude.equals("0") ? 0 : nanos(magnitude);
    return Duration.ofNanos(negative ? -nanos : nanos);
  }

  private long nanos(String magnitude) {
    Matcher part = PART.matcher(magnitude);
    BigDecimal sum = BigDecimal.ZERO;
    int end = 0;
    while (end < magnitude.length() && part.region(end, magnitude.length()).lookingAt()) {
      sum = sum.add(new BigDecimal(part.group(1)).multiply(BigDecimal.valueOf(NANOS.get(part.group(2)))));
      end = part.end();
    }
    if (magnitude.isEmpty() || end != magnitude.length()) {
      throw new IllegalArgumentException("'" + text + "' is not a valid duration, expected e.g. \"1.5s\" or \"1h30m\"");
    }
    try {
      return sum.toBigInteger().longValueExact();
    } catch (ArithmeticException _) {
      throw new IllegalArgumentException("'" + text + "' is too long a duration, the nanoseconds must fit in 64 bits");
    }
  }
}
