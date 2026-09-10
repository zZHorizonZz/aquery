package dev.horizon.aquery.aip158;

import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.FieldBackend;
import dev.horizon.aquery.aip160.FieldBackend.SortKey;
import dev.horizon.aquery.aip160.SqlGenerator;
import dev.horizon.aquery.aip160.WhereClause;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the SQL condition that continues a list after the last row of a page. This is keyset pagination.
 *
 * <p>
 * Make a keyset from the table and the order of the list. Use the merged order of
 * {@link dev.horizon.aquery.aip132.OrderByClause#mergeWithDefaultOrder}. The order must end with a unique
 * tiebreaker, for example the id. Each field of the order must have exactly one sort key, and its column must not
 * contain NULL. The constructor throws {@link InvalidOrderByException} for a field that is not sortable or that
 * occurs two times. It throws {@link IllegalArgumentException} for a field with more than one sort key.
 *
 * <p>
 * The cursor has one value for each term of the order, in the same sequence. Each value is the text form of the
 * database value of the last row. For an INT64 column, use the decimal digits. For a STRING column, use the
 * string. Put the cursor in a {@link PageToken}.
 *
 * <p>
 * For the order {@code create_time desc, id} and the cursor {@code 1335022200000000, "abc"}, the condition is:
 *
 * <pre>{@code
 * ((T.create_time < 1335022200000000) OR (T.create_time = 1335022200000000 AND T.id > @k_0))
 * }</pre>
 *
 * <p>
 * The backend of each field writes the cursor value. A string becomes a bound parameter. A number or a boolean
 * becomes a literal that the backend parsed. Thus the condition is safe against SQL injection.
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
   * @param cursor the values of the last row, one for each term of the order
   * @param tableAlias the alias of the table in the statement. Use null or an empty string if the table has no alias
   * @param parameterPrefix the prefix of the parameter names. Use a prefix that is different from the prefix of the filter
   * @return the condition in parentheses and its parameters
   * @throws InvalidPageTokenException if the cursor does not agree with the order
   * @throws IllegalArgumentException if the alias or the prefix is not an SQL identifier
   */
  public WhereClause.Result after(List<String> cursor, String tableAlias, String parameterPrefix) {
    SqlGenerator generator = new SqlGenerator(tableAlias, parameterPrefix);
    if (cursor.size() != order.size()) {
      throw new InvalidPageTokenException("page_token does not match the order of the listing; start the listing again");
    }
    if (order.isEmpty()) {
      return new WhereClause.Result("(TRUE)", List.of());
    }

    List<String> equalities = new ArrayList<>();
    List<String> alternatives = new ArrayList<>();
    for (int i = 0; i < order.size(); i++) {
      OrderBy term = order.get(i);
      FieldBackend backend = backends.get(i);
      SortKey key = backend.sortKeys(term.descending(), generator).getFirst();
      String value;
      try {
        value = backend.cursorArgument(cursor.get(i), generator);
      } catch (IllegalArgumentException unreadable) {
        throw new InvalidPageTokenException(
            "page_token has a cursor value that field '%s' cannot read; start the listing again", term.fieldPath());
      }
      List<String> conditions = new ArrayList<>(equalities);
      conditions.add(key.expression() + (key.descending() ? " < " : " > ") + value);
      alternatives.add("(" + String.join(" AND ", conditions) + ")");
      equalities.add(key.expression() + " = " + value);
    }
    return new WhereClause.Result("(" + String.join(" OR ", alternatives) + ")", generator.parameters());
  }
}
