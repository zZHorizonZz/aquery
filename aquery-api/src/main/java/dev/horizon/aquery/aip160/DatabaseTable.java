package dev.horizon.aquery.aip160;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip132.InvalidOrderByException;
import dev.horizon.aquery.aip132.OrderBy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The schema of a database table, view or query. It gives the fields for AIP-160 filters and AIP-132 order_by
 * clauses, and what clients can do with each field.
 *
 * <p>
 * The server declares the schema. The schema tells which field paths exist, which database columns answer them,
 * and which fields clients can filter or sort. The generators write their SQL only from this schema. A client
 * never names a column.
 *
 * <p>
 * A field path can name more than a field. The filter {@code labels.site = "pilsen"} names the field
 * {@code labels} and goes into {@code site}. The lookup tries the full path first. Then it removes segments from
 * the end until a filterable field answers. The answer includes the segments that the field did not use. The
 * backend of that field tells what they mean. A key-value field reads its key in this way.
 *
 * <p>
 * The lookup tries only prefixes that are not longer than the longest filterable path. Thus a very long path
 * does not make the lookup slow.
 *
 * @see <a href=
 * "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/datamodel.go">LUCI
 * aip160: datamodel.go (DatabaseTable)</a>
 */
public final class DatabaseTable {

  private final List<Field> fields;
  private final Map<FieldPath, Field> fieldByFieldPath = new HashMap<>();
  private final int longestFilterablePath;
  private final List<Field> implicitFields;
  private final String filterableNames;
  private final String sortableNames;

  public DatabaseTable(Field... fields) {
    this.fields = List.of(fields);
    for (Field field : this.fields) {
      if (fieldByFieldPath.putIfAbsent(field.fieldPath(), field) != null) {
        throw new IllegalStateException("multiple fields with the same field path: " + field.fieldPath());
      }
    }
    this.longestFilterablePath = this.fields.stream()
        .filter(Field::filterable)
        .mapToInt(field -> field.fieldPath().segments().size())
        .max()
        .orElse(0);
    this.implicitFields = this.fields.stream().filter(Field::implicitFilter).toList();
    this.filterableNames = names(Field::filterable);
    this.sortableNames = names(Field::sortable);
  }

  /**
   * Gives the fields of the table.
   *
   * @return the fields, in the sequence of their declaration. The list cannot change
   */
  public List<Field> fields() {
    return fields;
  }

  /**
   * Gives the fields that a bare value in a filter matches.
   *
   * @return the implicit fields, in the sequence of their declaration. The list cannot change
   */
  public List<Field> implicitFields() {
    return implicitFields;
  }

  /**
   * Finds the filterable field that the path names, or that a prefix of the path names.
   *
   * <p>
   * The lookup tries the full path first. If no field answers, it removes one segment from the end and tries again.
   * The result has the field and the segments of the path after the field.
   *
   * @param path the path from the filter
   * @return the field and the segments that the field did not use
   * @throws InvalidFilterException if no filterable field answers the path or a prefix of it. The message names the fields that
   * the client can use
   */
  public FilterableField filterableFieldByFieldPath(FieldPath path) {
    List<String> segments = path.segments();
    for (int length = Math.min(segments.size(), longestFilterablePath); length > 0; length--) {
      Field field = fieldByFieldPath.get(new FieldPath(segments.subList(0, length)));
      if (field != null && field.filterable()) {
        return new FilterableField(field, segments.subList(length, segments.size()));
      }
    }
    throw new InvalidFilterException("no filterable field '%s' or a prefix thereof, valid fields are %s", path,
        filterableNames);
  }

  /**
   * Finds the sortable field that the path names.
   *
   * @param path the path from the order
   * @return the sortable field
   * @throws InvalidOrderByException if no sortable field answers the path. The message names the fields that the client can
   * sort by
   */
  public Field sortableFieldByFieldPath(FieldPath path) {
    Field field = fieldByFieldPath.get(path);
    if (field != null && field.sortable()) {
      return field;
    }
    throw new InvalidOrderByException("no sortable field named '%s', valid fields are %s", path, sortableNames);
  }

  /**
   * Finds the sortable field of each term of an order.
   *
   * @param order the order of the list
   * @return the fields, in the sequence of the order
   * @throws InvalidOrderByException if a field is not sortable, or if a field occurs two times
   */
  public List<Field> sortableFieldsByOrder(List<OrderBy> order) {
    Set<FieldPath> seen = new HashSet<>();
    List<Field> result = new ArrayList<>(order.size());
    for (OrderBy term : order) {
      Field field = sortableFieldByFieldPath(term.fieldPath());
      if (!seen.add(term.fieldPath())) {
        throw new InvalidOrderByException("field appears in order_by multiple times: '%s'", term.fieldPath());
      }
      result.add(field);
    }
    return result;
  }

  private String names(Predicate<Field> condition) {
    return fields.stream().filter(condition).map(field -> field.fieldPath().toString()).collect(joining(", "));
  }

  /**
   * A filterable field and the segments of the path that the field did not use.
   *
   * <p>
   * The backend of the field tells what the segments mean. A key-value field reads its key from them. The
   * generator refuses the segments for all other fields.
   */
  public record FilterableField(Field field, List<String> unusedPath) {

    public FilterableField {
      unusedPath = List.copyOf(unusedPath);
    }
  }
}
