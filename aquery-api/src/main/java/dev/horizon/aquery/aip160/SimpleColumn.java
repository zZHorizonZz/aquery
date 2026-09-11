package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip160.Filter.Arg;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A field in one database column.
 *
 * <p>
 * This backend does not know the database type of the column. Thus it cannot filter the field. It sorts by the
 * order of the database. Use it for fields that clients sort by but do not filter.
 *
 * <p>
 * This class is also the parent of the column backends. It contains the parts that they share: the database
 * name of the column, the argument readers, the column comparison and the LIKE escape. The subclasses use these
 * parts to write their SQL.
 *
 * <p>
 * The SQL contains the database name as it is. Thus the name must be a constant of the server. It must not be
 * text from a client.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/simple_column.go">LUCI
 *      aip160: simple_column.go (SimpleColumn)</a>
 */
public class SimpleColumn implements FieldBackend {

  private final String databaseName;

  public SimpleColumn(String databaseName) {
    this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
  }

  protected final String databaseName() {
    return databaseName;
  }

  @Override
  public String typeName() {
    return "SIMPLE";
  }

  @Override
  public Set<Operator> operators() {
    return Set.of();
  }

  @Override
  public String restrictionQuery(RestrictionContext restriction, Generator generator) {
    throw new UnsupportedOperationException("simple columns do not support filtering");
  }

  @Override
  public boolean supportsSorting() {
    return true;
  }

  @Override
  public List<SortKey> sortKeys(boolean descending, ColumnReferences columns) {
    return List.of(new SortKey(column(columns), descending));
  }

  @Override
  public boolean supportsReading() {
    return true;
  }

  @Override
  public List<String> selectExpressions(ColumnReferences columns) {
    return List.of(column(columns));
  }

  /**
   * Gives the reference to this column. It includes the table alias if the statement has one.
   *
   * @param columns the names of the table columns
   * @return the reference to the column
   */
  protected final String column(ColumnReferences columns) {
    return columns.columnReference(databaseName);
  }

  /**
   * Writes {@code (column operator value)} with the SQL operator of the restriction.
   *
   * @param restriction the restriction with the operator
   * @param columns the names of the table columns
   * @param value the SQL value: a bound parameter or a literal
   * @return the comparison in parentheses
   */
  protected final String comparison(RestrictionContext restriction, ColumnReferences columns, String value) {
    return "(" + column(columns) + " " + restriction.operator().sql() + " " + value + ")";
  }

  /**
   * Reads the argument of the restriction with one of the {@link Args} readers.
   *
   * @param <T> the type of the value
   * @param restriction the restriction with the argument
   * @param reader the reader of the argument, for example {@code Args::coerceToStringConstant}
   * @return the value that the reader gives
   * @throws InvalidFilterException if the reader refuses the argument. The message names the field of the argument
   */
  protected static <T> T argument(RestrictionContext restriction, Function<Arg, T> reader) {
    try {
      return reader.apply(restriction.arg());
    } catch (InvalidFilterException refused) {
      throw new InvalidFilterException("argument for field '%s': %s", restriction.qualifiedFieldPath(), refused.getMessage());
    }
  }

  /**
   * Writes {@code left = value} or {@code left <> value} for a string that the client compares for equality.
   *
   * <p>
   * AIP-160 lets the client put a wildcard at the start or at the end of the string. The value {@code "*.foo"}
   * matches all values that end with {@code .foo}. The value {@code "foo*"} matches all values that start with
   * {@code foo}. The SQL compares such a value with LIKE. A {@code *} at a different location is a usual
   * character.
   *
   * @param left the SQL expression of the string, for example a column reference
   * @param operator {@link Operator#EQUALS} or {@link Operator#NOT_EQUALS}
   * @param value the string from the client
   * @param generator the generator that binds the value
   * @return the comparison without parentheses
   */
  protected static String stringEquality(String left, Operator operator, String value, Generator generator) {
    String pattern = wildcardPattern(value);
    if (pattern == null) {
      return left + " " + operator.sql() + " " + generator.bindString(value);
    }
    return left + (operator == Operator.NOT_EQUALS ? " NOT LIKE " : " LIKE ") + generator.bindString(pattern);
  }

  /**
   * Gives the LIKE pattern of a value with a wildcard at its start or end.
   *
   * @param value the string from the client
   * @return the LIKE pattern, or null if the value has no wildcard
   */
  protected static String wildcardPattern(String value) {
    boolean leading = value.startsWith("*");
    boolean trailing = value.length() > (leading ? 1 : 0) && value.endsWith("*");
    if (!leading && !trailing) {
      return null;
    }
    String middle = value.substring(leading ? 1 : 0, value.length() - (trailing ? 1 : 0));
    return (leading ? "%" : "") + quoteLike(middle) + (trailing ? "%" : "");
  }

  /**
   * Gives the LIKE pattern that matches all values that contain the value.
   *
   * @param value the string from the client
   * @return the LIKE pattern
   */
  protected static String containsPattern(String value) {
    return "%" + quoteLike(value) + "%";
  }

  /**
   * Escapes the wildcards in a LIKE pattern.
   *
   * <p>
   * The {@code %} and {@code _} characters from the client are then usual characters, not wildcards. For example,
   * the value {@code test_name} then matches only itself. It does not match {@code test3name}.
   *
   * @param value the string from the client
   * @return the string with an escape before each backslash, {@code %} and {@code _}
   */
  protected static String quoteLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
