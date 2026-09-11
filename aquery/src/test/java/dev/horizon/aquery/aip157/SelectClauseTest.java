package dev.horizon.aquery.aip157;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend;
import dev.horizon.aquery.aip160.IntegerColumn;
import dev.horizon.aquery.aip160.KeyValueColumn;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.aip160.SimpleColumn;
import dev.horizon.aquery.aip160.StringColumn;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SelectClauseTest {

  private final DatabaseTable books = new DatabaseTable(
      new Field.Builder("name").backend(new StringColumn("book_id")).filterable().readable().build(),
      new Field.Builder("title").backend(new StringColumn("title")).readable().build(),
      new Field.Builder("author", "given_name").backend(new StringColumn("author_given")).readable().build(),
      new Field.Builder("author", "family_name").backend(new StringColumn("author_family")).readable().build(),
      new Field.Builder("labels").backend(new KeyValueColumn("labels", Representation.STRING_ARRAY)).readable().build(),
      new Field.Builder("published").backend(new TwoColumns("published")).readable().build(),
      new Field.Builder("pages").backend(new IntegerColumn("pages")).filterable().sortable().build());

  private SelectClause.Result select(String readMask) {
    return SelectClause.of(books, ReadMask.parse(readMask), "T");
  }

  @Test
  @DisplayName("all fields read every readable column, in the sequence of the schema")
  void allFields() {
    SelectClause.Result result = select("*");

    assertThat(result.sql())
        .isEqualTo("T.book_id, T.title, T.author_given, T.author_family, T.labels, T.published_date, T.published_zone");
    assertThat(result.fields()).extracting(field -> field.fieldPath().toString())
        .containsExactly("name", "title", "author.given_name", "author.family_name", "labels", "published");
  }

  @ParameterizedTest(name = "[{0}] reads [{1}]")
  @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
      title,name                  | T.book_id, T.title
      author                      | T.author_given, T.author_family
      author.family_name          | T.author_family
      labels.site                 | T.labels
      labels.`site name`,labels.a | T.labels
      published                   | T.published_date, T.published_zone
      """)
  @DisplayName("reads only the columns of the selected fields")
  void subsets(String readMask, String expectedSql) {
    assertThat(select(readMask).sql()).isEqualTo(expectedSql);
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', textBlock = """
      pages            | no readable field 'pages'
      unknown          | no readable field 'unknown'
      title.subtitle   | no readable field 'title.subtitle'
      author.middle    | no readable field 'author.middle'
      """)
  @DisplayName("refuses a path that selects no readable field, naming the readable fields")
  void refusesUnknownPaths(String readMask, String expectedMessage) {
    assertThatThrownBy(() -> select(readMask))
        .isInstanceOf(InvalidReadMaskException.class)
        .hasMessageContaining(expectedMessage)
        .hasMessageContaining("valid fields are name, title, author.given_name");
  }

  @Test
  @DisplayName("the server adds the fields that it always needs")
  void including() {
    ReadMask mask = ReadMask.parse("title").including(books.fields().getFirst().fieldPath());

    assertThat(SelectClause.of(books, mask, null).sql()).isEqualTo("book_id, title");
  }

  @Test
  @DisplayName("a table without readable fields is a server error")
  void noReadableFields() {
    DatabaseTable hidden = new DatabaseTable(
        new Field.Builder("secret").backend(new StringColumn("secret")).filterable().build());

    assertThatThrownBy(() -> SelectClause.of(hidden, new ReadMask(), null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("the schema refuses a readable field on a backend that cannot read")
  void schemaRefusesUnreadableBackends() {
    assertThatThrownBy(() -> new Field.Builder("computed").backend(new Unreadable()).readable().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("backend does not support reading");
  }

  private static final class TwoColumns extends SimpleColumn {

    TwoColumns(String databaseName) {
      super(databaseName);
    }

    @Override
    public List<String> selectExpressions(ColumnReferences columns) {
      return List.of(column(columns) + "_date", column(columns) + "_zone");
    }
  }

  private static final class Unreadable implements FieldBackend {

    @Override
    public String typeName() {
      return "COMPUTED";
    }

    @Override
    public Set<Operator> operators() {
      return Set.of();
    }

    @Override
    public String restrictionQuery(RestrictionContext restriction, Generator generator) {
      throw new UnsupportedOperationException();
    }
  }
}
