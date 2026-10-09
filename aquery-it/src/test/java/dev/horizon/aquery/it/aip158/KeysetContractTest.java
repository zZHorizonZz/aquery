package dev.horizon.aquery.it.aip158;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.aquery.ParameterStyle;
import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip132.Order;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByClause;
import dev.horizon.aquery.aip158.Keyset;
import dev.horizon.aquery.it.ContractTest;
import dev.horizon.aquery.it.Items.Column;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class KeysetContractTest extends ContractTest {

  private static final List<String> ORDERS = List.of(
      "id",
      "id desc",
      "name",
      "name desc",
      "flag",
      "flag desc, count desc",
      "count",
      "age desc",
      "create_time",
      "create_time desc, flag",
      "status, name",
      "status_name desc",
      "uid desc");

  private record Fetched(List<Long> ids, List<String> cursor) {
  }

  static Stream<Arguments> listings() {
    return ORDERS.stream().flatMap(order -> Stream.of(1, 2, 4).map(pageSize -> Arguments.of(order, pageSize)));
  }

  @ParameterizedTest(name = "{0}, {1} per page")
  @MethodSource("listings")
  @DisplayName("the pages together give each row one time, in the order of the listing")
  void pages(String orderBy, int pageSize) {
    List<OrderBy> order = OrderByClause.mergeWithDefaultOrder(Order.parse("id"), Order.parse(orderBy));
    String orderClause = OrderByClause.of(table, order, "T");
    List<Long> expected = ids("SELECT T.id FROM item T " + orderClause, List.of());
    Keyset keyset = new Keyset(table, order);

    List<Long> listed = new ArrayList<>();
    List<String> cursor = null;
    for (int page = 0; page <= items.size(); page++) {
      Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);
      String where = cursor == null ? "(1 = 1)" : keyset.after(cursor, "T", parameters);
      Fetched fetched = fetch(where, orderClause, parameters.values(), order, pageSize);
      listed.addAll(fetched.ids());
      if (fetched.ids().size() < pageSize) {
        break;
      }
      cursor = fetched.cursor();
    }

    assertThat(expected).hasSize(items.size());
    assertThat(listed).containsExactlyElementsOf(expected);
  }

  private Fetched fetch(String where, String orderClause, List<Object> values, List<OrderBy> order, int pageSize) {
    String columns = items.sortColumns().values().stream().map(column -> "T." + column.name())
        .collect(Collectors.joining(", "));
    String sql = "SELECT " + columns + " FROM item T WHERE " + where + " " + orderClause;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setMaxRows(pageSize);
      for (int i = 0; i < values.size(); i++) {
        database.bind(statement, i + 1, values.get(i));
      }
      List<Long> ids = new ArrayList<>();
      List<String> cursor = List.of();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          ids.add(rows.getLong(1));
          cursor = cursor(rows, order);
        }
      }
      return new Fetched(ids, cursor);
    } catch (SQLException refused) {
      throw new AssertionError("the engine refused the SQL: " + sql, refused);
    }
  }

  private List<String> cursor(ResultSet row, List<OrderBy> order) throws SQLException {
    List<String> cursor = new ArrayList<>(order.size());
    for (OrderBy term : order) {
      Column column = items.sortColumns().get(term.fieldPath().toString());
      Object value = database.read(row, column.name(), column.type());
      cursor.add(table.sortableFieldByFieldPath(term.fieldPath()).backend().cursorText(value));
    }
    return cursor;
  }
}
