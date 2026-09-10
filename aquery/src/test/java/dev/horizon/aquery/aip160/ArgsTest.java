package dev.horizon.aquery.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip160.Filter.Arg;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArgsTest {

  private static final Args.EnumDefinition STATUS = new Args.EnumDefinition("Status",
      Map.of("STATUS_UNSPECIFIED", 0, "ACTIVE", 1, "INACTIVE", 2), 0);

  private static Arg argOf(String filter) {
    return Filter.parse(filter)
        .expression()
        .sequences()
        .getFirst()
        .factors()
        .getFirst()
        .terms()
        .getFirst()
        .simple()
        .restriction()
        .arg();
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({ "'foo = true',  true", "'foo = false', false" })
  void readsBooleans(String filter, boolean expected) {
    assertThat(Args.coerceToBoolConstant(argOf(filter))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = \"true\"", "foo = \"false\"", "foo = bar" })
  void refusesBooleans(String filter) {
    assertThatThrownBy(() -> Args.coerceToBoolConstant(argOf(filter)))
        .isInstanceOf(InvalidQueryException.class)
        .satisfies(error -> assertThat(error.getMessage()).containsAnyOf("unquoted literal", "double-quoted string"));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("durations")
  void readsDurations(String filter, Duration expected) {
    assertThat(Args.coerceToDurationConstant(argOf(filter))).isEqualTo(expected);
  }

  static Stream<Arguments> durations() {
    return Stream.of(
        Arguments.of("foo = 1s", Duration.ofSeconds(1)),
        Arguments.of("foo = 1.5s", Duration.ofMillis(1500)),
        Arguments.of("foo = 1.000285084s", Duration.ofNanos(1_000_285_084)),
        Arguments.of("foo = 0.000000001s", Duration.ofNanos(1)),
        Arguments.of("foo = 9223372036.854775807s", Duration.ofNanos(Long.MAX_VALUE)));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = \"1s\"", "foo = 1", "foo = -1s", "foo = 1.0000000001s" })
  void refusesDurations(String filter) {
    assertThatThrownBy(() -> Args.coerceToDurationConstant(argOf(filter))).isInstanceOf(InvalidQueryException.class)
        .satisfies(error -> assertThat(error.getMessage()).containsAnyOf("durations must be an unquoted number",
            "is not a valid duration"));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = 9223372036.854775808s", "foo = 99999999999999999999s" })
  @DisplayName("refuses a duration too long for INT64 nanoseconds rather than capping it")
  void refusesLongDurations(String filter) {
    assertThatThrownBy(() -> Args.coerceToDurationConstant(argOf(filter)))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("too long a duration");
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({ "'foo = 123', 123", "'foo = 0',   0", "'foo = -30', -30" })
  void readsIntegers(String filter, long expected) {
    assertThat(Args.coerceToIntegerConstant(argOf(filter))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = \"123\"", "foo = bar", "foo = 99999999999999999999" })
  void refusesIntegers(String filter) {
    assertThatThrownBy(() -> Args.coerceToIntegerConstant(argOf(filter))).isInstanceOf(InvalidQueryException.class);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = 2.5", "foo = 2.997e9" })
  @DisplayName("names the float it refuses as an integer")
  void refusesFloats(String filter) {
    assertThatThrownBy(() -> Args.coerceToIntegerConstant(argOf(filter)))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("expected an integer literal but found '" + filter.substring(6) + "'");
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({ "'foo = \"bar\"', bar", "'foo = \"\"',    ''" })
  void readsStrings(String filter, String expected) {
    assertThat(Args.coerceToStringConstant(argOf(filter))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = bar", "foo = (a OR b)" })
  void refusesStrings(String filter) {
    assertThatThrownBy(() -> Args.coerceToStringConstant(argOf(filter))).isInstanceOf(InvalidQueryException.class)
        .satisfies(error -> assertThat(error.getMessage()).containsAnyOf("did you mean to wrap the value in quotes?",
            "composite expressions in arguments not supported"));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({
      "'foo = \"2012-04-21T11:30:00-04:00\"', 2012-04-21T15:30Z",
      "'foo = \"2012-04-21T11:30:00Z\"',       2012-04-21T11:30Z",
      "'foo = \"2012-04-21t11:30:00z\"',       2012-04-21T11:30Z",
      "'foo = \"2012-04-21T11:30:00.500Z\"',   2012-04-21T11:30:00.500Z" })
  void readsTimestamps(String filter, OffsetDateTime expected) {
    assertThat(Args.coerceToTimestampConstant(argOf(filter))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = {
      "foo = \"not a time\"",
      "foo = \"2012-04-21 11:30\"",
      "foo = \"2012-04-21T11:30-04:00\"",
      "foo = \"2012-04-21T11:30:00\"",
      "foo = \"2012-13-21T11:30:00Z\"",
      "foo = \"+999999999-12-31T23:59:59Z\"",
      "foo = (a OR b)" })
  @DisplayName("refuses what is not an RFC 3339 date-time")
  void refusesTimestamps(String filter) {
    assertThatThrownBy(() -> Args.coerceToTimestampConstant(argOf(filter))).isInstanceOf(InvalidQueryException.class)
        .satisfies(error -> assertThat(error.getMessage()).containsAnyOf("did you mean to wrap the value in quotes?",
            "is not a valid RFC 3339 timestamp", "composite expressions in arguments not supported"));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("enumValues")
  void readsEnums(String filter, int expected) {
    assertThat(Args.coerceToEnumConstant(argOf(filter), STATUS)).isEqualTo(expected);
  }

  static Stream<Arguments> enumValues() {
    return Stream.of(Arguments.of("foo = ACTIVE", 1), Arguments.of("foo = INACTIVE", 2));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo = \"ACTIVE\"", "foo = UNKNOWN", "foo = STATUS_UNSPECIFIED" })
  void refusesEnums(String filter) {
    assertThatThrownBy(() -> Args.coerceToEnumConstant(argOf(filter), STATUS)).isInstanceOf(InvalidQueryException.class)
        .satisfies(error -> assertThat(error.getMessage()).containsAnyOf("expected an unquoted enum value",
            "is not one of the valid Status values", "is not allowed for this Status"));
  }

  @Test
  @DisplayName("names the allowed enum values by number, then by name")
  void allowedValuesOrder() {
    Args.EnumDefinition aliases = new Args.EnumDefinition("Color", Map.of("RED", 1, "CRIMSON", 1, "BLUE", 2, "NONE", 0), 0);

    assertThat(aliases.allowedValues()).isEqualTo("CRIMSON, RED, BLUE");
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({ "'foo : *', true", "'foo : \"*\"', false", "'foo : bar', false" })
  void presenceWildcard(String filter, boolean expected) {
    assertThat(Args.isPresenceWildcard(argOf(filter))).isEqualTo(expected);
  }
}
