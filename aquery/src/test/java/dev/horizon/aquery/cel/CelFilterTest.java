package dev.horizon.aquery.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.BoolColumn;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.DurationColumn;
import dev.horizon.aquery.aip160.EnumColumn;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.FilterParser;
import dev.horizon.aquery.aip160.IntegerColumn;
import dev.horizon.aquery.aip160.KeyValueColumn;
import dev.horizon.aquery.aip160.KeyValueColumn.ChildTable;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import dev.horizon.aquery.aip160.RepeatedStringColumn;
import dev.horizon.aquery.aip160.StringColumn;
import dev.horizon.aquery.aip160.TimestampColumn;
import dev.horizon.aquery.aip160.WhereClause;
import dev.horizon.aquery.ebnf.EbnfFilterParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CelFilterTest {

  private static final FilterParser CEL = new CelFilterParser();

  private static final FilterParser EBNF = new EbnfFilterParser();

  private static final Args.EnumDefinition STATUS = new Args.EnumDefinition("Status",
      Map.of("STATUS_UNSPECIFIED", 0, "ACTIVE", 1, "INACTIVE", 2), 0);

  private static final DatabaseTable TABLE = new DatabaseTable(
      new Field.Builder("name").backend(new StringColumn("db_name")).filterableImplicitly().build(),
      new Field.Builder("flag").backend(new BoolColumn("db_flag")).filterable().build(),
      new Field.Builder("count").backend(new IntegerColumn("db_count")).filterable().build(),
      new Field.Builder("age").backend(new DurationColumn("db_age")).filterable().build(),
      new Field.Builder("create_time").backend(new TimestampColumn("db_create_time")).filterable().build(),
      new Field.Builder("status").backend(new EnumColumn("db_status", STATUS)).filterable().build(),
      new Field.Builder("tags").backend(new RepeatedStringColumn("db_tags")).filterable().build(),
      new Field.Builder("labels").backend(new KeyValueColumn("db_labels", Representation.STRING_ARRAY)).filterable().build(),
      new Field.Builder("kv")
          .backend(new KeyValueColumn(new ChildTable("labels", "owner_id", "label_key", "label_value", "id")))
          .filterable()
          .build());

  private record Compiled(String sql, List<Object> values) {
  }

  private static Compiled compile(String filter, FilterParser parser) {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
    String sql = WhereClause.of(TABLE, parser.parse(filter), "T", parameters);
    return new Compiled(sql, parameters.values());
  }

  private static Compiled cel(String filter) {
    return compile(filter, CEL);
  }

  @ParameterizedTest(name = "[{0}] is [{1}]")
  @CsvSource(delimiterString = " ~ ", quoteCharacter = '`', textBlock = """
      name == "dan" ~ name = "dan"
      name != "dan" ~ name != "dan"
      name.contains("dan") ~ name : "dan"
      flag ~ flag = true
      !flag ~ NOT flag = true
      flag == false ~ flag = false
      count >= 10 && count < 20 ~ count >= 10 AND count < 20
      20 > count ~ count < 20
      count > -30 ~ count > -30
      count == 1 || count == 2 || count == 3 ~ count = 1 OR count = 2 OR count = 3
      count in [1, 2, 3] ~ count = 1 OR count = 2 OR count = 3
      status == ACTIVE ~ status = ACTIVE
      status in [ACTIVE, INACTIVE] ~ status = ACTIVE OR status = INACTIVE
      age > duration("1.5s") ~ age > 1.5s
      age < duration("1m") ~ age < 60s
      create_time > timestamp("2012-04-21T11:30:00-04:00") ~ create_time > "2012-04-21T11:30:00-04:00"
      create_time > "2012-04-21T11:30:00Z" ~ create_time > "2012-04-21T11:30:00Z"
      "prod" in tags ~ tags = "prod"
      tags.contains("pro") ~ tags : "pro"
      has(labels.site) ~ labels.site:*
      "site" in labels ~ labels:site
      labels.site == "pilsen" ~ labels.site = "pilsen"
      labels["site"] == "pilsen" ~ labels.site = "pilsen"
      labels.site.contains("pil") ~ labels.site : "pil"
      kv.site != "pilsen" ~ kv.site != "pilsen"
      "site" in kv ~ kv:site
      (name == "a" || name == "b") && !flag ~ (name = "a" OR name = "b") AND NOT flag = true
      !(count < 5 || flag) ~ NOT (count < 5 OR flag = true)
      """)
  @DisplayName("compiles to the same SQL and values as the AIP-160 filter")
  void sameAsAip160(String cel, String aip160) {
    assertThat(cel(cel)).isEqualTo(compile(aip160, EBNF));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("celOnly")
  @DisplayName("compiles the CEL forms that AIP-160 does not have")
  void celOnlyForms(String filter, String expectedSql, List<Object> expectedValues) {
    assertThat(cel(filter)).isEqualTo(new Compiled(expectedSql, expectedValues));
  }

  static List<Arguments> celOnly() {
    String element = "(EXISTS (SELECT 1 FROM UNNEST(T.db_%s) AS aquery_u(aquery_v) WHERE aquery_v %s))";
    return List.of(
        Arguments.of("name.startsWith(\"da_\")", "(T.db_name LIKE ? ESCAPE '!')", List.of("da!_%")),
        Arguments.of("name.endsWith(\"50%\")", "(T.db_name LIKE ? ESCAPE '!')", List.of("%50!%")),
        Arguments.of("name == \"*.com\"", "(T.db_name = ?)", List.of("*.com")),
        Arguments.of("name != \"prod*\"", "(T.db_name <> ?)", List.of("prod*")),
        Arguments.of("tags.startsWith(\"pr\")", element.formatted("tags", "LIKE ? ESCAPE '!'"), List.of("pr%")),
        Arguments.of("\"a*\" in tags", element.formatted("tags", "= ?"), List.of("a*")),
        Arguments.of("labels.site == \"pil*\"", element.formatted("labels", "= ?"), List.of("site:pil*")),
        Arguments.of("labels[\"a.b\"] == \"x\"", element.formatted("labels", "= ?"), List.of("a.b:x")),
        Arguments.of("labels.site.endsWith(\"sen\")", element.formatted("labels", "LIKE ? ESCAPE '!'"), List.of("site:%sen")),
        Arguments.of("kv.site.startsWith(\"pil\")",
            "(EXISTS (SELECT 1 FROM labels aquery_kv WHERE aquery_kv.owner_id = T.id AND aquery_kv.label_key = ? AND aquery_kv.label_value LIKE ? ESCAPE '!'))",
            List.of("site", "pil%")),
        Arguments.of("age <= duration(\"1h30m15.5s\")", "(T.db_age <= ?)", List.of(5_415_500_000_000L)),
        Arguments.of("age > duration(\"-300ms\")", "(T.db_age > ?)", List.of(-300_000_000L)),
        Arguments.of("age == duration(\"0\")", "(T.db_age = ?)", List.of(0L)),
        Arguments.of("count == 7u", "(T.db_count = ?)", List.of(7L)));
  }

  @Test
  @DisplayName("gives a flat tree for a chain of && and ||")
  void tree() {
    assertThat(CEL.parse("count > 1 && count < 5 && (flag || name == \"x\" || \"a\" in tags)"))
        .hasToString("filter{and{restriction{\"count\",\">\",int{1}},restriction{\"count\",\"<\",int{5}},"
            + "or{restriction{\"flag\",\"=\",bool{true}},restriction{\"name\",\"=\",string{\"x\"}},"
            + "restriction{\"tags\",\"in\",string{\"a\"}}}}}");
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "", "   ", "\n\t" })
  @DisplayName("blank text is an empty filter")
  void blank(String filter) {
    assertThat(cel(filter)).isEqualTo(new Compiled("(1 = 1)", List.of()));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      name == | invalid CEL expression
      name = "a" | invalid CEL expression
      size(tags) > 1 | expected a field on one side of '>' but found the function 'size' and a constant
      count + 1 == 2 | expected a field on one side of '==' but found the operator '+' and a constant
      1 == 2 | expected a field on one side of '=='
      name.matches("a.*") | matches() is not supported
      name == null | null is not supported
      name == b"x" | bytes are not supported
      tags.exists(t, t == "a") | the function 'exists' is not supported in a filter
      flag ? count == 1 : count == 2 | the conditional operator '? :' is not supported
      status in [] | the list after 'in' is empty
      count == [1] | a list is only allowed after 'in'
      "prod" | expected a condition but found a constant
      "a".startsWith(name) | expected a field before .startsWith()
      age > duration("1 hour") | '1 hour' is not a valid duration
      age > duration("99999999999h") | is too long a duration
      create_time > timestamp("yesterday") | 'yesterday' is not a valid RFC 3339 timestamp
      count == 18446744073709551615u | does not fit in 64 bits
      """)
  @DisplayName("refuses CEL that a WHERE clause cannot answer")
  void refusesAtParse(String filter, String expectedMessage) {
    assertThatThrownBy(() -> CEL.parse(filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining(expectedMessage)
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).field()).isEqualTo(InvalidQueryException.FILTER));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      count == 2.5 | expected an integer literal but found '2.5'
      name == other | possible field reference 'other'
      flag == "true" | double-quoted string 'true'
      status == "ACTIVE" | expected an unquoted enum value
      status == UNKNOWN | 'UNKNOWN' is not one of the valid Status values
      age > 5 | expected a duration like 1.2s
      nosuch == 1 | no filterable field 'nosuch'
      flag.startsWith("t") | operator 'startsWith' not implemented for field 'flag' of type BOOL
      "x" in name | operator 'in' not implemented for field 'name' of type STRING
      "x" in labels.site | the operator 'in' needs the key-value field itself
      """)
  @DisplayName("refuses values that do not fit the type of the field")
  void refusesAtCompile(String filter, String expectedMessage) {
    Filter parsed = CEL.parse(filter);

    assertThatThrownBy(() -> WhereClause.of(TABLE, parsed, "T", new Parameters(ParameterStyle.QUESTION_MARK)))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest(name = "[{0}] fails at {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      name == "a" && size(tags) > 1 | >
      name == "a" && name.matches("x") | (
      flag && count == null | n
      """)
  @DisplayName("says where in the text the expression is")
  void errorPositions(String filter, String expectedCharacter) {
    assertThatThrownBy(() -> CEL.parse(filter))
        .isInstanceOf(InvalidQueryException.class)
        .satisfies(thrown -> {
          int position = ((InvalidQueryException) thrown).position();
          assertThat(position).isBetween(0, filter.length() - 1);
          assertThat(filter.substring(position, position + 1)).isEqualTo(expectedCharacter);
        });
  }

  @Test
  @DisplayName("gives a syntax error the offset where cel-java stopped")
  void syntaxErrorPosition() {
    assertThatThrownBy(() -> CEL.parse("name == \"a\" &&"))
        .isInstanceOf(InvalidQueryException.class)
        .satisfies(thrown -> assertThat(((InvalidQueryException) thrown).position()).isGreaterThan(0));
  }

  @Test
  @DisplayName("compiles a long chain of conditions and refuses deep nesting without a stack overflow")
  void limits() {
    String chain = "count == 1 && ".repeat(1000) + "count == 1";
    String deep = "(".repeat(Filter.MAX_LENGTH / 2 - 10) + "flag" + ")".repeat(Filter.MAX_LENGTH / 2 - 10);

    assertThat(cel(chain).values()).hasSize(1001);
    assertThatThrownBy(() -> CEL.parse(deep)).isInstanceOf(InvalidQueryException.class);
  }

  @Test
  @DisplayName("refuses text longer than the limit")
  void tooLong() {
    String filter = "name == \"" + "x".repeat(Filter.MAX_LENGTH) + "\"";

    assertThatThrownBy(() -> CEL.parse(filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("too long");
  }
}
