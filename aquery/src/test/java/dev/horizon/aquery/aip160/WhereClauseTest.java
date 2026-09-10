package dev.horizon.aquery.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WhereClauseTest {

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build(),
      new Field.Builder("path", "to", "bar").backend(new StringColumn("db_bar")).filterableImplicitly().build(),
      new Field.Builder("path", "to", "baz").backend(new StringColumn("db_baz")).filterable().build(),
      new Field.Builder("path", "to", "keyvalue").backend(new KeyValueColumn("db_kv", Representation.STRING_ARRAY)).filterable()
          .build());

  @Test
  @DisplayName("an empty filter matches everything and binds nothing")
  void emptyFilter() {
    WhereClause.Result result = WhereClause.of(table, Filter.parse(""), "T", "p_");

    assertThat(result.parameters()).isEmpty();
    assertThat(result.sql()).isEqualTo("(TRUE)");
  }

  @Test
  @DisplayName("a null filter matches everything and binds nothing")
  void nullFilter() {
    WhereClause.Result result = WhereClause.of(table, null, "T", "p_");

    assertThat(result.parameters()).isEmpty();
    assertThat(result.sql()).isEqualTo("(TRUE)");
  }

  @Test
  @DisplayName("compiles the operators, the implicit search and the key-value member")
  void complexFilter() {
    Filter filter = Filter.parse(
        "implicit (foo=\"explicitone\") OR -path.to.bar=\"explicittwo\" AND foo!=\"explicitthree\" OR path.to.baz:\"explicitfour\" OR path.to.keyvalue.key:\"explicitfive\"");

    WhereClause.Result result = WhereClause.of(table, filter, "T", "p_");

    assertThat(result.parameters())
        .containsExactly(
            new WhereClause.QueryParameter("p_0", "%implicit%"),
            new WhereClause.QueryParameter("p_1", "%implicit%"),
            new WhereClause.QueryParameter("p_2", "explicitone"),
            new WhereClause.QueryParameter("p_3", "explicittwo"),
            new WhereClause.QueryParameter("p_4", "explicitthree"),
            new WhereClause.QueryParameter("p_5", "%explicitfour%"),
            new WhereClause.QueryParameter("p_6", "key:%explicitfive%"));

    assertThat(result.sql()).isEqualTo(
        "(((T.db_foo LIKE @p_0) OR (T.db_bar LIKE @p_1)) AND ((T.db_foo = @p_2) OR (NOT (T.db_bar = @p_3))) AND ((T.db_foo <> @p_4) OR (T.db_baz LIKE @p_5) OR (EXISTS (SELECT 1 FROM UNNEST(T.db_kv) as _v WHERE _v LIKE @p_6))))");
  }

  @Test
  @DisplayName("names the fields the resource does have when a field is missing")
  void fieldDoesNotExist() {
    Filter filter = Filter.parse("path.to.nonexisting=\"somevalue\"");

    assertThatThrownBy(() -> WhereClause.of(table, filter, "T", "p_"))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("no filterable field 'path.to.nonexisting' or a prefix thereof")
        .hasMessageContaining("foo")
        .hasMessageContaining("path.to.bar");
  }

  @Test
  @Timeout(5)
  @DisplayName("looks up a very long path in linear time")
  void longPaths() {
    String longest = "a" + ".a".repeat(Filter.MAX_LENGTH / 2 - 6) + " = \"x\"";
    Filter filter = Filter.parse(longest);

    long start = System.nanoTime();
    assertThatThrownBy(() -> WhereClause.of(table, filter, "T", "p_")).isInstanceOf(InvalidQueryException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
  }

  @Test
  @DisplayName("a quoted left hand side is a value, not a field")
  void quotedLeftHandSide() {
    Filter filter = Filter.parse("\"foo\"=\"somevalue\"");

    assertThatThrownBy(() -> WhereClause.of(table, filter, "T", "p_"))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("expected a field name on the left hand side");
  }

  @Test
  @DisplayName("a bare value with no implicit field answers an error naming the value")
  void noImplicitFields() {
    DatabaseTable plain = new DatabaseTable(new Field.Builder("foo").backend(new StringColumn("db_foo")).filterable().build());

    assertThatThrownBy(() -> WhereClause.of(plain, Filter.parse("bare"), "T", "p_"))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("no fields are configured to match the bare value 'bare'");
  }

  @Test
  @DisplayName("a value that looks like SQL is still only a bound value")
  void injection() {
    WhereClause.Result result = WhereClause.of(table, Filter.parse("foo=\"' OR 1=1 --\""), null, "p");

    assertThat(result.sql()).isEqualTo("(db_foo = @p0)");
    assertThat(result.parameters()).containsExactly(new WhereClause.QueryParameter("p0", "' OR 1=1 --"));
  }

  @Test
  @DisplayName("table aliases starting with '_' are reserved for generated SQL")
  void reservedAlias() {
    assertThatThrownBy(() -> WhereClause.of(table, null, "_t", "p_"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @ParameterizedTest(name = "alias [{0}]")
  @ValueSource(strings = { "T; DROP TABLE x", "t.u", "1t", "a b" })
  @DisplayName("table aliases must be SQL identifiers")
  void aliasesAreIdentifiers(String alias) {
    assertThatThrownBy(() -> WhereClause.of(table, null, alias, "p_")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "prefix [{0}]")
  @ValueSource(strings = { "p-", "p;", "", "1p" })
  @DisplayName("parameter prefixes must be SQL identifiers")
  void prefixesAreIdentifiers(String prefix) {
    assertThatThrownBy(() -> WhereClause.of(table, null, "T", prefix)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("without a table alias, columns are named plainly")
  void noAlias() {
    WhereClause.Result result = WhereClause.of(table, Filter.parse("foo=\"one\""), "", "p");

    assertThat(result.sql()).isEqualTo("(db_foo = @p0)");
  }

  @Test
  @DisplayName("the parameter prefix keeps filter parameters apart from the statement's own")
  void prefix() {
    WhereClause.Result result = WhereClause.of(table, Filter.parse("foo=\"one\""), "T", "filter_");

    assertThat(result.sql()).isEqualTo("(T.db_foo = @filter_0)");
    assertThat(result.parameters()).containsExactly(new WhereClause.QueryParameter("filter_0", "one"));
  }

  @Test
  @DisplayName("a filter at the depth limit compiles")
  void deepFilters() {
    String deep = "(".repeat(Filter.MAX_DEPTH) + "foo=\"x\"" + ")".repeat(Filter.MAX_DEPTH);

    assertThatNoException().isThrownBy(() -> WhereClause.of(table, Filter.parse(deep), "T", "p_"));
  }
}
