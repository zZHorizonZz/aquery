package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.Arg;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes the SQL that filters, sorts and reads one logical field. The backend knows how the database keeps the field.
 *
 * <p>
 * The database keeps many fields in the same form that the API shows. For example, a string field is in a VARCHAR
 * column. Other fields have a different form. The API can show a test identifier as one string, but the database
 * can keep it as several parts. The database can keep a duration as nanoseconds in a BIGINT column. The backend
 * connects the two forms.
 *
 * <p>
 * This interface is the extension point of the library. To add a new type of field, write a new implementation
 * of this interface. You do not change the generator, the table or an enum. For example, a JSON document field
 * is a backend that reads one member of the document for each restriction.
 *
 * <p>
 * Each backend declares the parts of AIP-160 and AIP-132 that it supports. The library enforces the declaration.
 * Thus the backend does not repeat the checks:
 *
 * <ul>
 * <li>{@link #operators()} gives the comparators that the backend supports. The generator refuses all other
 * comparators before it calls {@link #restrictionQuery}. The error tells the client which operator failed.
 * <li>{@link #acceptsNestedFields()} tells if a filter can go into the field, as in {@code labels.site}. The
 * generator refuses this for all other backends.
 * <li>{@link #supportsImplicitRestrictions()} tells if a bare value can match the field.
 * <li>{@link #supportsSorting()} tells if an order_by clause can sort by the field. Some fields have a storage
 * order that is different from their logical order. These fields do not support sorting.
 * <li>{@link #supportsReading()} tells if a read mask can select the field.
 * </ul>
 *
 * <p>
 * A {@link Field} compares the declaration with the configuration of the server. It does this check when the
 * server makes the schema. A sortable field on a backend that cannot sort is a server error. This error occurs
 * at startup, not in each request.
 *
 * <p>
 * All implementations must obey one rule. A value goes into the SQL only through {@link Generator#bind}. The SQL
 * text contains only placeholders, column references from {@link ColumnReferences} and constant SQL of the backend.
 * A backend that does not obey this rule makes the full library unsafe against SQL injection.
 *
 * <p>
 * Each placeholder binds its own value. If the SQL uses a value two times, the backend binds it two times. The
 * backend binds the values in the sequence of their placeholders in its SQL text. Thus a placeholder without a
 * number, such as {@code ?}, gets the correct value.
 *
 * <p>
 * The SQL must be portable ISO SQL. PostgreSQL, MySQL, SQL Server, Oracle and H2 must all accept it, unless the
 * storage of the field needs a feature that an engine does not have, for example an array column.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/field_backends.go">LUCI aip160: field_backends.go (FieldBackend)</a>
 */
public interface FieldBackend {

  /**
   * Gives the name of the field type for error messages.
   *
   * @return the name of the type, for example {@code STRING}
   */
  String typeName();

  /**
   * Gives the operators that a restriction on this field can use.
   *
   * @return the operators. An empty set means that you cannot filter the field
   */
  Set<Operator> operators();

  /**
   * Tells if a restriction can go into this field. Only key-value fields have members to go into.
   *
   * @return true if the field accepts nested fields
   */
  default boolean acceptsNestedFields() {
    return false;
  }

  /**
   * Writes the SQL for the restriction on the field.
   *
   * <p>
   * The restriction names this field, one of the {@link #operators()} and an argument. A restriction can go into
   * a field that {@link #acceptsNestedFields() accepts nested fields}. Then the nested segments are in
   * {@link RestrictionContext#nestedFields()}.
   *
   * <p>
   * The SQL must be one boolean expression in parentheses, for example {@code "(condition)"}. Other expressions
   * contain it.
   *
   * @param restriction the field, the nested segments, the operator and the argument
   * @param generator the generator that binds values and names columns
   * @return the SQL expression in parentheses
   * @throws InvalidFilterException if the backend cannot answer the restriction, for example because the argument has the wrong
   * type
   */
  String restrictionQuery(RestrictionContext restriction, Generator generator);

  /**
   * Tells if a bare value in a filter, as in {@code prod}, can match this field.
   *
   * @return true if the field supports implicit restrictions
   */
  default boolean supportsImplicitRestrictions() {
    return false;
  }

  /**
   * Writes the SQL that matches a bare value with the field.
   *
   * <p>
   * A bare value is a filter restriction without a field name, for example {@code prod}. All fields for implicit
   * filters answer the value. The clause puts OR between their answers. The generator calls this method only on a
   * backend that {@link #supportsImplicitRestrictions() supports implicit restrictions}.
   *
   * <p>
   * The SQL must be one boolean expression in parentheses, for example {@code "(condition)"}.
   *
   * @param restriction the bare value
   * @param generator the generator that binds values and names columns
   * @return the SQL expression in parentheses
   * @throws InvalidFilterException if the backend cannot match the value
   */
  default String implicitRestrictionQuery(ImplicitRestrictionContext restriction, Generator generator) {
    throw new UnsupportedOperationException(typeName() + " fields do not match bare values");
  }

  /**
   * Tells if an order_by clause can sort by this field.
   *
   * @return true if the field supports sorting
   */
  default boolean supportsSorting() {
    return false;
  }

  /**
   * Gives the keys that sort by this field. The most important key is first.
   *
   * <p>
   * Most fields sort by one column. Some fields come from several columns, or from an expression such as
   * {@code CASE WHEN Column2 = 'blah' THEN 1 ELSE 0 END}. These fields give one key for each expression. The
   * generator calls this method only on a backend that {@link #supportsSorting() supports sorting}.
   *
   * @param descending true if the client asked for the field in descending order
   * @param columns the names of the table columns
   * @return the sort keys, the most important key first
   */
  default List<SortKey> sortKeys(boolean descending, ColumnReferences columns) {
    throw new UnsupportedOperationException(typeName() + " fields cannot be sorted by");
  }

  /**
   * Tells if a read mask can select this field.
   *
   * @return true if the field supports reading
   */
  default boolean supportsReading() {
    return false;
  }

  /**
   * Gives the SQL expressions that read this field, for the select list of a statement.
   *
   * <p>
   * Most fields read one column. A field that comes from several columns gives one expression for each column.
   * The server reads the values of the expressions in this sequence. The generator calls this method only on a
   * backend that {@link #supportsReading() supports reading}.
   *
   * @param columns the names of the table columns
   * @return the SQL expressions, in the sequence that the server reads them
   */
  default List<String> selectExpressions(ColumnReferences columns) {
    throw new UnsupportedOperationException(typeName() + " fields cannot be read");
  }

  /**
   * Reads the text of a cursor, and gives the value that keyset pagination binds.
   *
   * <p>
   * A page token keeps the value of the field in the last row of a page as text. {@link #cursorText} writes that
   * text. This method changes the text back into a value of the type of the column, so that the comparison with
   * the column has the correct type. The default implementation gives the text as a string.
   *
   * @param cursorText the text in the page token
   * @return the value to bind. It cannot be null
   * @throws IllegalArgumentException if the text is not a value of this field
   */
  default Object cursorValue(String cursorText) {
    return cursorText;
  }

  /**
   * Writes the text of a cursor from the value of the field in a row.
   *
   * <p>
   * The server reads the value of the field in the last row of a page. This method changes the value into the text
   * that the page token keeps. {@link #cursorValue} reads the text again. The default implementation gives
   * {@link String#valueOf(Object)}.
   *
   * @param value the value that the server read from the row
   * @return the text for the page token
   * @throws IllegalArgumentException if the value is not a value of this field
   */
  default String cursorText(Object value) {
    return String.valueOf(value);
  }

  /**
   * A restriction on a field, for example {@code my_field="blah"}.
   *
   * <p>
   * The path can name more than the field. Then the segments after the field are the nested fields. A key-value
   * field reads its key from them.
   */
  record RestrictionContext(FieldPath fieldPath, List<String> nestedFields, Operator operator, Arg arg) {

    public RestrictionContext {
      nestedFields = List.copyOf(nestedFields);
    }

    /**
     * Gives the path that the client wrote: the field and the nested segments. Error messages use this path.
     *
     * @return the path of the field with the nested segments after it
     */
    public FieldPath qualifiedFieldPath() {
      if (nestedFields.isEmpty()) {
        return fieldPath;
      }
      return new FieldPath(Stream.concat(fieldPath.segments().stream(), nestedFields.stream()).toList());
    }
  }

  /**
   * A bare value in a filter, without a field name, for example {@code prod}.
   *
   * <p>
   * A bare value is always a string. AIP-160 says that the words {@code true} and {@code false} have a meaning
   * only for a typed field. Thus a bare value is never a boolean, an integer, a duration or an enum value.
   */
  record ImplicitRestrictionContext(String argValueUnsafe) {
  }

  /**
   * One key of an ORDER BY clause: an SQL expression and its direction.
   *
   * <p>
   * The backend writes the expression from the schema and from {@link ColumnReferences}. The expression never
   * contains text from a client.
   */
  record SortKey(String expression, boolean descending) {
  }

  /** Gives the names of the table columns. The names include the table alias if the statement has one. */
  interface ColumnReferences {

    /**
     * Gives the full name of the column. The name includes the table alias if the statement has one.
     *
     * <p>
     * The column name comes from the schema. It never comes from a client.
     *
     * @param databaseName the name of the column in the database
     * @return the name of the column with the table alias
     */
    String columnReference(String databaseName);
  }

  /**
   * The generator gives these methods to a backend. They are the only ways that a value goes into the SQL.
   */
  interface Generator extends ColumnReferences {

    /**
     * Binds the value as a query parameter, and gives the placeholder for the parameter.
     *
     * <p>
     * The value never becomes part of the SQL text. This is the only way that a value goes into the statement.
     * Bind the value in the Java type that the driver sends for the column, for example {@link Long} for a BIGINT
     * column. Call this method once for each placeholder, in the sequence of the placeholders in the SQL text.
     *
     * @param value the value to bind. It cannot be null
     * @return the placeholder, for example {@code ?} or {@code $1}
     */
    String bind(Object value);
  }
}
