package dev.horizon.aquery.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import dev.horizon.aquery.ebnf.EbnfFilterParser;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WhereClauseTest {

  private static final FilterParser EBNF = new EbnfFilterParser();

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build(),
      new Field.Builder("path", "to", "bar").backend(new StringColumn("db_bar")).filterableImplicitly().build(),
      new Field.Builder("path", "to", "baz").backend(new StringColumn("db_baz")).filterable().build(),
      new Field.Builder("path", "to", "keyvalue").backend(new KeyValueColumn("db_kv", Representation.STRING_ARRAY)).filterable()
          .build());

  private static Parameters parameters() {
    return new Parameters(ParameterStyle.QUESTION_MARK);
  }

  @Test
  @DisplayName("an empty filter matches everything and binds nothing")
  void emptyFilter() {
    Parameters parameters = parameters();

    assertThat(WhereClause.of(table, EBNF.parse(""), "T", parameters)).isEqualTo("(1 = 1)");
    assertThat(parameters.values()).isEmpty();
  }

  @Test
  @DisplayName("a null filter matches everything and binds nothing")
  void nullFilter() {
    Parameters parameters = parameters();

    assertThat(WhereClause.of(table, null, "T", parameters)).isEqualTo("(1 = 1)");
    assertThat(parameters.values()).isEmpty();
  }

  @Test
  @DisplayName("compiles the operators, the implicit search and the key-value member")
  void complexFilter() {
    Filter filter = EBNF.parse(
        "implicit (foo=\"explicitone\") OR -path.to.bar=\"explicittwo\" AND foo!=\"explicitthree\" OR path.to.baz:\"explicitfour\" OR path.to.keyvalue.key:\"explicitfive\"");
    Parameters parameters = new Parameters(ParameterStyle.DOLLAR);

    String sql = WhereClause.of(table, filter, "T", parameters);

    assertThat(parameters.values())
        .containsExactly("%implicit%", "%implicit%", "explicitone", "explicittwo", "explicitthree", "%explicitfour%",
            "key:%explicitfive%");
    assertThat(sql).isEqualTo(
        "(((T.db_foo LIKE $1 ESCAPE '!') OR (T.db_bar LIKE $2 ESCAPE '!')) AND ((T.db_foo = $3) OR (NOT (T.db_bar = $4))) AND ((T.db_foo <> $5) OR (T.db_baz LIKE $6 ESCAPE '!') OR (EXISTS (SELECT 1 FROM UNNEST(T.db_kv) AS aquery_u(aquery_v) WHERE aquery_v LIKE $7 ESCAPE '!'))))");
  }

  @Test
  @DisplayName("names the fields the resource does have when a field is missing")
  void fieldDoesNotExist() {
    Filter filter = EBNF.parse("path.to.nonexisting=\"somevalue\"");

    assertThatThrownBy(() -> WhereClause.of(table, filter, "T", parameters()))
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
    Filter filter = EBNF.parse(longest);

    long start = System.nanoTime();
    assertThatThrownBy(() -> WhereClause.of(table, filter, "T", parameters())).isInstanceOf(InvalidQueryException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
  }

  @Test
  @DisplayName("a quoted left hand side is a value, not a field")
  void quotedLeftHandSide() {
    assertThatThrownBy(() -> EBNF.parse("\"foo\"=\"somevalue\""))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("expected a field name on the left hand side");
  }

  @Test
  @DisplayName("a bare value with no implicit field answers an error naming the value")
  void noImplicitFields() {
    DatabaseTable plain = new DatabaseTable(new Field.Builder("foo").backend(new StringColumn("db_foo")).filterable().build());

    assertThatThrownBy(() -> WhereClause.of(plain, EBNF.parse("bare"), "T", parameters()))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("no fields are configured to match the bare value 'bare'");
  }

  @Test
  @DisplayName("a value that looks like SQL is still only a bound value")
  void injection() {
    Parameters parameters = parameters();

    String sql = WhereClause.of(table, EBNF.parse("foo=\"' OR 1=1 --\""), null, parameters);

    assertThat(sql).isEqualTo("(db_foo = ?)");
    assertThat(parameters.values()).containsExactly("' OR 1=1 --");
  }

  @ParameterizedTest(name = "alias [{0}]")
  @ValueSource(strings = { "aquery_t", "AQUERY_T", "Aquery_v" })
  @DisplayName("table aliases starting with 'aquery_' are reserved for generated SQL")
  void reservedAlias(String alias) {
    assertThatThrownBy(() -> WhereClause.of(table, null, alias, parameters()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @ParameterizedTest(name = "alias [{0}]")
  @ValueSource(strings = { "_t", "aquery", "aqueryt", "t_aquery_" })
  @DisplayName("other identifiers are table aliases")
  void unreservedAlias(String alias) {
    assertThatNoException().isThrownBy(() -> WhereClause.of(table, EBNF.parse("foo=\"x\""), alias, parameters()));
  }

  @ParameterizedTest(name = "alias [{0}]")
  @ValueSource(strings = { "T; DROP TABLE x", "t.u", "1t", "a b" })
  @DisplayName("table aliases must be SQL identifiers")
  void aliasesAreIdentifiers(String alias) {
    assertThatThrownBy(() -> WhereClause.of(table, null, alias, parameters())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("without a table alias, columns are named plainly")
  void noAlias() {
    assertThat(WhereClause.of(table, EBNF.parse("foo=\"one\""), "", parameters())).isEqualTo("(db_foo = ?)");
  }

  @Test
  @DisplayName("numbered placeholders continue after the values that the statement already bound")
  void sharedParameters() {
    Parameters parameters = new Parameters(ParameterStyle.AT_P);
    parameters.bind("tenant");

    String sql = WhereClause.of(table, EBNF.parse("foo=\"one\""), "T", parameters);

    assertThat(sql).isEqualTo("(T.db_foo = @p2)");
    assertThat(parameters.values()).containsExactly("tenant", "one");
  }

  @Test
  @DisplayName("a filter at the depth limit compiles")
  void deepFilters() {
    String deep = "(".repeat(EbnfFilterParser.MAX_DEPTH) + "foo=\"x\"" + ")".repeat(EbnfFilterParser.MAX_DEPTH);

    assertThatNoException().isThrownBy(() -> WhereClause.of(table, EBNF.parse(deep), "T", parameters()));
  }
}
