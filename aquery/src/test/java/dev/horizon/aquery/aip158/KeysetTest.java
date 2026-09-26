package dev.horizon.aquery.aip158;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.BoolColumn;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.DurationColumn;
import dev.horizon.aquery.aip160.EnumColumn;
import dev.horizon.aquery.aip160.EnumColumn.Storage;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend;
import dev.horizon.aquery.aip160.FieldBackend.ColumnReferences;
import dev.horizon.aquery.aip160.FieldBackend.SortKey;
import dev.horizon.aquery.aip160.IntegerColumn;
import dev.horizon.aquery.aip160.SimpleColumn;
import dev.horizon.aquery.aip160.StringColumn;
import dev.horizon.aquery.aip160.TimestampColumn;
import dev.horizon.aquery.aip160.UuidColumn;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeysetTest {

  private static final OffsetDateTime CREATED = OffsetDateTime.of(2012, 4, 21, 15, 30, 0, 0, ZoneOffset.UTC);

  private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  private static final Args.EnumDefinition STATUS = new Args.EnumDefinition("Status", Map.of("ACTIVE", 1, "INACTIVE", 2), 0);

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("create_time").backend(new TimestampColumn("create_time")).sortable().build(),
      new Field.Builder("id").backend(new StringColumn("id")).sortable().build(),
      new Field.Builder("archived").backend(new BoolColumn("archived")).sortable().build(),
      new Field.Builder("count").backend(new IntegerColumn("count")).sortable().build(),
      new Field.Builder("uid").backend(new UuidColumn("uid")).sortable().build(),
      new Field.Builder("status").backend(new EnumColumn("status", STATUS, Storage.NAME)).sortable().build(),
      new Field.Builder("rank").backend(new TwoKeys("rank")).sortable().build(),
      new Field.Builder("name").backend(new StringColumn("name")).filterable().build());

  private static List<OrderBy> order(String... terms) {
    return Arrays.stream(terms)
        .map(term -> term.startsWith("-") ? new OrderBy(new FieldPath(term.substring(1)), true)
            : new OrderBy(new FieldPath(term), false))
        .toList();
  }

  @Test
  @DisplayName("resumes after the cursor row, direction by direction")
  void resumesAfterTheCursor() {
    Keyset keyset = new Keyset(table, order("-create_time", "id"));
    Parameters parameters = new Parameters(ParameterStyle.DOLLAR);

    String sql = keyset.after(List.of("2012-04-21T15:30:00Z", "abc"), "T", parameters);

    assertThat(sql).isEqualTo("((T.create_time < $1) OR (T.create_time = $2 AND T.id > $3))");
    assertThat(parameters.values()).containsExactly(CREATED, CREATED, "abc");
  }

  @Test
  @DisplayName("each placeholder binds its own value, in the sequence of the text")
  void eachPlaceholderBindsItsOwnValue() {
    Keyset keyset = new Keyset(table, order("-create_time", "archived", "id"));
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

    String sql = keyset.after(List.of("2012-04-21T11:30:00-04:00", "false", "abc"), "T", parameters);

    assertThat(sql).isEqualTo(
        "((T.create_time < ?) OR (T.create_time = ? AND T.archived > ?) OR (T.create_time = ? AND T.archived = ? AND T.id > ?))");
    assertThat(parameters.values()).containsExactly(CREATED, CREATED, false, CREATED, false, "abc");
    assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(parameters.size());
  }

  @ParameterizedTest(name = "[{0}] binds {2}")
  @MethodSource("typedCursors")
  @DisplayName("binds the cursor value in the type of the column")
  void typedCursorValues(String field, String cursorText, Object expectedValue) {
    Keyset keyset = new Keyset(table, order(field));
    Parameters parameters = new Parameters(ParameterStyle.COLON);

    assertThat(keyset.after(List.of(cursorText), null, parameters)).isEqualTo("((" + field + " > :1))");
    assertThat(parameters.values()).containsExactly(expectedValue);
    assertThat(parameters.values().getFirst()).isExactlyInstanceOf(expectedValue.getClass());
  }

  static List<Arguments> typedCursors() {
    return List.of(
        Arguments.of("archived", "true", true),
        Arguments.of("count", "-42", -42L),
        Arguments.of("uid", "123e4567-e89b-12d3-a456-426614174000", ID),
        Arguments.of("status", "ACTIVE", "ACTIVE"),
        Arguments.of("create_time", "2012-04-21T15:30:00.123456Z", CREATED.plusNanos(123_456_000)),
        Arguments.of("id", "abc", "abc"));
  }

  @Test
  @DisplayName("an empty order resumes everywhere and binds nothing")
  void emptyOrder() {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

    assertThat(new Keyset(table, List.of()).after(List.of(), "T", parameters)).isEqualTo("(1 = 1)");
    assertThat(parameters.values()).isEmpty();
  }

  @Test
  @DisplayName("a string cursor value is only ever a bound value")
  void injection() {
    Keyset keyset = new Keyset(table, order("id"));
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

    String sql = keyset.after(List.of("' OR 1=1 --"), null, parameters);

    assertThat(sql).isEqualTo("((id > ?))").doesNotContain("OR 1=1");
    assertThat(parameters.values()).containsExactly("' OR 1=1 --");
  }

  @Test
  @DisplayName("refuses a cursor that does not fit the order, as a page token error")
  void refusesMismatchedCursors() {
    Keyset keyset = new Keyset(table, order("create_time"));
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

    assertThatThrownBy(() -> keyset.after(List.of("1", "2"), null, parameters)).isInstanceOf(InvalidPageTokenException.class);
    assertThatThrownBy(() -> keyset.after(List.of("not a timestamp"), null, parameters))
        .isInstanceOf(InvalidPageTokenException.class)
        .hasMessageContaining("create_time");
    assertThat(parameters.values()).isEmpty();
  }

  @ParameterizedTest(name = "[{0}] refuses [{1}]")
  @MethodSource("unreadableCursors")
  @DisplayName("refuses a cursor value that the backend cannot read")
  void refusesUnreadableCursorValues(String field, String cursorText) {
    Keyset keyset = new Keyset(table, order("id", field));
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

    assertThatThrownBy(() -> keyset.after(List.of("abc", cursorText), null, parameters))
        .isInstanceOf(InvalidPageTokenException.class)
        .hasMessageContaining("page_token has a cursor value that field '" + field + "' cannot read; start the listing again");
    assertThat(parameters.values()).isEmpty();
  }

  static List<Arguments> unreadableCursors() {
    return List.of(
        Arguments.of("archived", "TRUE"),
        Arguments.of("count", "4.2"),
        Arguments.of("count", "99999999999999999999"),
        Arguments.of("uid", "1-1-1-1-1"),
        Arguments.of("uid", "not a uuid"),
        Arguments.of("create_time", "1335022200000000"));
  }

  @Test
  @DisplayName("refuses an order it cannot resume")
  void refusesUnresumableOrders() {
    assertThatThrownBy(() -> new Keyset(table, order("name"))).isInstanceOf(InvalidOrderByException.class);
    assertThatThrownBy(() -> new Keyset(table, order("rank")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one sort key");
  }

  @ParameterizedTest(name = "{0} writes [{2}]")
  @MethodSource("timestampTexts")
  @DisplayName("a timestamp cursor is RFC 3339 in UTC, and reads back as the same instant")
  void timestampCursorTexts(String description, Object rowValue, String expectedText) {
    TimestampColumn column = new TimestampColumn("create_time");

    String text = column.cursorText(rowValue);

    assertThat(text).isEqualTo(expectedText);
    assertThat(column.cursorValue(text)).isInstanceOf(OffsetDateTime.class).satisfies(value -> {
      OffsetDateTime timestamp = (OffsetDateTime) value;
      assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
      assertThat(column.cursorText(timestamp)).isEqualTo(text);
    });
  }

  static List<Arguments> timestampTexts() {
    return List.of(
        Arguments.of("Instant", Instant.parse("2012-04-21T15:30:00Z"), "2012-04-21T15:30:00Z"),
        Arguments.of("OffsetDateTime", OffsetDateTime.parse("2012-04-21T11:30:00-04:00"), "2012-04-21T15:30:00Z"),
        Arguments.of("LocalDateTime", LocalDateTime.of(2012, 4, 21, 15, 30), "2012-04-21T15:30:00Z"),
        Arguments.of("microseconds", Instant.parse("2012-04-21T15:30:00.123456Z"), "2012-04-21T15:30:00.123456Z"),
        Arguments.of("nanoseconds", Instant.parse("2012-04-21T15:30:00.000000001Z"), "2012-04-21T15:30:00.000000001Z"));
  }

  @Test
  @DisplayName("a timestamp cursor refuses values that are not timestamps")
  void timestampCursorRefusals() {
    TimestampColumn column = new TimestampColumn("create_time");

    assertThatThrownBy(() -> column.cursorText("2012-04-21T15:30:00Z")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> column.cursorText(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> column.cursorValue("yesterday")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a UUID cursor is the lowercase UUID, and reads back as the same UUID")
  void uuidCursorTexts() {
    UuidColumn column = new UuidColumn("uid");

    assertThat(column.cursorText(ID)).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    assertThat(column.cursorText("123E4567-E89B-12D3-A456-426614174000")).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    assertThat(column.cursorValue(column.cursorText(ID))).isEqualTo(ID);
    assertThatThrownBy(() -> column.cursorText(42)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> column.cursorText("1-1-1-1-1")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> column.cursorValue("123e4567e89b12d3a456426614174000"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("integerTexts")
  @DisplayName("an integer cursor is the decimal text of the row value")
  void integerCursorTexts(Object rowValue, String expectedText) {
    FieldBackend column = new IntegerColumn("count");

    assertThat(column.cursorText(rowValue)).isEqualTo(expectedText);
    assertThat(column.cursorValue(expectedText)).isEqualTo(Long.valueOf(expectedText));
  }

  static List<Arguments> integerTexts() {
    return List.of(
        Arguments.of(42L, "42"),
        Arguments.of(-7, "-7"),
        Arguments.of(new BigDecimal("1500.000"), "1500"),
        Arguments.of(BigInteger.valueOf(Long.MIN_VALUE), "-9223372036854775808"));
  }

  @Test
  @DisplayName("typed cursors refuse row values of another type")
  void typedCursorRefusals() {
    assertThatThrownBy(() -> new IntegerColumn("count").cursorText(new BigDecimal("4.2")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DurationColumn("age").cursorText(BigInteger.TWO.pow(63)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IntegerColumn("count").cursorText("42")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BoolColumn("archived").cursorText(1)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new BoolColumn("archived").cursorText(true)).isEqualTo("true");
  }

  @ParameterizedTest(name = "storage {0}")
  @ValueSource(strings = { "NUMBER", "NAME" })
  @DisplayName("an enum cursor follows the storage of the column")
  void enumCursorTexts(Storage storage) {
    EnumColumn column = new EnumColumn("status", STATUS, storage);

    switch (storage) {
      case NUMBER -> {
        assertThat(column.cursorText(1)).isEqualTo("1");
        assertThat(column.cursorValue("1")).isEqualTo(1L);
      }
      case NAME -> {
        assertThat(column.cursorText("ACTIVE")).isEqualTo("ACTIVE");
        assertThat(column.cursorValue("ACTIVE")).isEqualTo("ACTIVE");
      }
    }
  }

  private static final class TwoKeys extends SimpleColumn {

    TwoKeys(String databaseName) {
      super(databaseName);
    }

    @Override
    public List<SortKey> sortKeys(boolean descending, ColumnReferences columns) {
      return List.of(new SortKey(column(columns) + "_major", descending), new SortKey(column(columns) + "_minor", descending));
    }
  }
}
