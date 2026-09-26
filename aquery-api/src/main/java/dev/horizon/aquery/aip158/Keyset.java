package dev.horizon.aquery.aip158;

import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByClause;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend;
import dev.horizon.aquery.aip160.FieldBackend.SortKey;
import dev.horizon.aquery.aip160.SqlGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the SQL condition that continues a list after the last row of a page. This is keyset pagination.
 *
 * <p>
 * Make a keyset from the table and the order of the list. Use the merged order of
 * {@link OrderByClause#mergeWithDefaultOrder}. The order must end with a unique tiebreaker, for example the id. Each
 * field of the order must have exactly one sort key, and its column must not contain NULL. The constructor throws
 * {@link InvalidOrderByException} for a field that is not sortable or that occurs two times. It throws
 * {@link IllegalArgumentException} for a field with more than one sort key.
 *
 * <p>
 * The cursor has one text for each term of the order, in the same sequence. Make each text with
 * {@link FieldBackend#cursorText} from the value of the last row. Put the cursor in a {@link PageToken}.
 *
 * <p>
 * For the order {@code create_time desc, id}, the condition is:
 *
 * <pre>{@code
 * ((T.create_time < ?) OR (T.create_time = ? AND T.id > ?))
 * }</pre>
 *
 * <p>
 * The backend of each field reads its cursor text with {@link FieldBackend#cursorValue}. The condition binds the
 * typed value at each placeholder. Thus each value is bound as often as the SQL uses it, and the condition is safe
 * against SQL injection.
 */
public final class Keyset {

  private final List<OrderBy> order;
  private final List<FieldBackend> backends = new ArrayList<>();

  public Keyset(DatabaseTable table, List<OrderBy> order) {
    this.order = List.copyOf(order);
    List<Field> fields = table.sortableFieldsByOrder(this.order);
    for (int i = 0; i < this.order.size(); i++) {
      OrderBy term = this.order.get(i);
      FieldBackend backend = fields.get(i).backend();
      int keys = backend.sortKeys(term.descending(), databaseName -> databaseName).size();
      if (keys != 1) {
        throw new IllegalArgumentException(
            "keyset pagination needs one sort key for each field, but field '" + term.fieldPath() + "' has " + keys);
      }
      backends.add(backend);
    }
  }

  /**
   * Writes the condition that selects the rows after the cursor.
   *
   * <p>
   * An empty order gives {@code (1 = 1)} and binds no values.
   *
   * @param cursor the texts of the last row, one for each term of the order
   * @param tableAlias the alias of the table in the statement. Use null or an empty string if the table has no alias
   * @param parameters the parameters of the statement. The condition binds its values in them
   * @return the condition in parentheses
   * @throws InvalidPageTokenException if the cursor does not agree with the order
   * @throws IllegalArgumentException if the alias is not an SQL identifier, or if it starts with {@code aquery_}
   */
  public String after(List<String> cursor, String tableAlias, Parameters parameters) {
    SqlGenerator generator = new SqlGenerator(tableAlias, parameters);
    if (cursor.size() != order.size()) {
      throw new InvalidPageTokenException("page_token does not match the order of the listing; start the listing again");
    }
    if (order.isEmpty()) {
      return "(1 = 1)";
    }

    List<SortKey> keys = new ArrayList<>(order.size());
    List<Object> values = new ArrayList<>(order.size());
    for (int i = 0; i < order.size(); i++) {
      OrderBy term = order.get(i);
      FieldBackend backend = backends.get(i);
      keys.add(backend.sortKeys(term.descending(), generator).getFirst());
      try {
        values.add(backend.cursorValue(cursor.get(i)));
      } catch (IllegalArgumentException unreadable) {
        throw new InvalidPageTokenException(
            "page_token has a cursor value that field '%s' cannot read; start the listing again", term.fieldPath());
      }
    }

    List<String> alternatives = new ArrayList<>();
    for (int i = 0; i < order.size(); i++) {
      List<String> conditions = new ArrayList<>(i + 1);
      for (int j = 0; j < i; j++) {
        conditions.add(keys.get(j).expression() + " = " + generator.bind(values.get(j)));
      }
      SortKey key = keys.get(i);
      conditions.add(key.expression() + (key.descending() ? " < " : " > ") + generator.bind(values.get(i)));
      alternatives.add("(" + String.join(" AND ", conditions) + ")");
    }
    return "(" + String.join(" OR ", alternatives) + ")";
  }
}
