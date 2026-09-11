package dev.horizon.aquery.aip157;

import static java.util.stream.Collectors.joining;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.TableAlias;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the select list of a statement from a table schema and an AIP-157 read mask.
 *
 * <p>
 * The select list contains only the columns of the readable fields that the mask selects. It does not include the
 * {@code SELECT} keyword. Thus the server can add its own columns to the list.
 *
 * <p>
 * A path of the mask selects a readable field in one of these ways:
 *
 * <ul>
 * <li>The path is the path of the field, for example {@code title} for the field {@code title}.
 * <li>The path is a parent of the field, for example {@code author} for the fields {@code author.given_name} and
 * {@code author.family_name}. AIP-161 lets the client select a field as a whole or one of its subfields.
 * <li>The path goes into a field that accepts nested fields, for example {@code labels.site} for the key-value
 * field {@code labels}. The select list then reads the full column. The server removes the other keys from the
 * response.
 * </ul>
 *
 * <p>
 * The select list has the columns in the sequence of the schema, not in the sequence of the mask. Thus two masks
 * with the same fields give the same SQL. A path that selects no readable field is an error. AIP-161 lets a
 * service ignore such a path, but an error tells the client about a wrong path.
 *
 * <p>
 * The output is safe against SQL injection. It contains only column references from the schema.
 */
public final class SelectClause {

  private SelectClause() {
  }

  /**
   * The select list and the fields that it reads.
   *
   * <p>
   * The server reads the columns of each field in the sequence of the fields. The columns of one field have the
   * sequence of {@link dev.horizon.aquery.aip160.FieldBackend#selectExpressions}.
   */
  public record Result(String sql, List<Field> fields) {

    public Result {
      fields = List.copyOf(fields);
    }
  }

  /**
   * Writes the select list for the read mask.
   *
   * @param table the schema of the table
   * @param mask the read mask of the request. Use {@link ReadMask#ReadMask()} for all fields
   * @param tableAlias the alias of the table in the statement. Use null or an empty string if the table has no alias
   * @return the columns with a comma between them, and the selected fields in the sequence of the schema
   * @throws InvalidReadMaskException if a path of the mask selects no readable field
   * @throws IllegalStateException if the table declares no readable fields
   * @throws IllegalArgumentException if the table alias starts with {@code _}, or if it is not an SQL identifier
   */
  public static Result of(DatabaseTable table, ReadMask mask, String tableAlias) {
    TableAlias columns = new TableAlias(tableAlias);
    List<Field> readable = table.fields().stream().filter(Field::readable).toList();
    if (readable.isEmpty()) {
      throw new IllegalStateException("the table declares no readable fields");
    }

    Set<Field> selected = new HashSet<>();
    if (mask.allFields()) {
      selected.addAll(readable);
    }
    for (FieldPath path : mask.paths()) {
      List<Field> matches = readable.stream().filter(field -> selects(path, field)).toList();
      if (matches.isEmpty()) {
        throw new InvalidReadMaskException("no readable field '%s' or a field inside it, valid fields are %s", path,
            readable.stream().map(field -> field.fieldPath().toString()).collect(joining(", ")));
      }
      selected.addAll(matches);
    }

    List<Field> fields = readable.stream().filter(selected::contains).toList();
    String sql = fields.stream()
        .flatMap(field -> field.backend().selectExpressions(columns).stream())
        .collect(joining(", "));
    return new Result(sql, fields);
  }

  private static boolean selects(FieldPath path, Field field) {
    List<String> requested = path.segments();
    List<String> declared = field.fieldPath().segments();
    if (requested.size() <= declared.size()) {
      return declared.subList(0, requested.size()).equals(requested);
    }
    return field.backend().acceptsNestedFields() && requested.subList(0, declared.size()).equals(declared);
  }
}
