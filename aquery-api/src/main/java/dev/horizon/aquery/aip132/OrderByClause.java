package dev.horizon.aquery.aip132;

import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend.SortKey;
import dev.horizon.aquery.aip160.TableAlias;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Writes a Standard SQL ORDER BY clause from a table schema and an AIP-132 order.
 *
 * <p>
 * The clause names only database columns that the table declares. The order names fields. The table gives the
 * backend of each field, and the backend gives the sort keys of the field. Thus a field from several columns
 * also sorts correctly.
 *
 * <p>
 * The output is safe against SQL injection. It contains no text from a client.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/orderby_generator.go">LUCI aip160: orderby_generator.go (OrderByClause, MergeWithDefaultOrder)</a>
 */
public final class OrderByClause {

  private OrderByClause() {
  }

  /**
   * Merges the order of the client with a default order.
   *
   * <ul>
   * <li>The order of the client comes first, as the client wrote it.
   * <li>The fields of the default order that the client did not name come next, in the sequence of the default
   * order.
   * </ul>
   *
   * <p>
   * A default order usually ends with a unique tiebreaker, such as the id. The merge keeps that tiebreaker. Thus the
   * list stays deterministic, and keyset pagination continues to work.
   *
   * @param defaultOrder the order of the server
   * @param order the order of the client
   * @return the merged order. The list cannot change
   */
  public static List<OrderBy> mergeWithDefaultOrder(List<OrderBy> defaultOrder, List<OrderBy> order) {
    List<OrderBy> result = new ArrayList<>(order);
    Set<FieldPath> seen = new HashSet<>();
    order.forEach(term -> seen.add(term.fieldPath()));
    for (OrderBy term : defaultOrder) {
      if (seen.add(term.fieldPath())) {
        result.add(term);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Writes the ORDER BY clause for a table without an alias.
   *
   * @param table the schema of the table
   * @param order the order of the list
   * @return the clause with a new line at the end, or an empty string for an empty order
   * @throws InvalidOrderByException if a field is not sortable, or if a field occurs two times
   */
  public static String of(DatabaseTable table, List<OrderBy> order) {
    return of(table, order, null);
  }

  /**
   * Writes the ORDER BY clause.
   *
   * <p>
   * The clause starts with {@code ORDER BY} and ends with a new line. An empty order gives an empty string. The
   * column references include the table alias.
   *
   * @param table the schema of the table
   * @param order the order of the list
   * @param tableAlias the alias of the table in the statement. Use null or an empty string if the table has no alias
   * @return the clause with a new line at the end, or an empty string for an empty order
   * @throws InvalidOrderByException if a field is not sortable, or if a field occurs two times
   * @throws IllegalArgumentException if the table alias starts with {@code aquery_}, or if it is not an SQL identifier
   */
  public static String of(DatabaseTable table, List<OrderBy> order, String tableAlias) {
    TableAlias columns = new TableAlias(tableAlias);
    if (order.isEmpty()) {
      return "";
    }
    List<Field> fields = table.sortableFieldsByOrder(order);
    StringJoiner clause = new StringJoiner(", ", "ORDER BY ", "\n");
    for (int i = 0; i < order.size(); i++) {
      for (SortKey key : fields.get(i).backend().sortKeys(order.get(i).descending(), columns)) {
        clause.add(key.descending() ? key.expression() + " DESC" : key.expression());
      }
    }
    return clause.toString();
  }
}
