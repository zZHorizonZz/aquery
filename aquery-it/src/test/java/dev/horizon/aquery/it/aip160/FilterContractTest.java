package dev.horizon.aquery.it.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip160.FilterParser;
import dev.horizon.aquery.aip160.WhereClause;
import dev.horizon.aquery.ebnf.EbnfFilterParser;
import dev.horizon.aquery.it.ContractTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

abstract class FilterContractTest extends ContractTest {

  private static final FilterParser EBNF = new EbnfFilterParser();

  static Stream<Arguments> scalarFilters() {
    return Stream.of(
        Arguments.of("", List.of(1L, 2L, 3L, 4L, 5L)),
        Arguments.of("flag = true", List.of(1L, 3L, 5L)),
        Arguments.of("flag != true", List.of(2L, 4L)),
        Arguments.of("count = 10", List.of(1L)),
        Arguments.of("count != 10", List.of(2L, 3L, 4L, 5L)),
        Arguments.of("count > 10", List.of(2L, 3L)),
        Arguments.of("count <= 0", List.of(4L, 5L)),
        Arguments.of("count > -30", List.of(1L, 2L, 3L, 4L, 5L)),
        Arguments.of("age > 60s", List.of(2L, 4L)),
        Arguments.of("age = 1.5s", List.of(5L)),
        Arguments.of("age < 1s", List.of(3L)),
        Arguments.of("status = ACTIVE", List.of(1L, 3L, 5L)),
        Arguments.of("status != ACTIVE", List.of(2L, 4L)),
        Arguments.of("status_name = INACTIVE", List.of(2L, 4L)),
        Arguments.of("uid = \"00000000-0000-0000-0000-000000000001\"", List.of(1L)),
        Arguments.of("uid != \"00000000-0000-0000-0000-000000000001\"", List.of(2L, 3L, 4L, 5L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarFilters")
  @DisplayName("compares booleans, integers, durations, enums and UUIDs")
  void scalarFilter(String filter, List<Long> expected) {
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> timestampFilters() {
    return Stream.of(
        Arguments.of("create_time = \"2024-01-01T00:00:00Z\"", List.of(1L)),
        Arguments.of("create_time = \"2024-01-01T01:00:00+01:00\"", List.of(1L)),
        Arguments.of("create_time = \"2024-01-01T00:00:00.5Z\"", List.of(5L)),
        Arguments.of("create_time < \"2024-01-01T00:00:00Z\"", List.of(4L)),
        Arguments.of("create_time >= \"2024-06-01T00:00:00Z\" AND create_time < \"2024-12-31T22:30:00Z\"",
            List.of(3L)),
        Arguments.of("create_time > \"2024-12-31T22:30:00Z\"", List.of(2L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("timestampFilters")
  @DisplayName("compares the instant of a timestamp, not its local time")
  void timestampFilter(String filter, List<Long> expected) {
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> stringFilters() {
    return Stream.of(
        Arguments.of("name = \"alpha\"", List.of(1L)),
        Arguments.of("name != \"alpha\"", List.of(2L, 3L, 4L, 5L)),
        Arguments.of("name : \"ta\"", List.of(2L, 4L)),
        Arguments.of("name = \"a*\"", List.of(1L, 5L)),
        Arguments.of("name = \"*a\"", List.of(1L, 2L, 4L)),
        Arguments.of("name != \"*a\"", List.of(3L, 5L)),
        Arguments.of("elt", List.of(4L)),
        Arguments.of("-name : \"e\"", List.of(1L, 3L, 5L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stringFilters")
  @DisplayName("matches strings with =, != and LIKE")
  void stringFilter(String filter, List<Long> expected) {
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> likeCharacters() {
    return Stream.of(
        Arguments.of("name : \"_\"", List.of(3L)),
        Arguments.of("name : \"%\"", List.of(4L)),
        Arguments.of("name : \"!\"", List.of(5L)),
        Arguments.of("name = \"*0%*\"", List.of(4L)),
        Arguments.of("name = \"a!*\"", List.of(5L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("likeCharacters")
  @DisplayName("finds the LIKE characters _, % and ! as text")
  void likeCharacter(String filter, List<Long> expected) {
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("LIKE ignores case only where the engine ignores case")
  void likeCase() {
    assertThat(ids("name : \"GAMMA\"")).isEqualTo(database.likeIgnoresCase() ? List.of(3L) : List.of());
  }

  @Test
  @DisplayName("= ignores case only where the engine ignores case")
  void equalsCase() {
    assertThat(ids("name = \"ALPHA\"")).isEqualTo(database.equalsIgnoresCase() ? List.of(1L) : List.of());
  }

  static Stream<Arguments> logicFilters() {
    return Stream.of(
        Arguments.of("flag = true AND count > 10", List.of(3L)),
        Arguments.of("flag = false OR status = ACTIVE", List.of(1L, 2L, 3L, 4L, 5L)),
        Arguments.of("NOT flag = true", List.of(2L, 4L)),
        Arguments.of("(count > 0 OR age > 60s) AND NOT flag = true", List.of(2L, 4L)),
        Arguments.of("flag = true count >= 10", List.of(1L, 3L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("logicFilters")
  @DisplayName("joins restrictions with AND, OR and NOT")
  void logicFilter(String filter, List<Long> expected) {
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> keyValueFilters() {
    return Stream.of(
        Arguments.of("%s.env = \"prod\"", List.of(1L)),
        Arguments.of("%s.env != \"prod\"", List.of(2L, 5L)),
        Arguments.of("%s.env = \"pro*\"", List.of(1L, 5L)),
        Arguments.of("%s.env : \"_\"", List.of(5L)),
        Arguments.of("%s.team : *", List.of(1L, 4L)),
        Arguments.of("%s : team", List.of(1L, 4L)),
        Arguments.of("%s : *", List.of(1L, 2L, 4L, 5L)),
        Arguments.of("NOT %s : env", List.of(3L, 4L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("keyValueFilters")
  @DisplayName("finds key-value pairs in a child table")
  void childTableFilter(String filter, List<Long> expected) {
    assertThat(ids(filter.formatted("labels"))).containsExactlyElementsOf(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("keyValueFilters")
  @DisplayName("finds key-value pairs in an array")
  void stringArrayFilter(String filter, List<Long> expected) {
    assumeTrue(items.hasArrays(), "the engine has no arrays");
    assertThat(ids(filter.formatted("attrs"))).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> repeatedFilters() {
    return Stream.of(
        Arguments.of("tags : \"red\"", List.of(1L, 4L)),
        Arguments.of("tags = \"red\"", List.of(1L, 4L)),
        Arguments.of("tags = \"gr*\"", List.of(2L)),
        Arguments.of("tags : *", List.of(1L, 2L, 4L)),
        Arguments.of("NOT tags : *", List.of(3L, 5L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("repeatedFilters")
  @DisplayName("finds elements of a repeated string")
  void repeatedFilter(String filter, List<Long> expected) {
    assumeTrue(items.hasArrays(), "the engine has no arrays");
    assertThat(ids(filter)).containsExactlyElementsOf(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = { "name = \"'; DROP TABLE item; --\"", "labels.\"x' OR '1'='1\" = \"y\"" })
  @DisplayName("binds the text of the client and does not put it in the SQL")
  void injection(String filter) {
    assertThat(ids(filter)).isEmpty();
    assertThat(ids("")).hasSize(5);
  }

  private List<Long> ids(String filter) {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
    String where = WhereClause.of(table, EBNF.parse(filter), "T", parameters);
    return ids("SELECT T.id FROM item T WHERE " + where + " ORDER BY T.id", parameters.values());
  }
}
