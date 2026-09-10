package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip160.FieldBackend.Generator;
import dev.horizon.aquery.aip160.WhereClause.QueryParameter;
import dev.horizon.aquery.common.Identifiers;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Generator} of one SQL fragment. It adds the table alias to the columns, binds values with the
 * parameter prefix, and keeps the parameters that it binds.
 *
 * <p>
 * {@link WhereClause} and {@code Keyset} write through this generator. A caller can also use it to test a backend.
 *
 * <p>
 * The table alias follows the rules of {@link TableAlias}. The parameter prefix keeps the parameter names different
 * from the other parameters of the statement. The prefix becomes part of the SQL text. Thus it must be an SQL
 * identifier: letters, digits and underscores, with no digit at the start. The constructor throws
 * {@link IllegalArgumentException} for an alias or a prefix that does not obey these rules.
 */
public final class SqlGenerator implements Generator {

  private final TableAlias tableAlias;
  private final String parameterPrefix;
  private final List<QueryParameter> parameters = new ArrayList<>();

  public SqlGenerator(String tableAlias, String parameterPrefix) {
    if (parameterPrefix == null || !Identifiers.isIdentifier(parameterPrefix)) {
      throw new IllegalArgumentException(
          "parameter prefixes are SQL identifiers of letters, digits and '_', was '" + parameterPrefix + "'");
    }
    this.tableAlias = new TableAlias(tableAlias);
    this.parameterPrefix = parameterPrefix;
  }

  @Override
  public String columnReference(String databaseName) {
    return tableAlias.columnReference(databaseName);
  }

  @Override
  public String bindString(String value) {
    String name = parameterPrefix + parameters.size();
    parameters.add(new QueryParameter(name, value));
    return "@" + name;
  }

  @Override
  public String literal(long value) {
    return Long.toString(value);
  }

  @Override
  public String literal(boolean value) {
    return value ? "TRUE" : "FALSE";
  }

  /**
   * Gives the parameters that the generator bound.
   *
   * @return the parameters, in the sequence of the binds. The list cannot change
   */
  public List<QueryParameter> parameters() {
    return List.copyOf(parameters);
  }
}
