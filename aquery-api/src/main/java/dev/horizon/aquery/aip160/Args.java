package dev.horizon.aquery.aip160;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip160.Filter.Arg;
import dev.horizon.aquery.aip160.Filter.Member;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the literal that a client wrote, and gives the value that a backend keeps.
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
 * Each reader gives the value in the type that the backend keeps. The backend then writes the value into the
 * SQL in the form that the database expects.
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
   * Reads the argument as a string constant in double quotes.
   *
   * @param arg the argument of the restriction
   * @return the string without the quotes and with the escapes applied
   * @throws InvalidFilterException if the client did not write a string in quotes, or if the value has a dot outside the quotes
   */
  public static String coerceToStringConstant(Arg arg) {
    Member member = memberOf(arg);
    if (!member.value().quoted()) {
      throw new InvalidFilterException(
          "expected a quoted (\") string literal but got possible field reference '%s'; did you mean to wrap the value in quotes?",
          member.input());
    }
    requireNoNavigation(member);
    return member.value().value();
  }

  /**
   * Reads the argument as the word {@code true} or {@code false} without quotes.
   *
   * <p>
   * The words are case-sensitive. The reader refuses a {@code "true"} in quotes, because it is a string.
   *
   * @param arg the argument of the restriction
   * @return the boolean value of the word
   * @throws InvalidFilterException if the client wrote a different value
   */
  public static boolean coerceToBoolConstant(Arg arg) {
    Member member = unquotedMemberOf(arg, "the unquoted literal 'true' or 'false'");
    requireNoNavigation(member);
    // The literals are case-sensitive.
    return switch (member.value().value()) {
      case "true" -> true;
      case "false" -> false;
      default ->
        throw new InvalidFilterException("expected the unquoted literal 'true' or 'false' (case-sensitive) but found '%s'",
            member.value().value());
    };
  }

  /**
   * Reads the argument as an integer without quotes. The integer can have a minus sign.
   *
   * @param arg the argument of the restriction
   * @return the integer
   * @throws InvalidFilterException if the client did not write an integer, or if the integer does not fit in an INT64
   */
  public static long coerceToIntegerConstant(Arg arg) {
    Member member = unquotedMemberOf(arg, "an unquoted integer literal");
    // A float such as 2.5 or 2.997e9 comes as a member with a dot.
    if (!member.fields().isEmpty()) {
      throw new InvalidFilterException("expected an integer literal but found '%s'", member.input());
    }
    try {
      return Long.parseLong(member.value().value());
    } catch (NumberFormatException malformed) {
      throw new InvalidFilterException("expected an integer literal but found '%s'", member.value().value());
    }
  }

  /**
   * Reads the argument as an RFC 3339 timestamp in double quotes, for example {@code "2012-04-21T11:30:00-04:00"}.
   *
   * <p>
   * The timestamp must have seconds and a UTC offset or {@code Z}. The {@code T} and the {@code Z} can be lowercase,
   * as RFC 3339 permits. The fraction of a second can have at most nine digits.
   *
   * @param arg the argument of the restriction
   * @return the timestamp with its UTC offset
   * @throws InvalidFilterException if the client did not write an RFC 3339 timestamp
   */
  public static OffsetDateTime coerceToTimestampConstant(Arg arg) {
    Member member = memberOf(arg);
    if (!member.value().quoted()) {
      throw new InvalidFilterException(
          "expected a quoted RFC 3339 timestamp like \"2012-04-21T11:30:00-04:00\" but got possible field reference '%s'; did you mean to wrap the value in quotes?",
          member.input());
    }
    requireNoNavigation(member);
    String value = member.value().value();
    try {
      if (TIMESTAMP.matcher(value).matches()) {
        return OffsetDateTime.parse(value.toUpperCase(Locale.ROOT));
      }
    } catch (DateTimeParseException outOfRange) {
      // A month 13 or an offset of 25 hours has the correct form. It is still not a timestamp.
    }
    throw new InvalidFilterException("'%s' is not a valid RFC 3339 timestamp, expected e.g. \"2012-04-21T11:30:00-04:00\"",
        value);
  }

  /**
   * Reads the argument as a duration without quotes: a number with an {@code s} suffix, for example {@code 1.5s}.
   *
   * <p>
   * The duration is exact to the nanosecond. The number of nanoseconds must fit in an INT64.
   *
   * @param arg the argument of the restriction
   * @return the duration
   * @throws InvalidFilterException if the client did not write a duration, or if the duration is too long
   */
  public static Duration coerceToDurationConstant(Arg arg) {
    Member member = memberOf(arg);
    if (member.value().quoted()) {
      throw new InvalidFilterException(
          "durations must be an unquoted number with 's' suffix like 1.2s but got a quoted string '%s'",
          member.value().value());
    }
    // The lexer reads 1.5s as the member 1 with the field 5s.
    String input = member.value().value();
    if (!member.fields().isEmpty()) {
      if (member.fields().size() > 1 || member.fields().getFirst().quoted()) {
        throw new InvalidFilterException("expected a duration like 1.2s, but got '%s'", member.input());
      }
      input += "." + member.fields().getFirst().value();
    }
    return parseDuration(input);
  }

  /**
   * Reads the argument as the name of a key: a string in quotes or a word without quotes.
   *
   * <p>
   * The has operator uses a key, as in {@code labels:site}.
   *
   * @param arg the argument of the restriction
   * @return the name of the key
   * @throws InvalidFilterException if the value has a dot outside the quotes
   */
  public static String coerceToKeyConstant(Arg arg) {
    Member member = memberOf(arg);
    requireNoNavigation(member);
    return member.value().value();
  }

  /**
   * Tells if the argument is the presence wildcard: a {@code *} without quotes, as in {@code tags:*}.
   *
   * @param arg the argument of the restriction
   * @return true if the argument is the presence wildcard
   */
  public static boolean isPresenceWildcard(Arg arg) {
    if (arg.composite() != null || arg.comparable() == null || arg.comparable().member() == null) {
      return false;
    }
    Member member = arg.comparable().member();
    return !member.value().quoted() && member.fields().isEmpty() && member.value().value().equals("*");
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
   * Reads the argument as the name of an enum value without quotes.
   *
   * @param arg the argument of the restriction
   * @param definition the definition of the enum
   * @return the number of the enum value
   * @throws InvalidFilterException if the client wrote a string in quotes, an unknown name or a disallowed name
   */
  public static int coerceToEnumConstant(Arg arg, EnumDefinition definition) {
    Member member = unquotedMemberOf(arg, "an unquoted enum value");
    requireNoNavigation(member);
    String name = member.value().value();
    Integer value = definition.values.get(name);
    if (value == null) {
      throw new InvalidFilterException("'%s' is not one of the valid %s values, expected one of [%s]", name,
          definition.typeName, definition.allowedValues);
    }
    if (definition.disallowedValues.contains(value)) {
      throw new InvalidFilterException("'%s' is not allowed for this %s, expected one of [%s]", name, definition.typeName,
          definition.allowedValues);
    }
    return value;
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
      // The column keeps nanoseconds in an INT64. Thus the full duration must fit in an INT64.
      return Duration.ofNanos(Math.addExact(Math.multiplyExact(seconds, NANOS_PER_SECOND), nanos));
    } catch (NumberFormatException | ArithmeticException tooLong) {
      throw new InvalidFilterException("'%s' is too long a duration, at most %d.%09ds", value,
          Long.MAX_VALUE / NANOS_PER_SECOND, Long.MAX_VALUE % NANOS_PER_SECOND);
    }
  }

  private static Member unquotedMemberOf(Arg arg, String expected) {
    Member member = memberOf(arg);
    if (member.value().quoted()) {
      throw new InvalidFilterException("expected %s but found double-quoted string '%s'", expected, member.value().value());
    }
    return member;
  }

  private static void requireNoNavigation(Member member) {
    if (!member.fields().isEmpty()) {
      throw new InvalidFilterException("field navigation (using '.') is not supported");
    }
  }

  private static Member memberOf(Arg arg) {
    if (arg.composite() != null) {
      throw new InvalidFilterException("composite expressions in arguments not supported yet");
    }
    if (arg.comparable() == null || arg.comparable().member() == null) {
      throw new InvalidFilterException("missing comparable in argument");
    }
    return arg.comparable().member();
  }
}
