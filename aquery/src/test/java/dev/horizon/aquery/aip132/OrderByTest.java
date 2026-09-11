package dev.horizon.aquery.aip132;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.StringColumn;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderByTest {

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("foo").backend(new StringColumn("db_foo")).sortable().build(),
      new Field.Builder("bar").backend(new StringColumn("db_bar")).sortable().build(),
      new Field.Builder("baz").backend(new StringColumn("db_baz")).sortable().build(),
      new Field.Builder("unsortable").backend(new StringColumn("unsortable")).build());

  @Test
  @DisplayName("values are a comma separated list of fields")
  void commaSeparated() {
    assertThat(Order.parse("foo,bar")).containsExactly(new OrderBy(new FieldPath("foo"), false),
        new OrderBy(new FieldPath("bar"), false));
    assertThat(Order.parse("foo")).containsExactly(new OrderBy(new FieldPath("foo"), false));
  }

  @Test
  @DisplayName("the default sort order is ascending")
  void ascendingByDefault() {
    assertThat(Order.parse("foo desc, bar")).containsExactly(new OrderBy(new FieldPath("foo"), true),
        new OrderBy(new FieldPath("bar"), false));
    assertThat(Order.parse("foo asc")).containsExactly(new OrderBy(new FieldPath("foo"), false));
  }

  @Test
  @DisplayName("redundant space characters in the syntax are insignificant")
  void spacesAreInsignificant() {
    List<OrderBy> expected = List.of(new OrderBy(new FieldPath("foo"), false), new OrderBy(new FieldPath("bar"), true));

    assertThat(Order.parse("foo, bar desc")).isEqualTo(expected);
    assertThat(Order.parse("  foo  ,  bar desc  ")).isEqualTo(expected);
    assertThat(Order.parse("foo,bar desc")).isEqualTo(expected);
  }

  @Test
  @DisplayName("subfields are specified with a . character")
  void subfields() {
    assertThat(Order.parse("foo.bar, foo.foo.bar desc")).containsExactly(new OrderBy(new FieldPath("foo", "bar"), false),
        new OrderBy(new FieldPath("foo", "foo", "bar"), true));
  }

  @Test
  @DisplayName("quoted strings can be used instead of string literals")
  void quotedSegments() {
    assertThat(Order.parse("foo.`bar`, foo.foo.`a-backtick-```.bar desc")).containsExactly(
        new OrderBy(new FieldPath("foo", "bar"), false), new OrderBy(new FieldPath("foo", "foo", "a-backtick-`", "bar"), true));
  }

  @Test
  @DisplayName("a quoted segment may hold commas and spaces")
  void quotedSeparators() {
    assertThat(Order.parse("labels.`a, b` desc, foo")).containsExactly(new OrderBy(new FieldPath("labels", "a, b"), true),
        new OrderBy(new FieldPath("foo"), false));
  }

  @ParameterizedTest(name = "[{0}] fails at {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
      `something | 0
      foo, | 4
      a-b | 1
      foo desc asc | 9
      foo up | 4
      foo. | 4
      1foo | 0
      """)
  @DisplayName("invalid input is rejected where it goes wrong")
  void invalidInput(String orderBy, int position) {
    assertThatThrownBy(() -> Order.parse(orderBy))
        .isInstanceOf(InvalidOrderByException.class)
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).position()).isEqualTo(position));
  }

  @Test
  @DisplayName("a field may not appear twice")
  void duplicates() {
    assertThatThrownBy(() -> Order.parse("foo, foo"))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("appears multiple times");
  }

  @Test
  @DisplayName("an empty order by is no order at all")
  void empty() {
    assertThat(Order.parse("")).isEmpty();
    assertThat(Order.parse("   ")).isEmpty();
    assertThat(Order.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("parses one field path")
  void fieldPaths() {
    assertThat(FieldPath.parse("named_bars.`bar-key`.foobar")).isEqualTo(new FieldPath("named_bars", "bar-key", "foobar"));
    assertThat(new FieldPath("named_bars", "bar-key", "foobar")).hasToString("named_bars.`bar-key`.foobar");
    assertThatThrownBy(() -> FieldPath.parse("foo bar")).isInstanceOf(InvalidOrderByException.class);
  }

  @Test
  @DisplayName("writes the clause from the names the table declares")
  void writesTheClause() {
    assertThat(OrderByClause.of(table, Order.parse("foo"))).isEqualTo("ORDER BY db_foo\n");
    assertThat(OrderByClause.of(table, Order.parse("foo desc, bar, baz desc")))
        .isEqualTo("ORDER BY db_foo DESC, db_bar, db_baz DESC\n");
  }

  @Test
  @DisplayName("qualifies the columns with the table alias")
  void writesTheClauseWithAnAlias() {
    assertThat(OrderByClause.of(table, Order.parse("foo desc, bar"), "T")).isEqualTo("ORDER BY T.db_foo DESC, T.db_bar\n");
  }

  @Test
  @DisplayName("an empty order writes no clause")
  void emptyClause() {
    assertThat(OrderByClause.of(table, List.of())).isEmpty();
  }

  @Test
  @DisplayName("refuses an unsortable field, naming the sortable ones")
  void refusesUnsortable() {
    assertThatThrownBy(() -> OrderByClause.of(table, Order.parse("unsortable")))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("no sortable field named 'unsortable', valid fields are foo, bar, baz");
  }

  @Test
  @DisplayName("refuses a repeated field")
  void refusesRepeated() {
    List<OrderBy> order = List.of(new OrderBy(new FieldPath("foo"), false), new OrderBy(new FieldPath("foo"), false));

    assertThatThrownBy(() -> OrderByClause.of(table, order))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("field appears in order_by multiple times: 'foo'");
  }

  @Test
  @DisplayName("ordering specified takes precedence over the default order")
  void mergesWithDefaultOrder() {
    List<OrderBy> defaultOrder = List.of(
        new OrderBy(new FieldPath("foo"), true),
        new OrderBy(new FieldPath("bar"), false),
        new OrderBy(new FieldPath("baz"), true));

    assertThat(OrderByClause.mergeWithDefaultOrder(defaultOrder, List.of())).isEqualTo(defaultOrder);

    List<OrderBy> order = List.of(new OrderBy(new FieldPath("other"), true), new OrderBy(new FieldPath("baz"), false));

    assertThat(OrderByClause.mergeWithDefaultOrder(defaultOrder, order)).containsExactly(
        new OrderBy(new FieldPath("other"), true),
        new OrderBy(new FieldPath("baz"), false),
        new OrderBy(new FieldPath("foo"), true),
        new OrderBy(new FieldPath("bar"), false));
  }
}
