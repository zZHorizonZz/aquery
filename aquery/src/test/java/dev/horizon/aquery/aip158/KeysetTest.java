package dev.horizon.aquery.aip158;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip160.BoolColumn;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend.ColumnReferences;
import dev.horizon.aquery.aip160.FieldBackend.SortKey;
import dev.horizon.aquery.aip160.SimpleColumn;
import dev.horizon.aquery.aip160.StringColumn;
import dev.horizon.aquery.aip160.TimestampColumn;
import dev.horizon.aquery.aip160.WhereClause;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeysetTest {

  private final DatabaseTable table = new DatabaseTable(
      new Field.Builder("create_time").backend(new TimestampColumn("create_time")).sortable().build(),
      new Field.Builder("id").backend(new StringColumn("id")).sortable().build(),
      new Field.Builder("archived").backend(new BoolColumn("archived")).sortable().build(),
      new Field.Builder("rank").backend(new TwoKeys("rank")).sortable().build(),
      new Field.Builder("name").backend(new StringColumn("name")).filterable().build());

  @Test
  @DisplayName("resumes after the cursor row, direction by direction")
  void resumesAfterTheCursor() {
    Keyset keyset = new Keyset(table,
        List.of(new OrderBy(new FieldPath("create_time"), true), new OrderBy(new FieldPath("id"), false)));

    WhereClause.Result result = keyset.after(List.of("1335022200000000", "abc"), "T", "k_");

    assertThat(result.sql())
        .isEqualTo("((T.create_time < 1335022200000000) OR (T.create_time = 1335022200000000 AND T.id > @k_0))");
    assertThat(result.parameters()).containsExactly(new WhereClause.QueryParameter("k_0", "abc"));
  }

  @Test
  @DisplayName("writes numbers and booleans as literals of their type")
  void typedCursorValues() {
    Keyset keyset = new Keyset(table, List.of(new OrderBy(new FieldPath("archived"), false)));

    assertThat(keyset.after(List.of("true"), null, "k").sql()).isEqualTo("((archived > TRUE))");
  }

  @Test
  @DisplayName("a string cursor value is only ever a bound value")
  void injection() {
    Keyset keyset = new Keyset(table, List.of(new OrderBy(new FieldPath("id"), false)));

    WhereClause.Result result = keyset.after(List.of("' OR 1=1 --"), null, "k");

    assertThat(result.sql()).isEqualTo("((id > @k0))");
    assertThat(result.parameters()).containsExactly(new WhereClause.QueryParameter("k0", "' OR 1=1 --"));
  }

  @Test
  @DisplayName("refuses a cursor that does not fit the order, as a page token error")
  void refusesMismatchedCursors() {
    Keyset keyset = new Keyset(table, List.of(new OrderBy(new FieldPath("create_time"), false)));

    assertThatThrownBy(() -> keyset.after(List.of("1", "2"), null, "k")).isInstanceOf(InvalidPageTokenException.class);
    assertThatThrownBy(() -> keyset.after(List.of("not a number"), null, "k"))
        .isInstanceOf(InvalidPageTokenException.class)
        .hasMessageContaining("create_time");
  }

  @Test
  @DisplayName("refuses an order it cannot resume")
  void refusesUnresumableOrders() {
    assertThatThrownBy(() -> new Keyset(table, List.of(new OrderBy(new FieldPath("name"), false))))
        .isInstanceOf(InvalidOrderByException.class);
    assertThatThrownBy(() -> new Keyset(table, List.of(new OrderBy(new FieldPath("rank"), false))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one sort key");
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
