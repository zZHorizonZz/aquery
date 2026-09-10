package dev.horizon.aquery.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByClause;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnsTest {

  private final DatabaseTable strings = new DatabaseTable(
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build(),
      new Field.Builder("bar").backend(new StringColumn("db_bar")).filterableImplicitly().build());

  private final DatabaseTable keyValues = new DatabaseTable(
      new Field.Builder("kv").backend(new KeyValueColumn("db_kv", Representation.REPEATED_STRUCT)).filterable().build(),
      new Field.Builder("sakv").backend(new KeyValueColumn("db_sakv", Representation.STRING_ARRAY)).filterable().build(),
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build());

  private final DatabaseTable scalars = new DatabaseTable(
      new Field.Builder("flag").backend(new BoolColumn("db_flag")).filterable().sortable().build(),
      new Field.Builder("count").backend(new IntegerColumn("db_count")).filterable().sortable().build(),
      new Field.Builder("age").backend(new DurationColumn("db_age")).filterable().build(),
      new Field.Builder("create_time").backend(new TimestampColumn("db_create_time")).filterable().sortable().build(),
      new Field.Builder("status")
          .backend(new EnumColumn("db_status", new Args.EnumDefinition("Status", Map.of("ACTIVE", 1, "INACTIVE", 2), 0)))
          .filterable()
          .build(),
      new Field.Builder("tags").backend(new RepeatedStringColumn("db_tags")).filterable().build(),
      new Field.Builder("hashed").backend(new OpaqueStringColumn("db_hashed", value -> "[" + value + "]")).filterable()
          .build());

  private String sql(DatabaseTable table, String filter) {
    return WhereClause.of(table, Filter.parse(filter), "T", "p_").sql();
  }

  private List<WhereClause.QueryParameter> parameters(DatabaseTable table, String filter) {
    return WhereClause.of(table, Filter.parse(filter), "T", "p_").parameters();
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', textBlock = """
      foo:"somevalue"    | (T.db_foo LIKE @p_0)
      foo = "somevalue"  | (T.db_foo = @p_0)
      foo != "somevalue" | (T.db_foo <> @p_0)
      foo < "m"          | (T.db_foo < @p_0)
      foo >= "m"         | (T.db_foo >= @p_0)
      foo = "*.com"      | (T.db_foo LIKE @p_0)
      foo != "prod*"     | (T.db_foo NOT LIKE @p_0)
      """)
  void stringColumns(String filter, String expectedSql) {
    assertThat(sql(strings, filter)).isEqualTo(expectedSql);
  }

  @ParameterizedTest(name = "[{0}] binds [{1}]")
  @CsvSource(delimiter = '|', textBlock = """
      foo = "*.com"      | %.com
      foo = "prod*"      | prod%
      foo = "*prod*"     | %prod%
      foo = "*"          | %
      foo = "a*b"        | a*b
      foo = "*50%_off"   | %50\\%\\_off
      """)
  void stringWildcards(String filter, String expectedParameter) {
    assertThat(parameters(strings, filter)).containsExactly(new WhereClause.QueryParameter("p_0", expectedParameter));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "foo:(\"somevalue\")", "foo=(somevalue)" })
  void stringColumnsRefuseComposites(String filter) {
    assertThatThrownBy(() -> sql(strings, filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("argument for field 'foo': composite expressions in arguments");
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("keyValueQueries")
  void keyValueColumns(String filter, String expectedSql) {
    assertThat(sql(keyValues, filter)).isEqualTo(expectedSql);
  }

  static List<Arguments> keyValueQueries() {
    return List.of(
        Arguments.of("kv.key:\"somevalue\"",
            "(EXISTS (SELECT _v.key, _v.value FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0 AND _v.value LIKE @p_1))"),
        Arguments.of("kv.key=\"somevalue\"",
            "(EXISTS (SELECT _v.key, _v.value FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0 AND _v.value = @p_1))"),
        Arguments.of("kv.key!=\"somevalue\"",
            "(EXISTS (SELECT _v.key, _v.value FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0 AND _v.value <> @p_1))"),
        Arguments.of("kv.key=\"some*\"",
            "(EXISTS (SELECT _v.key, _v.value FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0 AND _v.value LIKE @p_1))"),
        Arguments.of("sakv.key:\"somevalue\"", "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) as _v WHERE _v LIKE @p_0))"),
        Arguments.of("sakv.key=\"somevalue\"", "(@p_0 IN UNNEST(T.db_sakv))"),
        Arguments.of("sakv.key!=\"somevalue\"",
            "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) as _v WHERE STARTS_WITH(_v, @p_1) AND _v <> @p_0))"),
        Arguments.of("sakv.key=\"*value\"", "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) as _v WHERE _v LIKE @p_0))"),
        Arguments.of("kv:*", "(ARRAY_LENGTH(T.db_kv) > 0)"),
        Arguments.of("sakv:*", "(ARRAY_LENGTH(T.db_sakv) > 0)"),
        Arguments.of("kv:key", "(EXISTS (SELECT 1 FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0))"),
        Arguments.of("kv:\"key\"", "(EXISTS (SELECT 1 FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0))"),
        Arguments.of("sakv:key", "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) as _v WHERE STARTS_WITH(_v, @p_0)))"),
        Arguments.of("kv.key:*", "(EXISTS (SELECT 1 FROM UNNEST(T.db_kv) as _v WHERE _v.key = @p_0))"),
        Arguments.of("sakv.key:*", "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) as _v WHERE STARTS_WITH(_v, @p_0)))"));
  }

  @ParameterizedTest(name = "[{0}] binds {1}")
  @MethodSource("keyValueParameters")
  void keyValueParameters(String filter, List<String> expectedValues) {
    assertThat(parameters(keyValues, filter)).extracting(WhereClause.QueryParameter::value).isEqualTo(expectedValues);
  }

  static List<Arguments> keyValueParameters() {
    return List.of(
        Arguments.of("sakv.key:\"somevalue\"", List.of("key:%somevalue%")),
        Arguments.of("sakv.a_b%:\"v\"", List.of("a\\_b\\%:%v%")),
        Arguments.of("sakv.key!=\"somevalue\"", List.of("key:somevalue", "key:")),
        Arguments.of("sakv.key=\"*value\"", List.of("key:%value")),
        Arguments.of("sakv:key", List.of("key:")),
        Arguments.of("kv.key=\"some*\"", List.of("key", "some%")));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "kv = \"somevalue\"", "sakv != somevalue" })
  void keyValueNeedsAKey(String filter) {
    assertThatThrownBy(() -> sql(keyValues, filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("key value columns must specify the key to search on");
  }

  @Test
  void stringArrayKeysCannotHoldTheSeparator() {
    assertThatThrownBy(() -> sql(keyValues, "sakv.\"a:b\" = \"c\""))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("cannot contain ':'");
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "kv.key < \"x\"", "kv.key >= \"x\"", "sakv.key > \"x\"" })
  void keyValueColumnsRefuseOrderings(String filter) {
    assertThatThrownBy(() -> sql(keyValues, filter))
        .isInstanceOf(OperatorNotImplementedException.class)
        .hasMessageContaining("of type KEY-VALUE");
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({
      "'foo.baz=blah',  fields are only supported for key-value columns",
      "'foo=blah.baz',   did you mean to wrap the value in quotes?"
  })
  void nestedFieldsOnlyOnKeyValueColumns(String filter, String expectedMessage) {
    assertThatThrownBy(() -> sql(keyValues, filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({
      "'flag = true',      (T.db_flag = TRUE)",
      "'flag != false',    (T.db_flag <> FALSE)",
      "'count = 3',        (T.db_count = 3)",
      "'count > 3',        (T.db_count > 3)",
      "'count <= 3',       (T.db_count <= 3)",
      "'count != 3',       (T.db_count <> 3)",
      "'count > -30',      (T.db_count > -30)",
      "'age = 1.5s',       (T.db_age = 1500000000)",
      "'age < 1s',         (T.db_age < 1000000000)",
      "'age != 1s',        (T.db_age <> 1000000000)",
      "'status = ACTIVE',  (T.db_status = 1)",
      "'status != INACTIVE', (T.db_status <> 2)",
      "'hashed = \"x\"',   (T.db_hashed = @p_0)",
      "'create_time > \"2012-04-21T11:30:00-04:00\"', (T.db_create_time > 1335022200000000)",
      "'create_time = \"2012-04-21T15:30:00Z\"', (T.db_create_time = 1335022200000000)",
      "'create_time != \"2012-04-21T15:30:00Z\"', (T.db_create_time <> 1335022200000000)"
  })
  void scalarColumns(String filter, String expectedSql) {
    assertThat(sql(scalars, filter)).isEqualTo(expectedSql);
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("unsupportedOperators")
  void columnsRefuseOperatorsTheyCannotAnswer(String filter, String expectedMessage) {
    assertThatThrownBy(() -> sql(scalars, filter))
        .isInstanceOf(OperatorNotImplementedException.class)
        .hasMessageContaining(expectedMessage);
  }

  static List<Arguments> unsupportedOperators() {
    return List.of(
        Arguments.of("flag > true", "not implemented for field 'flag' of type BOOL"),
        Arguments.of("count : 3", "not implemented for field 'count' of type INTEGER"),
        Arguments.of("status < ACTIVE", "not implemented for field 'status' of type Status"),
        Arguments.of("tags != \"prod\"", "not implemented for field 'tags' of type REPEATED STRING"),
        Arguments.of("hashed : \"x\"", "not implemented for field 'hashed' of type OPAQUE STRING"),
        Arguments.of("create_time : \"2012-04-21T11:30:00Z\"", "not implemented for field 'create_time' of type TIMESTAMP"));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', textBlock = """
      tags : "prod" | (EXISTS (SELECT value FROM UNNEST(T.db_tags) as value WHERE value LIKE @p_0))
      tags = "prod" | (EXISTS (SELECT value FROM UNNEST(T.db_tags) as value WHERE value = @p_0))
      tags = "pro*" | (EXISTS (SELECT value FROM UNNEST(T.db_tags) as value WHERE value LIKE @p_0))
      tags : *      | (ARRAY_LENGTH(T.db_tags) > 0)
      """)
  void repeatedStringColumns(String filter, String expectedSql) {
    assertThat(sql(scalars, filter)).isEqualTo(expectedSql);
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("impossibleDeclarations")
  void schemaRefusesWhatTheBackendCannotDo(Field.Builder declaration, String expectedMessage) {
    assertThatThrownBy(declaration::build).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(expectedMessage);
  }

  static List<Arguments> impossibleDeclarations() {
    return List.of(
        Arguments.of(new Field.Builder("tags").backend(new RepeatedStringColumn("db_tags")).sortable(),
            "backend does not support sorting"),
        Arguments.of(new Field.Builder("hashed").backend(new OpaqueStringColumn("db_hashed", value -> value)).sortable(),
            "backend does not support sorting"),
        Arguments.of(new Field.Builder("count").backend(new IntegerColumn("db_count")).filterableImplicitly(),
            "backend does not match bare values"),
        Arguments.of(new Field.Builder("plain").backend(new SimpleColumn("db_plain")).filterable(),
            "backend supports no operators"));
  }

  @Test
  void orderByUsesTheSortKeysOfTheBackend() {
    assertThat(OrderByClause.of(scalars, List.of(new OrderBy(new FieldPath("count"), true)), "T"))
        .isEqualTo("ORDER BY T.db_count DESC\n");
  }

  @ParameterizedTest(name = "encodes [{0}] before it binds")
  @CsvSource({ "'hashed = \"x\"', [x]", "'hashed = \"a b\"', [a b]", "'hashed = \"a*\"', [a*]" })
  void opaqueStringEncodesBeforeBinding(String filter, String expectedBoundValue) {
    assertThat(parameters(scalars, filter)).containsExactly(new WhereClause.QueryParameter("p_0", expectedBoundValue));
  }
}
