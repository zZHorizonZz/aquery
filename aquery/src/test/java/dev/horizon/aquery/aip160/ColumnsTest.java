package dev.horizon.aquery.aip160;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByClause;
import dev.horizon.aquery.aip160.EnumColumn.Storage;
import dev.horizon.aquery.aip160.KeyValueColumn.ChildTable;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnsTest {

  private static final Args.EnumDefinition STATUS = new Args.EnumDefinition("Status", Map.of("ACTIVE", 1, "INACTIVE", 2), 0);

  private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  private final DatabaseTable strings = new DatabaseTable(
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build(),
      new Field.Builder("bar").backend(new StringColumn("db_bar")).filterableImplicitly().build());

  private final DatabaseTable keyValues = new DatabaseTable(
      new Field.Builder("kv")
          .backend(new KeyValueColumn(new ChildTable("labels", "owner_id", "label_key", "label_value", "id")))
          .filterable()
          .build(),
      new Field.Builder("sakv").backend(new KeyValueColumn("db_sakv", Representation.STRING_ARRAY)).filterable().build(),
      new Field.Builder("foo").backend(new StringColumn("db_foo")).filterableImplicitly().build());

  private final DatabaseTable scalars = new DatabaseTable(
      new Field.Builder("flag").backend(new BoolColumn("db_flag")).filterable().sortable().build(),
      new Field.Builder("count").backend(new IntegerColumn("db_count")).filterable().sortable().build(),
      new Field.Builder("age").backend(new DurationColumn("db_age")).filterable().build(),
      new Field.Builder("create_time").backend(new TimestampColumn("db_create_time")).filterable().sortable().build(),
      new Field.Builder("status").backend(new EnumColumn("db_status", STATUS)).filterable().build(),
      new Field.Builder("status_name").backend(new EnumColumn("db_status_name", STATUS, Storage.NAME)).filterable().build(),
      new Field.Builder("tags").backend(new RepeatedStringColumn("db_tags")).filterable().build(),
      new Field.Builder("hashed").backend(new OpaqueStringColumn("db_hashed", value -> "[" + value + "]")).filterable()
          .build(),
      new Field.Builder("uid").backend(new UuidColumn("db_uid")).filterable().sortable().readable().build());

  private record Compiled(String sql, List<Object> values) {
  }

  private static Compiled compile(DatabaseTable table, String filter) {
    Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
    String sql = WhereClause.of(table, Filter.parse(filter), "T", parameters);
    return new Compiled(sql, parameters.values());
  }

  private static String sql(DatabaseTable table, String filter) {
    return compile(table, filter).sql();
  }

  private static List<Object> values(DatabaseTable table, String filter) {
    return compile(table, filter).values();
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("stringQueries")
  void stringColumns(String filter, String expectedSql, List<Object> expectedValues) {
    Compiled compiled = compile(strings, filter);

    assertThat(compiled.sql()).isEqualTo(expectedSql);
    assertThat(compiled.values()).isEqualTo(expectedValues);
  }

  static List<Arguments> stringQueries() {
    return List.of(
        Arguments.of("foo:\"somevalue\"", "(T.db_foo LIKE ? ESCAPE '!')", List.of("%somevalue%")),
        Arguments.of("foo = \"somevalue\"", "(T.db_foo = ?)", List.of("somevalue")),
        Arguments.of("foo != \"somevalue\"", "(T.db_foo <> ?)", List.of("somevalue")),
        Arguments.of("foo < \"m\"", "(T.db_foo < ?)", List.of("m")),
        Arguments.of("foo >= \"m\"", "(T.db_foo >= ?)", List.of("m")),
        Arguments.of("foo = \"*.com\"", "(T.db_foo LIKE ? ESCAPE '!')", List.of("%.com")),
        Arguments.of("foo != \"prod*\"", "(T.db_foo NOT LIKE ? ESCAPE '!')", List.of("prod%")),
        Arguments.of("bare", "((T.db_foo LIKE ? ESCAPE '!') OR (T.db_bar LIKE ? ESCAPE '!'))", List.of("%bare%", "%bare%")));
  }

  @ParameterizedTest(name = "[{0}] binds [{1}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      foo = "*.com" | %.com
      foo = "prod*" | prod%
      foo = "*prod*" | %prod%
      foo = "*" | %
      foo = "a*b" | a*b
      foo = "*50%_off" | %50!%!_off
      foo = "*wow!" | %wow!!
      """)
  void stringWildcards(String filter, String expectedValue) {
    assertThat(values(strings, filter)).containsExactly(expectedValue);
  }

  @ParameterizedTest(name = "[{0}] binds {1}")
  @MethodSource("likeEscapes")
  @DisplayName("LIKE patterns escape '!', '%' and '_' with '!'")
  void likeEscapes(String filter, List<Object> expectedValues) {
    Compiled compiled = compile(keyValues, filter);

    assertThat(compiled.sql()).contains("ESCAPE '!'").doesNotContain("\\");
    assertThat(compiled.values()).isEqualTo(expectedValues);
  }

  static List<Arguments> likeEscapes() {
    return List.of(
        Arguments.of("foo : \"100%\"", List.of("%100!%%")),
        Arguments.of("foo : \"a_b\"", List.of("%a!_b%")),
        Arguments.of("foo : \"hey!\"", List.of("%hey!!%")),
        Arguments.of("foo : \"!%_\"", List.of("%!!!%!_%")),
        Arguments.of("sakv.\"a_b%!\" : \"v\"", List.of("a!_b!%!!:%v%")),
        Arguments.of("sakv.\"a_b\" : *", List.of("a!_b:%")),
        Arguments.of("sakv.key = \"50%*\"", List.of("key:50!%%")),
        Arguments.of("kv.key : \"5%\"", List.of("key", "%5!%%")),
        Arguments.of("kv.key = \"a_*\"", List.of("key", "a!_%")));
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
  void keyValueColumns(String filter, String expectedSql, List<Object> expectedValues) {
    Compiled compiled = compile(keyValues, filter);

    assertThat(compiled.sql()).isEqualTo(expectedSql);
    assertThat(compiled.values()).isEqualTo(expectedValues);
  }

  static List<Arguments> keyValueQueries() {
    String child = "(EXISTS (SELECT 1 FROM labels aquery_kv WHERE aquery_kv.owner_id = T.id";
    String array = "(EXISTS (SELECT 1 FROM UNNEST(T.db_sakv) AS aquery_u(aquery_v) WHERE ";
    return List.of(
        Arguments.of("kv.key:\"somevalue\"",
            child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value LIKE ? ESCAPE '!'))",
            List.of("key", "%somevalue%")),
        Arguments.of("kv.key=\"somevalue\"", child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value = ?))",
            List.of("key", "somevalue")),
        Arguments.of("kv.key!=\"somevalue\"", child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value <> ?))",
            List.of("key", "somevalue")),
        Arguments.of("kv.key=\"some*\"",
            child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value LIKE ? ESCAPE '!'))", List.of("key", "some%")),
        Arguments.of("kv.key!=\"*some\"",
            child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value NOT LIKE ? ESCAPE '!'))",
            List.of("key", "%some")),
        Arguments.of("kv.\"a:b\"=\"c\"", child + " AND aquery_kv.label_key = ? AND aquery_kv.label_value = ?))",
            List.of("a:b", "c")),
        Arguments.of("kv:*", child + "))", List.of()),
        Arguments.of("kv:key", child + " AND aquery_kv.label_key = ?))", List.of("key")),
        Arguments.of("kv:\"key\"", child + " AND aquery_kv.label_key = ?))", List.of("key")),
        Arguments.of("kv.key:*", child + " AND aquery_kv.label_key = ?))", List.of("key")),
        Arguments.of("sakv.key:\"somevalue\"", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("key:%somevalue%")),
        Arguments.of("sakv.key=\"somevalue\"", array + "aquery_v = ?))", List.of("key:somevalue")),
        Arguments.of("sakv.key!=\"somevalue\"", array + "aquery_v LIKE ? ESCAPE '!' AND aquery_v <> ?))",
            List.of("key:%", "key:somevalue")),
        Arguments.of("sakv.key!=\"some*\"", array + "aquery_v LIKE ? ESCAPE '!' AND aquery_v NOT LIKE ? ESCAPE '!'))",
            List.of("key:%", "key:some%")),
        Arguments.of("sakv.key=\"*value\"", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("key:%value")),
        Arguments.of("sakv:*", "(CARDINALITY(T.db_sakv) > 0)", List.of()),
        Arguments.of("sakv:key", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("key:%")),
        Arguments.of("sakv.key:*", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("key:%")));
  }

  @Test
  @DisplayName("a child table without a table alias refers to the plain column")
  void childTableWithoutAlias() {
    Parameters parameters = new Parameters(ParameterStyle.DOLLAR);

    assertThat(WhereClause.of(keyValues, Filter.parse("kv.site = \"pilsen\""), null, parameters)).isEqualTo(
        "(EXISTS (SELECT 1 FROM labels aquery_kv WHERE aquery_kv.owner_id = id AND aquery_kv.label_key = $1 AND aquery_kv.label_value = $2))");
    assertThat(parameters.values()).containsExactly("site", "pilsen");
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

  @ParameterizedTest(name = "child table [{0}]")
  @ValueSource(strings = { "labels; DROP TABLE x", "1labels", "", "a.b" })
  @DisplayName("the names of a child table must be SQL identifiers")
  void childTableNamesAreIdentifiers(String name) {
    assertThatThrownBy(() -> new ChildTable(name, "owner_id", "k", "v", "id")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the table of a child table");
    assertThatThrownBy(() -> new ChildTable("labels", "owner_id", "k", name, "id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the valueColumn of a child table");
    assertThatThrownBy(() -> new ChildTable("labels", "owner_id", "k", "v", null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a child table has its own constructor")
  void childTableConstructor() {
    assertThatThrownBy(() -> new KeyValueColumn("labels", Representation.CHILD_TABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("KeyValueColumn(ChildTable)");
    assertThat(new KeyValueColumn(new ChildTable("labels", "owner_id", "k", "v", "id")).representation())
        .isEqualTo(Representation.CHILD_TABLE);
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource({
      "'foo.baz=blah', fields are only supported for key-value columns",
      "'foo=blah.baz', did you mean to wrap the value in quotes?"
  })
  void nestedFieldsOnlyOnKeyValueColumns(String filter, String expectedMessage) {
    assertThatThrownBy(() -> sql(keyValues, filter))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("scalarQueries")
  void scalarColumns(String filter, String expectedSql, Object expectedValue) {
    Compiled compiled = compile(scalars, filter);

    assertThat(compiled.sql()).isEqualTo(expectedSql);
    assertThat(compiled.values()).containsExactly(expectedValue);
    assertThat(compiled.values().getFirst()).isExactlyInstanceOf(expectedValue.getClass());
  }

  static List<Arguments> scalarQueries() {
    OffsetDateTime instant = OffsetDateTime.of(2012, 4, 21, 15, 30, 0, 0, ZoneOffset.UTC);
    return List.of(
        Arguments.of("flag = true", "(T.db_flag = ?)", true),
        Arguments.of("flag != false", "(T.db_flag <> ?)", false),
        Arguments.of("count = 3", "(T.db_count = ?)", 3L),
        Arguments.of("count > 3", "(T.db_count > ?)", 3L),
        Arguments.of("count <= 3", "(T.db_count <= ?)", 3L),
        Arguments.of("count != 3", "(T.db_count <> ?)", 3L),
        Arguments.of("count > -30", "(T.db_count > ?)", -30L),
        Arguments.of("age = 1.5s", "(T.db_age = ?)", 1_500_000_000L),
        Arguments.of("age < 1s", "(T.db_age < ?)", 1_000_000_000L),
        Arguments.of("age != 1s", "(T.db_age <> ?)", 1_000_000_000L),
        Arguments.of("status = ACTIVE", "(T.db_status = ?)", 1L),
        Arguments.of("status != INACTIVE", "(T.db_status <> ?)", 2L),
        Arguments.of("status_name = ACTIVE", "(T.db_status_name = ?)", "ACTIVE"),
        Arguments.of("status_name != INACTIVE", "(T.db_status_name <> ?)", "INACTIVE"),
        Arguments.of("hashed = \"x\"", "(T.db_hashed = ?)", "[x]"),
        Arguments.of("create_time > \"2012-04-21T11:30:00-04:00\"", "(T.db_create_time > ?)", instant),
        Arguments.of("create_time = \"2012-04-21T15:30:00Z\"", "(T.db_create_time = ?)", instant),
        Arguments.of("create_time != \"2012-04-21t15:30:00.5z\"", "(T.db_create_time <> ?)", instant.plusNanos(500_000_000)),
        Arguments.of("uid = \"123e4567-e89b-12d3-a456-426614174000\"", "(T.db_uid = ?)", ID),
        Arguments.of("uid != \"123E4567-E89B-12D3-A456-426614174000\"", "(T.db_uid <> ?)", ID));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      uid = "not-a-uuid" | argument for field 'uid': 'not-a-uuid' is not a valid UUID
      uid = "1-1-1-1-1" | '1-1-1-1-1' is not a valid UUID
      uid = "123e4567e89b12d3a456426614174000" | is not a valid UUID
      uid = abc | expected a quoted (") string literal
      status_name = UNKNOWN | 'UNKNOWN' is not one of the valid Status values, expected one of [ACTIVE, INACTIVE]
      status_name = "ACTIVE" | expected an unquoted enum value
      """)
  void columnsRefuseValuesOfTheWrongForm(String filter, String expectedMessage) {
    assertThatThrownBy(() -> sql(scalars, filter))
        .isInstanceOf(InvalidFilterException.class)
        .hasMessageContaining(expectedMessage);
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
        Arguments.of("status_name >= ACTIVE", "not implemented for field 'status_name' of type Status"),
        Arguments.of("tags != \"prod\"", "not implemented for field 'tags' of type REPEATED STRING"),
        Arguments.of("hashed : \"x\"", "not implemented for field 'hashed' of type OPAQUE STRING"),
        Arguments.of("uid < \"123e4567-e89b-12d3-a456-426614174000\"", "not implemented for field 'uid' of type UUID"),
        Arguments.of("create_time : \"2012-04-21T11:30:00Z\"", "not implemented for field 'create_time' of type TIMESTAMP"));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("repeatedStringQueries")
  void repeatedStringColumns(String filter, String expectedSql, List<Object> expectedValues) {
    Compiled compiled = compile(scalars, filter);

    assertThat(compiled.sql()).isEqualTo(expectedSql);
    assertThat(compiled.values()).isEqualTo(expectedValues);
  }

  static List<Arguments> repeatedStringQueries() {
    String array = "(EXISTS (SELECT 1 FROM UNNEST(T.db_tags) AS aquery_u(aquery_v) WHERE ";
    return List.of(
        Arguments.of("tags : \"prod\"", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("%prod%")),
        Arguments.of("tags : \"50%\"", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("%50!%%")),
        Arguments.of("tags = \"prod\"", array + "aquery_v = ?))", List.of("prod")),
        Arguments.of("tags = \"pro*\"", array + "aquery_v LIKE ? ESCAPE '!'))", List.of("pro%")),
        Arguments.of("tags : *", "(CARDINALITY(T.db_tags) > 0)", List.of()));
  }

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = {
      "foo = \"x' OR '1'='1\"",
      "foo : \"x' OR '1'='1\"",
      "foo != \"*x' OR '1'='1\"",
      "sakv.key = \"x' OR '1'='1\"",
      "sakv.key != \"x' OR '1'='1\"",
      "sakv.\"x' OR '1'='1\" : *",
      "kv.\"x' OR '1'='1\" = \"x' OR '1'='1\"",
      "kv : \"x' OR '1'='1\"",
      "\"x' OR '1'='1\""
  })
  @DisplayName("client text in any backend is only ever a bound value")
  void clientTextNeverReachesTheSql(String filter) {
    Compiled compiled = compile(keyValues, filter);

    assertThat(compiled.sql()).doesNotContain("OR '1'").doesNotContain("x'");
    assertThat(compiled.values()).anySatisfy(value -> assertThat((String) value).contains("x' OR '1'='1"));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("impossibleDeclarations")
  void schemaRefusesWhatTheBackendCannotDo(Field.Builder declaration, String expectedMessage) {
    assertThatThrownBy(declaration::build).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(expectedMessage);
  }

  static List<Arguments> impossibleDeclarations() {
    KeyValueColumn childTable = new KeyValueColumn(new ChildTable("labels", "owner_id", "k", "v", "id"));
    return List.of(
        Arguments.of(new Field.Builder("tags").backend(new RepeatedStringColumn("db_tags")).sortable(),
            "backend does not support sorting"),
        Arguments.of(new Field.Builder("hashed").backend(new OpaqueStringColumn("db_hashed", value -> value)).sortable(),
            "backend does not support sorting"),
        Arguments.of(new Field.Builder("count").backend(new IntegerColumn("db_count")).filterableImplicitly(),
            "backend does not match bare values"),
        Arguments.of(new Field.Builder("plain").backend(new SimpleColumn("db_plain")).filterable(),
            "backend supports no operators"),
        Arguments.of(new Field.Builder("labels").backend(childTable).readable(), "backend does not support reading"),
        Arguments.of(new Field.Builder("labels").backend(childTable).sortable(), "backend does not support sorting"));
  }

  @Test
  void orderByUsesTheSortKeysOfTheBackend() {
    assertThat(OrderByClause.of(scalars, List.of(new OrderBy(new FieldPath("count"), true)), "T"))
        .isEqualTo("ORDER BY T.db_count DESC\n");
  }

  @ParameterizedTest(name = "encodes [{0}] before it binds")
  @CsvSource({ "'hashed = \"x\"', [x]", "'hashed = \"a b\"', [a b]", "'hashed = \"a*\"', [a*]" })
  void opaqueStringEncodesBeforeBinding(String filter, String expectedBoundValue) {
    assertThat(values(scalars, filter)).containsExactly(expectedBoundValue);
  }
}
