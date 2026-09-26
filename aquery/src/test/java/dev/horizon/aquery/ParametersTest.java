package dev.horizon.aquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip158.Keyset;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.StringColumn;
import dev.horizon.aquery.aip160.TimestampColumn;
import dev.horizon.aquery.aip160.WhereClause;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParametersTest {

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("name").backend(new StringColumn("name")).filterable().build(),
      new Field.Builder("create_time").backend(new TimestampColumn("create_time")).sortable().build(),
      new Field.Builder("id").backend(new StringColumn("id")).sortable().build());

  @ParameterizedTest(name = "{0} writes index {1} as [{2}]")
  @MethodSource("placeholders")
  @DisplayName("each style writes its placeholder")
  void styles(ParameterStyle style, int index, String expectedPlaceholder) {
    assertThat(style.placeholder(index)).isEqualTo(expectedPlaceholder);
  }

  static List<Arguments> placeholders() {
    Named<ParameterStyle> questionMark = Named.of("QUESTION_MARK", ParameterStyle.QUESTION_MARK);
    Named<ParameterStyle> dollar = Named.of("DOLLAR", ParameterStyle.DOLLAR);
    Named<ParameterStyle> atP = Named.of("AT_P", ParameterStyle.AT_P);
    Named<ParameterStyle> colon = Named.of("COLON", ParameterStyle.COLON);
    return List.of(
        Arguments.of(questionMark, 0, "?"),
        Arguments.of(questionMark, 7, "?"),
        Arguments.of(dollar, 0, "$1"),
        Arguments.of(dollar, 9, "$10"),
        Arguments.of(atP, 0, "@p1"),
        Arguments.of(atP, 4, "@p5"),
        Arguments.of(colon, 0, ":1"),
        Arguments.of(colon, 2, ":3"));
  }

  @Test
  @DisplayName("binds values in sequence and gives one placeholder for each")
  void binds() {
    Parameters parameters = new Parameters(ParameterStyle.DOLLAR);

    assertThat(parameters.bind("a")).isEqualTo("$1");
    assertThat(parameters.bind(2L)).isEqualTo("$2");
    assertThat(parameters.bind("a")).isEqualTo("$3");
    assertThat(parameters.size()).isEqualTo(3);
    assertThat(parameters.values()).containsExactly("a", 2L, "a");
  }

  @Test
  @DisplayName("the values are an unmodifiable copy")
  void valuesAreACopy() {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
    parameters.bind("a");

    List<Object> values = parameters.values();
    parameters.bind("b");

    assertThat(values).containsExactly("a");
    assertThatThrownBy(() -> values.add("c")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("refuses a null value and a null style")
  void refusesNull() {
    assertThatThrownBy(() -> new Parameters(ParameterStyle.QUESTION_MARK).bind(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Parameters(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("a where clause and a keyset share one numbering")
  void numberingContinuesAcrossClauses() {
    Parameters parameters = new Parameters(ParameterStyle.DOLLAR);
    List<OrderBy> order = List.of(new OrderBy(new FieldPath("create_time"), true), new OrderBy(new FieldPath("id"), false));

    String where = WhereClause.of(table, Filter.parse("name = \"dan\" OR name : \"eva\""), "T", parameters);
    String after = new Keyset(table, order).after(List.of("2012-04-21T15:30:00Z", "abc"), "T", parameters);

    OffsetDateTime created = OffsetDateTime.of(2012, 4, 21, 15, 30, 0, 0, ZoneOffset.UTC);
    assertThat(where).isEqualTo("((T.name = $1) OR (T.name LIKE $2 ESCAPE '!'))");
    assertThat(after).isEqualTo("((T.create_time < $3) OR (T.create_time = $4 AND T.id > $5))");
    assertThat(parameters.values()).containsExactly("dan", "%eva%", created, created, "abc");
  }

  @Test
  @DisplayName("with question marks, the values follow the placeholders of the statement")
  void questionMarksFollowTheText() {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
    List<OrderBy> order = List.of(new OrderBy(new FieldPath("create_time"), false), new OrderBy(new FieldPath("id"), false));

    String statement = "SELECT T.id FROM things T WHERE "
        + WhereClause.of(table, Filter.parse("name = \"dan\""), "T", parameters) + " AND "
        + new Keyset(table, order).after(List.of("2012-04-21T15:30:00Z", "abc"), "T", parameters);

    assertThat(statement.chars().filter(character -> character == '?').count()).isEqualTo(parameters.size());
    assertThat(parameters.values()).hasSize(4).startsWith("dan").endsWith("abc");
  }
}
