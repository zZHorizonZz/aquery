package dev.horizon.aquery.aip160;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.DoubleLiteral;
import dev.horizon.aquery.aip160.Filter.DurationLiteral;
import dev.horizon.aquery.aip160.Filter.IntLiteral;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Filter.TimestampLiteral;
import dev.horizon.aquery.aip160.Filter.Value;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the value that a client wrote, and gives the value that a backend keeps.
 *
 * <p>
 * AIP-160 specifies how to write literals. The rules depend on the quotes:
 *
 * <ul>
 * <li>Strings are in double quotes. A word without quotes can be a field reference. Thus a string backend
 * refuses such a word, and the message tells the client to add quotes.
 * <li>Booleans, integers, durations and enum values have no quotes. A {@code "true"} in quotes is a string, not
 * a boolean. Thus a boolean backend refuses it.
 * <li>An integer can have a minus sign, for example {@code -30}. A float is not an integer.
 * <li>A duration is a number with an {@code s} suffix, for example {@code 1.5s}.
 * <li>A timestamp is an RFC 3339 string in double quotes, for example {@code "2012-04-21T11:30:00-04:00"}.
 * </ul>
 *
 * <p>
 * CEL has typed literals. Each reader also accepts the CEL literal of its type: {@code true}, {@code 42},
 * {@code duration("1.5s")} and {@code timestamp("2012-04-21T11:30:00-04:00")}. An enum value is a CEL identifier
 * without quotes, as in AIP-160.
 *
 * <p>
 * Each reader gives the value in the type that the backend keeps. The backend then binds the value in the type
 * that the database expects.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/arg_parsers.go">LUCI aip160: arg_parsers.go (Coerce* constants, EnumDefinition)</a>
 */
public final class Args {

  private static final Pattern DURATION = Pattern.compile("([0-9]+)(?:\\.([0-9]{1,9}))?s");

  private static final Pattern TIMESTAMP = Pattern
      .compile("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})");

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private Args() {
  }

  /**
   * Reads the value as a string constant in double quotes.
   *
   * @param value the value of the restriction
   * @return the string without the quotes and with the escapes applied
   * @throws InvalidFilterException if the client did not write a string in quotes
   */
  public static String coerceToStringConstant(Value value) {
    return switch (value) {
      case StringLiteral string -> string.value();
      case Text text -> throw new InvalidFilterException(
          "expected a quoted (\") string literal but got possible field reference '%s'; did you mean to wrap the value in quotes?",
          text.text());
      default -> throw unexpected("a quoted (\") string literal", value);
    };
  }

  /**
   * Tells if a {@code *} at the start or at the end of the string value is a wildcard.
   *
   * <p>
   * AIP-160 strings have wildcards in an equality. CEL strings do not have wildcards.
   *
   * @param value the value of the restriction
   * @return true if the value is a string with wildcards
   */
  public static boolean hasWildcards(Value value) {
    return value instanceof StringLiteral string && string.wildcards();
  }

  /**
   * Reads the value as the word {@code true} or {@code false} without quotes.
   *
   * <p>
   * The words are case-sensitive. The reader refuses a {@code "true"} in quotes, because it is a string.
   *
   * @param value the value of the restriction
   * @return the boolean value
   * @throws InvalidFilterException if the client wrote a different value
   */
  public static boolean coerceToBoolConstant(Value value) {
    return switch (value) {
      case BoolLiteral bool -> bool.value();
      // The literals are case-sensitive.
      case Text text when text.text().equals("true") -> true;
      case Text text when text.text().equals("false") -> false;
      case Text text ->
        throw new InvalidFilterException("expected the unquoted literal 'true' or 'false' (case-sensitive) but found '%s'",
            text.text());
      default -> throw unexpected("the unquoted literal 'true' or 'false'", value);
    };
  }

  /**
   * Reads the value as an integer without quotes. The integer can have a minus sign.
   *
   * @param value the value of the restriction
   * @return the integer
   * @throws InvalidFilterException if the client did not write an integer, or if the integer does not fit in 64 bits
   */
  public static long coerceToIntegerConstant(Value value) {
    return switch (value) {
      case IntLiteral integer -> integer.value();
      case Text text -> {
        try {
          yield Long.parseLong(text.text());
        } catch (NumberFormatException malformed) {
          // A float such as 2.5 or 2.997e9 is not an integer.
          throw new InvalidFilterException("expected an integer literal but found '%s'", text.text());
        }
      }
      case DoubleLiteral number ->
        throw new InvalidFilterException("expected an integer literal but found '%s'", number.input());
      default -> throw unexpected("an unquoted integer literal", value);
    };
  }

  /**
   * Reads the value as an RFC 3339 timestamp, for example {@code "2012-04-21T11:30:00-04:00"}.
   *
   * <p>
   * In AIP-160, the timestamp is a string in double quotes. In CEL, it is a {@code timestamp("...")} or a string.
   * {@link #parseTimestamp} gives the rules for the text.
   *
   * @param value the value of the restriction
   * @return the timestamp with its UTC offset
   * @throws InvalidFilterException if the client did not write an RFC 3339 timestamp
   */
  public static OffsetDateTime coerceToTimestampConstant(Value value) {
    return switch (value) {
      case TimestampLiteral timestamp -> timestamp.value();
      case StringLiteral string -> parseTimestamp(string.value());
      case Text text -> throw new InvalidFilterException(
          "expected a quoted RFC 3339 timestamp like \"2012-04-21T11:30:00-04:00\" but got possible field reference '%s'; did you mean to wrap the value in quotes?",
          text.text());
      default -> throw unexpected("an RFC 3339 timestamp", value);
    };
  }

  /**
   * Reads an RFC 3339 timestamp, for example {@code 2012-04-21T11:30:00-04:00}.
   *
   * <p>
   * The timestamp must have seconds and a UTC offset or {@code Z}. The {@code T} and the {@code Z} can be lowercase,
   * as RFC 3339 permits. The fraction of a second can have at most nine digits.
   *
   * @param text the text of the timestamp, without quotes
   * @return the timestamp with its UTC offset
   * @throws InvalidFilterException if the text is not an RFC 3339 timestamp
   */
  public static OffsetDateTime parseTimestamp(String text) {
    try {
      if (TIMESTAMP.matcher(text).matches()) {
        return OffsetDateTime.parse(text.toUpperCase(Locale.ROOT));
      }
    } catch (DateTimeParseException outOfRange) {
      // A month 13 or an offset of 25 hours has the correct form. It is still not a timestamp.
    }
    throw new InvalidFilterException("'%s' is not a valid RFC 3339 timestamp, expected e.g. \"2012-04-21T11:30:00-04:00\"",
        text);
  }

  /**
   * Reads the value as a duration.
   *
   * <p>
   * In AIP-160, the duration is a number with an {@code s} suffix and without quotes, for example {@code 1.5s}. In
   * CEL, it is a {@code duration("1.5s")}. The duration is exact to the nanosecond. The number of nanoseconds must
   * fit in 64 bits.
   *
   * @param value the value of the restriction
   * @return the duration
   * @throws InvalidFilterException if the client did not write a duration, or if the duration is too long
   */
  public static Duration coerceToDurationConstant(Value value) {
    return switch (value) {
      case DurationLiteral duration -> {
        try {
          duration.value().toNanos();
        } catch (ArithmeticException tooLong) {
          throw tooLongDuration(duration.input());
        }
        yield duration.value();
      }
      case Text text -> parseDuration(text.text());
      case StringLiteral string -> throw new InvalidFilterException(
          "durations must be an unquoted number with 's' suffix like 1.2s, or duration(\"1.2s\") in CEL, but got a quoted string '%s'",
          string.value());
      default -> throw unexpected("a duration like 1.2s", value);
    };
  }

  /**
   * Reads the value as the name of a key: a string in quotes or a word without quotes.
   *
   * <p>
   * The has operator uses a key, as in {@code labels:site}.
   *
   * @param value the value of the restriction
   * @return the name of the key
   * @throws InvalidFilterException if the word has a dot, or if the value is not a string or a word
   */
  public static String coerceToKeyConstant(Value value) {
    return switch (value) {
      case StringLiteral string -> string.value();
      case Text text -> {
        requireNoNavigation(text);
        yield text.text();
      }
      default -> throw unexpected("a key", value);
    };
  }

  /**
   * Tells if the value is the presence wildcard: a {@code *} without quotes, as in {@code tags:*}.
   *
   * <p>
   * The CEL parser gives {@code has(labels.site)} as {@code labels.site:*}.
   *
   * @param value the value of the restriction
   * @return true if the value is the presence wildcard
   */
  public static boolean isPresenceWildcard(Value value) {
    return value instanceof Text text && text.text().equals("*");
  }

  /**
   * The definition of an enumeration in a field.
   *
   * <p>
   * The definition gives the number for each name. The disallowed values are numbers in the definition that the
   * client cannot use. Usually this is the UNSPECIFIED value. If a client writes an unknown name or a disallowed
   * name, the error gives the names that the client can use. The names are in the order of their numbers. Names
   * with the same number are in alphabetical order.
   */
  public static final class EnumDefinition {

    private final String typeName;
    private final Map<String, Integer> values;
    private final Set<Integer> disallowedValues;
    private final String allowedValues;

    public EnumDefinition(String typeName, Map<String, Integer> values, Integer... disallowedValues) {
      this.typeName = typeName;
      this.values = Map.copyOf(values);
      this.disallowedValues = Set.of(disallowedValues);
      this.allowedValues = this.values.entrySet()
          .stream()
          .filter(entry -> !this.disallowedValues.contains(entry.getValue()))
          .sorted(Map.Entry.<String, Integer> comparingByValue().thenComparing(Map.Entry.comparingByKey()))
          .map(Map.Entry::getKey)
          .collect(joining(", "));
    }

    /**
     * Gives the name of the enum type for the error messages.
     *
     * @return the name of the enum type
     */
    public String typeName() {
      return typeName;
    }

    /**
     * Gives the names that the client can use.
     *
     * @return the names with a comma between them, in the order of their numbers
     */
    public String allowedValues() {
      return allowedValues;
    }
  }

  /**
   * Reads the value as the name of an enum value without quotes.
   *
   * @param value the value of the restriction
   * @param definition the definition of the enum
   * @return the number of the enum value
   * @throws InvalidFilterException if the client wrote a string in quotes, an unknown name or a disallowed name
   */
  public static int coerceToEnumConstant(Value value, EnumDefinition definition) {
    if (!(value instanceof Text text)) {
      throw unexpected("an unquoted enum value", value);
    }
    requireNoNavigation(text);
    String name = text.text();
    Integer number = definition.values.get(name);
    if (number == null) {
      throw new InvalidFilterException("'%s' is not one of the valid %s values, expected one of [%s]", name,
          definition.typeName, definition.allowedValues);
    }
    if (definition.disallowedValues.contains(number)) {
      throw new InvalidFilterException("'%s' is not allowed for this %s, expected one of [%s]", name, definition.typeName,
          definition.allowedValues);
    }
    return number;
  }

  private static Duration parseDuration(String value) {
    Matcher matcher = DURATION.matcher(value);
    if (!matcher.matches()) {
      throw new InvalidFilterException("'%s' is not a valid duration, expected a number followed by 's' (e.g. \"20.1s\")",
          value);
    }
    String fraction = matcher.group(2) == null ? "" : matcher.group(2);
    try {
      long seconds = Long.parseLong(matcher.group(1));
      long nanos = fraction.isEmpty() ? 0 : Long.parseLong((fraction + "00000000").substring(0, 9));
      // The column keeps nanoseconds in a 64-bit integer. Thus the full duration must fit in 64 bits.
      return Duration.ofNanos(Math.addExact(Math.multiplyExact(seconds, NANOS_PER_SECOND), nanos));
    } catch (NumberFormatException | ArithmeticException tooLong) {
      throw tooLongDuration(value);
    }
  }

  private static InvalidFilterException tooLongDuration(String value) {
    return new InvalidFilterException("'%s' is too long a duration, at most %d.%09ds", value, Long.MAX_VALUE / NANOS_PER_SECOND,
        Long.MAX_VALUE % NANOS_PER_SECOND);
  }

  private static InvalidFilterException unexpected(String expected, Value value) {
    if (value instanceof StringLiteral string) {
      return new InvalidFilterException("expected %s but found double-quoted string '%s'", expected, string.value());
    }
    return new InvalidFilterException("expected %s but found '%s'", expected, value.input());
  }

  private static void requireNoNavigation(Text text) {
    if (text.text().indexOf('.') >= 0) {
      throw new InvalidFilterException("field navigation (using '.') is not supported");
    }
  }
}
